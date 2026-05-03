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
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
 * @author AhogeK
 * @since 0.20.0
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
            OAuthProvider provider, String accessToken, GitHubUserInfo userInfo) {
        return oauthAccountRepository
                .findByProviderAndProviderUserId(provider, String.valueOf(userInfo.id()))
                .map(account -> handleExistingBinding(account, accessToken, userInfo))
                .orElseGet(() -> handleNewBinding(provider, accessToken, userInfo));
    }

    /** Handles existing OAuth binding: validate status, update tokens, issue credentials. */
    private LoginResponse handleExistingBinding(
            UserOAuthAccount account, String accessToken, GitHubUserInfo userInfo) {
        User user = account.getUser();

        validateUserStatus(user);

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
            OAuthProvider provider, String accessToken, GitHubUserInfo userInfo) {
        return userRepository
                .findByEmailIgnoreCase(userInfo.email())
                .map(
                        existingUser ->
                                linkToExistingUser(existingUser, provider, accessToken, userInfo))
                .orElseGet(() -> registerNewUser(provider, accessToken, userInfo));
    }

    /** Links OAuth account to existing local user by email. */
    private LoginResponse linkToExistingUser(
            User user, OAuthProvider provider, String accessToken, GitHubUserInfo userInfo) {
        UserOAuthAccount newAccount =
                createOAuthAccountEntity(user, provider, accessToken, userInfo);
        oauthAccountRepository.save(newAccount);

        auditLogService.logSuccess(
                user.getId(),
                AuditAction.OAUTH_ACCOUNT_LINKED,
                ResourceType.USER,
                user.getId().toString());

        return handleExistingBinding(newAccount, accessToken, userInfo);
    }

    /** Registers new OAuth-only user (ACTIVE status, no password, email verified). */
    private LoginResponse registerNewUser(
            OAuthProvider provider, String accessToken, GitHubUserInfo userInfo) {
        User newUser = new User();
        newUser.setEmail(userInfo.email());
        newUser.setDisplayName(userInfo.name() != null ? userInfo.name() : userInfo.login());
        newUser.setPasswordHash(null);

        // OAuth provider verified email - activate user directly
        newUser.verifyEmail();

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
                user.getId(), accessToken, refreshToken, jwtProps.accessTokenTtl().getSeconds(), "Bearer", termsExpired);
    }

    /** Creates refresh token for OAuth login. */
    private String createRefreshToken(UUID userId) {
        Duration ttl = jwtProps.refreshTokenTtlWeb();

        TokenUtils.RefreshTokenPair tokenPair =
                TokenUtils.createRefreshToken(
                        userId, ISSUED_FOR_WEB, ttl, null, refreshTokenRepository);

        return tokenPair.rawToken();
    }
}
