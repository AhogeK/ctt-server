package com.ahogek.cttserver.auth.passkey.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.lockout.LoginAttemptService;
import com.ahogek.cttserver.auth.passkey.entity.UserPasskey;
import com.ahogek.cttserver.auth.passkey.repository.UserPasskeyRepository;
import com.ahogek.cttserver.auth.service.UserLoginService;
import com.ahogek.cttserver.auth.twofactor.service.TwoFactorChallengeService;
import com.ahogek.cttserver.common.config.properties.PasskeyProperties;
import com.ahogek.cttserver.common.context.RequestContext;
import com.ahogek.cttserver.common.context.RequestInfo;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;

/**
 * Discoverable passkey authentication ceremony.
 *
 * <p>The assertion challenge is anonymous: no email or username is sent to the browser. The
 * authenticator returns its user handle, which is the stable UUID used to resolve the local
 * account. A successful assertion is a complete login and does not enter the TOTP second step.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Service
public class PasskeyAuthenticationService {

    private final RelyingParty relyingParty;
    private final PasskeyProperties properties;
    private final PasskeyChallengeService challenges;
    private final UserPasskeyRepository passkeys;
    private final UserRepository users;
    private final UserLoginService userLoginService;
    private final LoginAttemptService loginAttempts;
    private final AuditLogService auditLog;
    private final TwoFactorChallengeService twoFactorChallenges;
    private final ObjectMapper objectMapper;

    public PasskeyAuthenticationService(
            RelyingParty relyingParty,
            PasskeyProperties properties,
            PasskeyChallengeService challenges,
            UserPasskeyRepository passkeys,
            UserRepository users,
            UserLoginService userLoginService,
            LoginAttemptService loginAttempts,
            AuditLogService auditLog,
            TwoFactorChallengeService twoFactorChallenges,
            ObjectMapper objectMapper) {
        this.relyingParty = relyingParty;
        this.properties = properties;
        this.challenges = challenges;
        this.passkeys = passkeys;
        this.users = users;
        this.userLoginService = userLoginService;
        this.loginAttempts = loginAttempts;
        this.auditLog = auditLog;
        this.twoFactorChallenges = twoFactorChallenges;
        this.objectMapper = objectMapper;
    }

    /**
     * Starts a discoverable assertion ceremony. The options contain no allowCredentials list.
     *
     * @return browser-ready request options
     */
    public Map<String, Object> startAssertion() {
        AssertionRequest request =
                relyingParty.startAssertion(
                        StartAssertionOptions.builder()
                                .userVerification(UserVerificationRequirement.REQUIRED)
                                .timeout(properties.timeout().toSeconds())
                                .build());
        try {
            challenges.store(
                    PasskeyChallengeService.Operation.ASSERTION,
                    null,
                    request.getPublicKeyCredentialRequestOptions().getChallenge(),
                    request.toJson());
            return objectMapper.readValue(request.toCredentialsGetJson(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize passkey assertion options", e);
        }
    }

    /**
     * Starts an account-bound assertion for an existing two-factor login challenge.
     *
     * <p>The account is already known from the login challenge, so the options carry {@code
     * allowCredentials} for that account instead of using a discoverable credential.
     *
     * @param challengeToken the second-factor login challenge
     * @return browser-ready assertion options
     */
    public Map<String, Object> startSecondFactor(String challengeToken) {
        TwoFactorChallengeService.ChallengePayload twoFactor =
                twoFactorChallenges.peek(challengeToken);
        User user =
                users.findById(twoFactor.userId())
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                ErrorCode.AUTH_026,
                                                "Two-factor challenge is invalid or expired"));
        if (user.getTotpConfirmedAt() == null) {
            throw new UnauthorizedException(
                    ErrorCode.AUTH_026, "Two-factor challenge is invalid or expired");
        }
        if (passkeys.countByUserId(user.getId()) == 0) {
            throw new UnauthorizedException(ErrorCode.AUTH_033, "No passkey registered");
        }

        AssertionRequest request =
                relyingParty.startAssertion(
                        StartAssertionOptions.builder()
                                .userHandle(PasskeyUserHandle.toByteArray(user.getId()))
                                .userVerification(UserVerificationRequirement.REQUIRED)
                                .timeout(properties.timeout().toSeconds())
                                .build());
        try {
            challenges.store(
                    PasskeyChallengeService.Operation.SECOND_FACTOR,
                    user.getId(),
                    request.getPublicKeyCredentialRequestOptions().getChallenge(),
                    request.toJson(),
                    TokenUtils.hashToken(challengeToken));
            return objectMapper.readValue(request.toCredentialsGetJson(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize second-factor passkey options", e);
        }
    }

    /**
     * Verifies a discoverable assertion and issues the normal login response.
     *
     * @param credential the browser assertion JSON
     * @return the normal login response, including cookies and refresh session
     */
    @SuppressWarnings("deprecation") // Yubico 2.10 exposes BS only through this deprecated accessor
    public LoginResponse finishAssertion(Map<String, Object> credential) {
        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs>
                response;
        try {
            response = parseAssertion(credential);
        } catch (UnauthorizedException e) {
            throw fail(null, "Malformed assertion");
        }
        UserPasskey knownPasskey =
                passkeys.findByCredentialId(response.getId().getBase64Url()).orElse(null);
        User knownUser = knownPasskey == null ? null : knownPasskey.getUser();

        AssertionResult result;
        try {
            PasskeyChallengeService.ChallengePayload challenge =
                    challenges.consume(
                            PasskeyChallengeService.Operation.ASSERTION,
                            null,
                            null,
                            response.getResponse().getClientData().getChallenge());
            result = finishAssertion(challenge.requestJson(), response);
        } catch (UnauthorizedException e) {
            // AUTH_032 is a challenge failure, not an account-enumeration signal.
            throw e;
        } catch (AssertionFailedException | JsonProcessingException | RuntimeException e) {
            throw fail(knownUser, "Assertion verification failed");
        }

        if (!result.isUserVerified()) {
            throw fail(knownUser, "User verification missing");
        }

        UUID userId =
                PasskeyUserHandle.toUuid(response.getResponse().getUserHandle().orElse(null))
                        .orElseThrow(() -> fail(knownUser, "User handle missing"));
        User user = users.findById(userId).orElseThrow(() -> fail(knownUser, "Account not found"));
        UserPasskey passkey = requireOwned(user, response);
        validateUsable(passkey, result);
        return completeLogin(user, passkey, result, null);
    }

    /**
     * Verifies an assertion for an existing two-factor challenge and issues the normal login
     * response.
     *
     * <p>The login challenge is only consumed after a successful assertion. A failed passkey
     * attempt therefore leaves the TOTP/recovery-code fallback intact.
     *
     * @param challengeToken the second-factor login challenge
     * @param credential the browser assertion JSON
     * @return the normal login response, including cookies and refresh session
     */
    @SuppressWarnings("deprecation") // Yubico 2.10 exposes BS only through this deprecated accessor
    public LoginResponse finishSecondFactor(String challengeToken, Map<String, Object> credential) {
        TwoFactorChallengeService.ChallengePayload twoFactor =
                twoFactorChallenges.peek(challengeToken);
        User user =
                users.findById(twoFactor.userId())
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                ErrorCode.AUTH_026,
                                                "Two-factor challenge is invalid or expired"));
        if (user.getTotpConfirmedAt() == null) {
            throw new UnauthorizedException(
                    ErrorCode.AUTH_026, "Two-factor challenge is invalid or expired");
        }

        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs>
                response;
        try {
            response = parseAssertion(credential);
        } catch (UnauthorizedException e) {
            throw fail(user, "Malformed assertion");
        }
        User knownUser = user;
        AssertionResult result;
        try {
            PasskeyChallengeService.ChallengePayload challenge =
                    challenges.consume(
                            PasskeyChallengeService.Operation.SECOND_FACTOR,
                            user.getId(),
                            TokenUtils.hashToken(challengeToken),
                            response.getResponse().getClientData().getChallenge());
            result = finishAssertion(challenge.requestJson(), response);
        } catch (UnauthorizedException e) {
            recordFailure(user, "Challenge invalid or expired");
            throw e;
        } catch (AssertionFailedException | JsonProcessingException | RuntimeException e) {
            throw fail(knownUser, "Assertion verification failed");
        }

        if (!result.isUserVerified()) {
            throw fail(knownUser, "User verification missing");
        }

        UserPasskey passkey = requireOwned(user, response);
        validateUsable(passkey, result);
        TwoFactorChallengeService.ChallengePayload claimed =
                twoFactorChallenges.consume(challengeToken);
        return completeLogin(user, passkey, result, claimed.deviceId());
    }

    private PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs>
            parseAssertion(Map<String, Object> credential) {
        try {
            return PublicKeyCredential.parseAssertionResponseJson(
                    objectMapper.writeValueAsString(credential));
        } catch (IOException e) {
            throw new UnauthorizedException(ErrorCode.AUTH_033, "Malformed assertion");
        }
    }

    private AssertionResult finishAssertion(
            String requestJson,
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs>
                    response)
            throws AssertionFailedException, JsonProcessingException {
        AssertionRequest request = AssertionRequest.fromJson(requestJson);
        return relyingParty.finishAssertion(
                FinishAssertionOptions.builder().request(request).response(response).build());
    }

    private UserPasskey requireOwned(
            User user,
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs>
                    response) {
        UserPasskey passkey =
                passkeys.findByCredentialId(response.getId().getBase64Url())
                        .orElseThrow(() -> fail(user, "Credential not found"));
        if (!passkey.getUser().getId().equals(user.getId())) {
            throw fail(user, "Credential owner mismatch");
        }
        return passkey;
    }

    private void validateUsable(UserPasskey passkey, AssertionResult result) {
        if (isCounterRegression(passkey.getSignCount(), result.getSignatureCount())) {
            throw fail(passkey.getUser(), "Signature counter regression");
        }
        Instant now = Instant.now();
        if (!properties.newCredentialDelay().isZero()
                && passkey.getCreatedAt().plus(properties.newCredentialDelay()).isAfter(now)) {
            throw fail(passkey.getUser(), "Credential is not yet active");
        }
    }

    private LoginResponse completeLogin(
            User user, UserPasskey passkey, AssertionResult result, String deviceId) {
        passkey.markUsed(result.getSignatureCount(), result.isBackedUp(), Instant.now());
        passkeys.save(passkey);

        LoginResponse login = userLoginService.completeLogin(user, deviceId);
        loginAttempts.recordSuccess(user.getEmail());
        auditLog.logSuccess(
                user.getId(),
                AuditAction.PASSKEY_LOGIN_SUCCESS,
                ResourceType.USER,
                user.getId().toString());
        return login;
    }

    private void recordFailure(User user, String reason) {
        if (user != null) {
            String ip = RequestContext.current().map(RequestInfo::clientIp).orElse(null);
            loginAttempts.recordFailure(user.getEmail(), ip);
            auditLog.log(
                    user.getId(),
                    AuditAction.PASSKEY_LOGIN_FAILED,
                    ResourceType.USER,
                    user.getId().toString(),
                    SecuritySeverity.WARNING,
                    AuditDetails.reason(reason));
        } else {
            auditLog.logFailure(
                    null, AuditAction.PASSKEY_LOGIN_FAILED, ResourceType.USER, "anonymous", reason);
        }
    }

    private UnauthorizedException fail(User user, String reason) {
        recordFailure(user, reason);
        return new UnauthorizedException(ErrorCode.AUTH_033, "Passkey verification failed");
    }

    private static boolean isCounterRegression(long stored, long observed) {
        return stored > 0 && (observed == 0 || observed <= stored);
    }
}
