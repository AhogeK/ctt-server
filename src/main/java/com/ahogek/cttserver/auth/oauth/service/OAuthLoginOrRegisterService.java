package com.ahogek.cttserver.auth.oauth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.oauth.entity.UserOAuthAccount;
import com.ahogek.cttserver.auth.oauth.enums.OAuthProvider;
import com.ahogek.cttserver.auth.oauth.model.GitHubUserInfo;
import com.ahogek.cttserver.auth.oauth.repository.UserOAuthAccountRepository;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.auth.service.JwtTokenProvider;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.config.properties.TermsProperties;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * OAuth login/registration orchestration service.
 *
 * <p>Handles third-party OAuth authentication flow, performing identity coordination between OAuth
 * providers and local accounts. Supports:
 *
 * <ul>
 *   <li>Existing OAuth binding: validate status, update tokens, issue CTT credentials
 *   <li>Email-based auto-merge: link OAuth to existing local account
 *   <li>New user registration: create OAuth-only user (ACTIVE, no password)
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-22
 */
@Service
public class OAuthLoginOrRegisterService {

    private static final String ISSUED_FOR_WEB = "WEB";

    private final UserOAuthAccountRepository oauthAccountRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;
    private final SecurityProperties.JwtProperties jwtProps;
    private final TermsProperties termsProperties;

    public OAuthLoginOrRegisterService(
            UserOAuthAccountRepository oauthAccountRepository,
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenRepository refreshTokenRepository,
            AuditLogService auditLogService,
            SecurityProperties securityProperties,
            TermsProperties termsProperties) {
        this.oauthAccountRepository = oauthAccountRepository;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogService = auditLogService;
        this.jwtProps = securityProperties.jwt();
        this.termsProperties = termsProperties;
    }

    /**
     * Processes OAuth authentication and returns CTT credentials.
     *
     * <p>Flow:
     *
     * <ol>
     *   <li>Query existing OAuth binding by (provider, providerUserId)
     *   <li>If found: validate user status, update tokens, issue credentials
     *   <li>If not found: check email for merge or create new user
     * </ol>
     *
     * @param provider the OAuth provider
     * @param accessToken the OAuth access token from provider (GitHub token)
     * @param userInfo the GitHub user info from OAuth callback
     * @return LoginResponse with CTT access and refresh tokens
     */
    @Transactional
    public LoginResponse process(
            OAuthProvider provider,
            String accessToken,
            GitHubUserInfo userInfo,
            String clientIp) {
        return oauthAccountRepository
                .findByProviderAndProviderUserId(provider, String.valueOf(userInfo.id()))
                .map(account -> handleExistingBinding(account, accessToken, userInfo, clientIp))
                .orElseGet(() -> handleNewBinding(provider, accessToken, userInfo, clientIp));
    }

    /** Handles existing OAuth binding: validate status, update tokens, issue credentials. */
    private LoginResponse handleExistingBinding(
            UserOAuthAccount account,
            String accessToken,
            GitHubUserInfo userInfo,
            String clientIp) {
        User user = account.getUser();

        validateUserStatus(user);

        user.setLastLoginAt(Instant.now());
        user.setLastLoginIp(clientIp);

        account.setAccessToken(accessToken);
        account.setProviderLogin(userInfo.login());
        account.setProviderEmail(userInfo.email());
        account.setTokenExpiresAt(null);

        oauthAccountRepository.save(account);

        auditLogService.logSuccess(
                user.getId(),
                AuditAction.OAUTH_LOGIN_SUCCESS,
                ResourceType.USER,
                user.getId().toString());

        return createLoginResponse(user);
    }

    /** Handles new OAuth binding: email merge or new user registration. */
    private LoginResponse handleNewBinding(
            OAuthProvider provider, String accessToken, GitHubUserInfo userInfo, String clientIp) {
        return userRepository
                .findByEmailIgnoreCase(userInfo.email())
                .map(
                        existingUser ->
                                linkToExistingUser(
                                        existingUser, provider, accessToken, userInfo, clientIp))
                .orElseGet(() -> registerNewUser(provider, accessToken, userInfo, clientIp));
    }

