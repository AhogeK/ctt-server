package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.entity.RefreshToken;
import com.ahogek.cttserver.auth.lockout.LoginAttemptService;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.config.properties.TermsProperties;
import com.ahogek.cttserver.common.context.RequestContext;
import com.ahogek.cttserver.common.context.RequestInfo;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.device.repository.DeviceRepository;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * User login service handling authentication and JWT token generation.
 *
 * <p>This service validates user credentials, generates access and refresh tokens, and records
 * audit events for login attempts (success/failure).
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-03
 */
@Service
public class UserLoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DeviceRepository deviceRepository;
    private final AuditLogService auditLogService;
    private final LoginAttemptService loginAttemptService;
    private final SecurityProperties.JwtProperties jwtProps;
    private final TermsProperties termsProperties;

    public UserLoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenRepository refreshTokenRepository,
            DeviceRepository deviceRepository,
            AuditLogService auditLogService,
            LoginAttemptService loginAttemptService,
            SecurityProperties securityProperties,
            TermsProperties termsProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.deviceRepository = deviceRepository;
        this.auditLogService = auditLogService;
        this.loginAttemptService = loginAttemptService;
        this.jwtProps = securityProperties.jwt();
        this.termsProperties = termsProperties;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user =
                userRepository
                        .findByEmailIgnoreCase(request.email())
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                ErrorCode.AUTH_001, "Invalid email or password"));

        validateUserStatus(user);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedLogin(user);
        }

        // Only reached on successful authentication (handleFailedLogin throws)
        loginAttemptService.recordSuccess(user.getEmail());

        String clientIp = RequestContext.current().map(RequestInfo::clientIp).orElse(null);
        user.setLastLoginAt(Instant.now());
        user.setLastLoginIp(clientIp);

        UUID sessionId = UUID.randomUUID();
        String accessToken = jwtTokenProvider.generateAccessToken(user, sessionId);
        String refreshToken = createRefreshToken(user.getId(), request.deviceId(), sessionId);

        auditLogService.logSuccess(
                user.getId(),
                AuditAction.LOGIN_SUCCESS,
                ResourceType.USER,
                user.getId().toString());

        boolean termsExpired = !termsProperties.currentVersion().equals(user.getTermsVersion());

        return new LoginResponse(
                user.getId(),
                accessToken,
                refreshToken,
                jwtProps.accessTokenTtl().getSeconds(),
                "Bearer",
                termsExpired);
    }

    /**
     * Issues JWT tokens for an authenticated user.
     *
     * <p>Generates a new access token and refresh token without performing authentication. Used for
     * token refresh and terms acceptance flows where the user is already validated.
     *
     * @param user the authenticated user entity
     * @param sessionId the session the new tokens belong to
     * @return LoginResponse containing access token, refresh token, and terms expiration status
     */
    public LoginResponse issueTokens(User user, UUID sessionId) {
        // A token minted outside a session (no sid claim) still needs a session id: the column is
        // not nullable, and a fresh id is the honest answer for "this issuance started one".
        UUID effectiveSessionId = sessionId != null ? sessionId : UUID.randomUUID();
        // Re-issuing continues the session the caller presented: audience, device binding, client
        // User-Agent and the TTL that follows from the audience all belong to the session, so
        // rebuilding them from scratch would turn a plugin session into a web one — and drop its
        // device — the first time such a session accepted new terms.
        RefreshToken liveToken =
                sessionId == null
                        ? null
                        : refreshTokenRepository
                                .findFirstByUserIdAndSessionIdAndRevokedAtIsNullOrderByCreatedAtDesc(
                                        user.getId(), sessionId)
                                .orElse(null);
        boolean pluginSession = liveToken != null && liveToken.isPluginSession();
        String issuedFor = pluginSession ? RefreshToken.AUDIENCE_PLUGIN : RefreshToken.AUDIENCE_WEB;
        UUID deviceId = liveToken != null ? liveToken.getDeviceId() : null;
        String userAgent = liveToken != null ? liveToken.getUserAgent() : currentUserAgent();
        Duration ttl =
                pluginSession ? jwtProps.refreshTokenTtlPlugin() : jwtProps.refreshTokenTtlWeb();

        String accessToken = jwtTokenProvider.generateAccessToken(user, effectiveSessionId);
        TokenUtils.RefreshTokenPair tokenPair =
                TokenUtils.createRefreshToken(
                        user.getId(),
                        issuedFor,
                        ttl,
                        deviceId,
                        effectiveSessionId,
                        userAgent,
                        refreshTokenRepository);

        boolean termsExpired = !termsProperties.currentVersion().equals(user.getTermsVersion());

        return new LoginResponse(
                user.getId(),
                accessToken,
                tokenPair.rawToken(),
                jwtProps.accessTokenTtl().getSeconds(),
                "Bearer",
                termsExpired);
    }

    private void validateUserStatus(User user) {
        User checkedUser = loginAttemptService.checkLockStatus(user);

        // Check non-locked status issues (not handled by LoginAttemptService)
        if (checkedUser.getStatus() == UserStatus.ACTIVE) {
            return;
        }
        throw switch (checkedUser.getStatus()) {
            case PENDING_VERIFICATION ->
                    new ForbiddenException(ErrorCode.AUTH_006, "Email not verified");
            case SUSPENDED, DELETED ->
                    new ForbiddenException(ErrorCode.AUTH_005, "Account is disabled");
            case LOCKED, ACTIVE -> throw new AssertionError("Unreachable");
        };
    }

    private void handleFailedLogin(User user) {
        String clientIp = RequestContext.current().map(RequestInfo::clientIp).orElse(null);
        loginAttemptService.recordFailure(user.getEmail(), clientIp);

        auditLogService.logFailure(
                user.getId(),
                AuditAction.LOGIN_FAILED,
                ResourceType.USER,
                user.getId().toString(),
                "Invalid password");

        throw new UnauthorizedException(ErrorCode.AUTH_001, "Invalid email or password");
    }

    private String createRefreshToken(UUID userId, String deviceId, UUID sessionId) {
        UUID deviceUuid = parseDeviceId(deviceId);
        // A registered device is an IDE plugin client, and only that makes the session a plugin
        // session: every login carries a device id (the request DTO requires one), so the id itself
        // proves nothing. The registry is the server-verifiable statement of "this client is a
        // plugin", and it is why a plugin TTL exists at all.
        boolean pluginSession = isRegisteredDevice(userId, deviceUuid);
        String issuedFor = pluginSession ? RefreshToken.AUDIENCE_PLUGIN : RefreshToken.AUDIENCE_WEB;
        Duration ttl =
                pluginSession ? jwtProps.refreshTokenTtlPlugin() : jwtProps.refreshTokenTtlWeb();

        TokenUtils.RefreshTokenPair tokenPair =
                TokenUtils.createRefreshToken(
                        userId,
                        issuedFor,
                        ttl,
                        deviceUuid,
                        sessionId,
                        currentUserAgent(),
                        refreshTokenRepository);

        return tokenPair.rawToken();
    }

    private boolean isRegisteredDevice(UUID userId, UUID deviceUuid) {
        return deviceUuid != null
                && deviceRepository
                        .findById(deviceUuid)
                        .filter(device -> userId.equals(device.getUserId()))
                        .isPresent();
    }

    private String currentUserAgent() {
        return RequestContext.current().map(RequestInfo::userAgent).orElse(null);
    }

    private UUID parseDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(deviceId);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }
}
