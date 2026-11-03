package com.ahogek.cttserver.auth.passkey.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.passkey.dto.PasskeyRegistrationRequest;
import com.ahogek.cttserver.auth.passkey.dto.PasskeyResponse;
import com.ahogek.cttserver.auth.passkey.entity.UserPasskey;
import com.ahogek.cttserver.auth.passkey.repository.UserPasskeyRepository;
import com.ahogek.cttserver.auth.service.RecentAuthenticationService;
import com.ahogek.cttserver.common.config.properties.PasskeyProperties;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.RegistrationFailedException;

/**
 * Registration ceremony for discoverable passkeys.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Service
public class PasskeyRegistrationService {

    private final RelyingParty relyingParty;
    private final UserRepository users;
    private final UserPasskeyRepository passkeys;
    private final PasskeyChallengeService challenges;
    private final PasskeyProperties properties;
    private final RecentAuthenticationService recentAuthentication;
    private final PasskeyNotificationService notifications;
    private final AuditLogService auditLog;
    private final ObjectMapper objectMapper;

    public PasskeyRegistrationService(
            RelyingParty relyingParty,
            UserRepository users,
            UserPasskeyRepository passkeys,
            PasskeyChallengeService challenges,
            PasskeyProperties properties,
            RecentAuthenticationService recentAuthentication,
            PasskeyNotificationService notifications,
            AuditLogService auditLog,
            ObjectMapper objectMapper) {
        this.relyingParty = relyingParty;
        this.users = users;
        this.passkeys = passkeys;
        this.challenges = challenges;
        this.properties = properties;
        this.recentAuthentication = recentAuthentication;
        this.notifications = notifications;
        this.auditLog = auditLog;
        this.objectMapper = objectMapper;
    }

    /**
     * Starts a registration ceremony and returns browser-ready creation options.
     *
     * @param userId the authenticated account
     * @param sessionId the current login session
     * @return the WebAuthn creation options wrapped in the publicKey envelope
     */
    public Map<String, Object> startRegistration(UUID userId, UUID sessionId) {
        recentAuthentication.requireRecent(userId, sessionId);
        User user = requireUser(userId);
        PublicKeyCredentialCreationOptions options =
                relyingParty.startRegistration(
                        StartRegistrationOptions.builder()
                                .user(
                                        UserIdentity.builder()
                                                .name(user.getEmail())
                                                .displayName(user.getDisplayName())
                                                .id(PasskeyUserHandle.toByteArray(user.getId()))
                                                .build())
                                .authenticatorSelection(
                                        AuthenticatorSelectionCriteria.builder()
                                                .residentKey(ResidentKeyRequirement.REQUIRED)
                                                .userVerification(
                                                        UserVerificationRequirement.REQUIRED)
                                                .build())
                                .timeout(properties.timeout().toSeconds())
                                .build());
        try {
            challenges.store(
                    PasskeyChallengeService.Operation.REGISTRATION,
                    userId,
                    options.getChallenge(),
                    options.toJson());
            return objectMapper.readValue(
                    options.toCredentialsCreateJson(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize passkey registration options", e);
        }
    }

    /**
     * Finishes a registration ceremony and persists the credential.
     *
     * @param userId the authenticated account
     * @param sessionId the current login session
     * @param request the browser registration response
     * @return the safe credential metadata
     */
    @SuppressWarnings("deprecation") // Yubico 2.10 exposes BE/BS only as deprecated flag fields
    @Transactional
    public PasskeyResponse finishRegistration(
            UUID userId, UUID sessionId, PasskeyRegistrationRequest request) {
        recentAuthentication.requireRecent(userId, sessionId);
        User user = requireUser(userId);
        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs>
                response;
        try {
            response =
                    PublicKeyCredential.parseRegistrationResponseJson(
                            objectMapper.writeValueAsString(request.credential()));
        } catch (IOException e) {
            throw new UnauthorizedException(
                    ErrorCode.AUTH_033, "Passkey registration could not be verified");
        }

        String requestJson =
                challenges
                        .consume(
                                PasskeyChallengeService.Operation.REGISTRATION,
                                userId,
                                null,
                                response.getResponse().getClientData().getChallenge())
                        .requestJson();

        PublicKeyCredentialCreationOptions options;
        try {
            options = PublicKeyCredentialCreationOptions.fromJson(requestJson);
        } catch (JsonProcessingException e) {
            throw new UnauthorizedException(
                    ErrorCode.AUTH_033, "Passkey registration could not be verified");
        }

        String credentialId = response.getId().getBase64Url();
        if (passkeys.existsByCredentialId(credentialId)) {
            throw new ConflictException(ErrorCode.AUTH_034);
        }

        RegistrationResult result;
        try {
            result =
                    relyingParty.finishRegistration(
                            FinishRegistrationOptions.builder()
                                    .request(options)
                                    .response(response)
                                    .build());
        } catch (RegistrationFailedException e) {
            throw new UnauthorizedException(
                    ErrorCode.AUTH_033, "Passkey registration could not be verified");
        }

        if (!result.isUserVerified()) {
            throw new UnauthorizedException(
                    ErrorCode.AUTH_033, "Passkey registration requires user verification");
        }

        String name = PasskeyName.forRegistration(request.name());
        UserPasskey passkey = new UserPasskey();
        passkey.setUser(user);
        passkey.setCredentialId(credentialId);
        passkey.setPublicKeyCose(result.getPublicKeyCose().getBytes());
        passkey.setSignCount(result.getSignatureCount());
        passkey.setTransports(
                response.getResponse().getTransports().stream()
                        .map(transport -> transport.getId())
                        .toList());
        passkey.setAaguid(aaguid(result.getAaguid()));
        passkey.setBackupEligible(
                response.getResponse().getParsedAuthenticatorData().getFlags().BE);
        passkey.setBackupState(response.getResponse().getParsedAuthenticatorData().getFlags().BS);
        passkey.setName(name);
        passkeys.saveAndFlush(passkey);

        auditLog.log(
                userId,
                AuditAction.PASSKEY_REGISTERED,
                ResourceType.USER,
                userId.toString(),
                SecuritySeverity.INFO,
                AuditDetails.extension(Map.of("name", name)));
        notifications.credentialAdded(userId, user.getDisplayName(), user.getEmail(), name);
        return PasskeyResponse.fromEntity(passkey);
    }

    private User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException(ErrorCode.USER_004));
    }

    private static UUID aaguid(ByteArray aaguid) {
        if (aaguid == null || aaguid.size() != 16) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(aaguid.getBytes());
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