    /**
     * Links OAuth account to existing local user by email merge, then performs login.
     *
     * <p>Creates the OAuth binding and delegates to {@link #handleExistingBinding} which issues
     * CTT tokens and sets {@code lastLoginAt}. This is a login flow (not just binding) because the
     * user receives new authentication tokens.
     */
    private LoginResponse linkToExistingUser(
            User user,
            OAuthProvider provider,
            String accessToken,
            GitHubUserInfo userInfo,
            String clientIp) {
        UserOAuthAccount newAccount =
                createOAuthAccountEntity(user, provider, accessToken, userInfo);
        oauthAccountRepository.save(newAccount);

        auditLogService.logSuccess(
                user.getId(),
                AuditAction.OAUTH_ACCOUNT_LINKED,
                ResourceType.USER,
                user.getId().toString());

        return handleExistingBinding(newAccount, accessToken, userInfo, clientIp);
    }

    /** Registers new OAuth-only user (ACTIVE status, no password, email verified). */
    private LoginResponse registerNewUser(
            OAuthProvider provider, String accessToken, GitHubUserInfo userInfo, String clientIp) {
        User newUser = new User();
        newUser.setEmail(userInfo.email());
        newUser.setDisplayName(userInfo.name() != null ? userInfo.name() : userInfo.login());
        newUser.setPasswordHash(null);

        // OAuth provider verified email - activate user directly
        newUser.verifyEmail();

        // Set terms acceptance for new OAuth users (same as email-verified users)
        newUser.setTermsAcceptedAt(Instant.now());
        newUser.setTermsVersion(termsProperties.currentVersion());
        newUser.setLastLoginAt(Instant.now());
        newUser.setLastLoginIp(clientIp);

        userRepository.save(newUser);

        UserOAuthAccount account =
                createOAuthAccountEntity(newUser, provider, accessToken, userInfo);
        oauthAccountRepository.save(account);

        auditLogService.logSuccess(
                newUser.getId(),
                AuditAction.OAUTH_LOGIN_SUCCESS,
                ResourceType.USER,
                newUser.getId().toString());

        return createLoginResponse(newUser);
    }

    /** Creates OAuth account entity with user binding. */
    private UserOAuthAccount createOAuthAccountEntity(
            User user, OAuthProvider provider, String accessToken, GitHubUserInfo userInfo) {
        UserOAuthAccount account = new UserOAuthAccount();
        account.setUser(user);
        account.setProvider(provider);
        account.setProviderUserId(String.valueOf(userInfo.id()));
        account.setProviderLogin(userInfo.login());
        account.setProviderEmail(userInfo.email());
        account.setAccessToken(accessToken);
        account.setTokenExpiresAt(null);
        return account;
    }

    /**
     * Validates user status for OAuth login.
     *
     * <p>OAuth login requires ACTIVE status. LOCKED, SUSPENDED, DELETED are rejected.
     *
     * @param user the user to validate
     * @throws ForbiddenException if user status is not ACTIVE
     */
    private void validateUserStatus(User user) {
        if (user.getStatus() == UserStatus.ACTIVE) {
            return;
        }

        throw switch (user.getStatus()) {
            case LOCKED -> new ForbiddenException(ErrorCode.AUTH_004, "Account locked");
            case SUSPENDED, DELETED ->
                    new ForbiddenException(ErrorCode.AUTH_005, "Account disabled");
            case PENDING_VERIFICATION ->
                    new ForbiddenException(ErrorCode.AUTH_006, "Email not verified");
            case ACTIVE -> throw new AssertionError("Unreachable");
        };
    }

    /** Creates LoginResponse with access and refresh tokens. */
    private LoginResponse createLoginResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = createRefreshToken(user.getId());
        boolean termsExpired = !termsProperties.currentVersion().equals(user.getTermsVersion());

        return new LoginResponse(
                user.getId(),
                accessToken,
                refreshToken,
                jwtProps.accessTokenTtl().getSeconds(),
                "Bearer",
                termsExpired);
    }

    /** Creates refresh token for OAuth login. */
    private String createRefreshToken(UUID userId) {
        Duration ttl = jwtProps.refreshTokenTtlWeb();

        TokenUtils.RefreshTokenPair tokenPair =
                TokenUtils.createRefreshToken(
                        userId, ISSUED_FOR_WEB, ttl, null, refreshTokenRepository);

        return tokenPair.rawToken();
    }

    /**
     * Attaches a third-party OAuth account to an existing local user (BIND flow).
     *
     * <p><b>Session invariant</b>: this method does NOT issue new CTT access/refresh tokens. The
     * user's existing session ({@code ctt_access_token} / {@code ctt_refresh_token} in browser
     * storage) remains valid and unchanged after a successful bind. Browser-side tokens are
     * byte-for-byte identical before and after the operation.
     *
     * <p>If the bind fails (conflict, validation, etc.), no state mutation occurs and the session
     * is likewise unaffected.
     *
     * @param currentUserId the user performing the binding (from {@link
     *     com.ahogek.cttserver.auth.oauth.model.OAuthStatePayload})
     * @param provider the OAuth provider (e.g. {@link OAuthProvider#GITHUB})
     * @param accessToken the OAuth access token from the provider
     * @param userInfo the GitHub user info returned by the provider
     * @throws NotFoundException ({@code USER_004}) if no user exists with the given id
     * @throws ForbiddenException ({@code AUTH_006}) if the user is not in {@link UserStatus#ACTIVE}
     * @throws ConflictException ({@code AUTH_016}) if the provider user ID is already linked to a
     *     different user, or if the current user already has a binding for the given provider
     * @since 2026-06-28
     */
    @Transactional
    public void attachToExistingUser(
            UUID currentUserId,
            OAuthProvider provider,
            String accessToken,
            GitHubUserInfo userInfo) {
        User user =
                userRepository
                        .findById(currentUserId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.USER_004, "User not found"));

        validateUserStatus(user);

        String providerUserId = String.valueOf(userInfo.id());

        oauthAccountRepository
                .findByProviderAndProviderUserId(provider, providerUserId)
                .ifPresent(
                        existing -> {
                            if (!existing.getUser().getId().equals(currentUserId)) {
                                throw new ConflictException(
                                        ErrorCode.AUTH_016,
                                        "OAuth account already linked to another user");
                            }
                        });

        if (oauthAccountRepository.existsByUserIdAndProvider(currentUserId, provider)) {
            throw new ConflictException(
                    ErrorCode.AUTH_016, "Provider already linked to current user");
        }

        UserOAuthAccount account =
                createOAuthAccountEntity(user, provider, accessToken, userInfo);
        oauthAccountRepository.save(account);

        auditLogService.logSuccess(
                currentUserId,
                AuditAction.OAUTH_ACCOUNT_LINKED,
                ResourceType.USER,
                currentUserId.toString());
    }

    /**
     * Detaches a third-party OAuth account from the current user (UNBIND flow).
     *
     * <p>Removes the {@link UserOAuthAccount} row matching {@code (currentUserId, provider)}.
     * The provider-side OAuth grant is left untouched (this is a local unbind only).
     *
     * <p><b>Last-login-method guard</b>: a user must always retain at least one credential
     * (password or OAuth). Unbinding is rejected with {@link ErrorCode#AUTH_018} when:
     *
     * <ul>
     *   <li>the user has no password set ({@code passwordHash == null}), AND
     *   <li>this OAuth binding is the only one they have
     * </ul>
     *
     * <p><b>Session invariant</b>: this method does NOT issue or revoke CTT tokens. The user's
     * existing session ({@code ctt_access_token} / {@code ctt_refresh_token} in browser storage)
     * remains valid and unchanged after a successful unbind. The user stays logged in via their
     * remaining credentials.
     *
     * <p>Audit log emits {@link AuditAction#OAUTH_ACCOUNT_UNLINKED} on success.
     *
     * @param currentUserId the user performing the unbind
     * @param provider the OAuth provider to unbind
     * @throws NotFoundException ({@code AUTH_017}) if no binding exists for this user and provider
     * @throws ConflictException ({@code AUTH_018}) if unbinding would leave the user with no
     *     credentials
     * @since 2026-06-29
     */
    @Transactional
    public void unbindFromExistingUser(UUID currentUserId, OAuthProvider provider) {
        UserOAuthAccount binding =
                oauthAccountRepository
                        .findByUserIdAndProvider(currentUserId, provider)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.AUTH_017,
                                                "OAuth account not linked to this user"));

        boolean userHasPassword = binding.getUser().getPasswordHash() != null;
        long oauthCount = oauthAccountRepository.countByUserId(currentUserId);
        if (!userHasPassword && oauthCount <= 1) {
            throw new ConflictException(
                    ErrorCode.AUTH_018, "Cannot unlink the last login method");
        }

        oauthAccountRepository.delete(binding);

        auditLogService.logSuccess(
                currentUserId,
                AuditAction.OAUTH_ACCOUNT_UNLINKED,
                ResourceType.USER,
                currentUserId.toString());
    }
}
