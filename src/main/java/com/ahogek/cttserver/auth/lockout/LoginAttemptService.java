package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.auth.repository.LoginAttemptRepository;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.config.properties.SecurityProperties.PasswordProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Facade service for login attempt tracking and account lockout management.
 *
 * <p>Coordinates between the lockout strategy, login attempt repository, and user repository to
 * provide a unified API for recording login attempts and checking lock status. All public methods
 * operate on email addresses rather than User entity references.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-09
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final String UNKNOWN_IP = "0.0.0.0";

    private final LockoutStrategyPort lockoutStrategy;
    private final LoginAttemptRepository loginAttemptRepository;
    private final UserRepository userRepository;
    private final PasswordProperties passwordProps;

    public LoginAttemptService(
            LockoutStrategyPort lockoutStrategy,
            LoginAttemptRepository loginAttemptRepository,
            UserRepository userRepository,
            SecurityProperties securityProperties) {
        this.lockoutStrategy = lockoutStrategy;
        this.loginAttemptRepository = loginAttemptRepository;
        this.userRepository = userRepository;
        this.passwordProps = securityProperties.password();
    }

    /**
     * Checks if account is locked and throws ForbiddenException if so.
     *
     * <p>Also handles auto-unlock: if lock expired, unlocks and clears failure state.
     * Uses optimistic locking with retry to prevent TOCTOU race conditions when
     * concurrent requests attempt to lock/unlock the same account.
     *
     * @param user the user entity to check (already loaded by caller)
     * @throws NotFoundException if user not found during retry refresh
     * @throws ForbiddenException with AUTH_004 if account is locked and not expired
     */
    @Transactional
    public void checkLockStatus(User user) {
        // Retry loop for optimistic locking to prevent TOCTOU race condition
        int retries = 0;
        while (true) {
            try {
                String emailHash = TokenUtils.hashToken(user.getEmail().toLowerCase());

                if (lockoutStrategy.shouldAutoUnlock(
                        emailHash, user.getStatus(), passwordProps.lockDuration(), passwordProps.failureWindowSeconds())) {
                    lockoutStrategy.recordSuccess(emailHash);
                    user.reactivate();
                    userRepository.save(user);
                }

                if (user.getStatus() == UserStatus.LOCKED) {
                    throw new ForbiddenException(
                            ErrorCode.AUTH_004,
                            "Account is locked due to too many failed attempts");
                }

                Instant windowStart = Instant.now().minusSeconds(passwordProps.failureWindowSeconds());
                long recentAttempts = loginAttemptRepository.countAttemptsInWindow(emailHash, windowStart);
                if (recentAttempts >= passwordProps.maxFailedAttempts()) {
                    throw new ForbiddenException(
                            ErrorCode.AUTH_004,
                            "Account is locked due to too many failed attempts");
                }

                return;
            } catch (ObjectOptimisticLockingFailureException _) {
                if (retries >= 2) {
                    throw new ForbiddenException(ErrorCode.AUTH_004, "Account lockout check failed due to concurrent access");
                }
                retries++;
                user = userRepository.findById(user.getId()).orElseThrow(
                        () -> new NotFoundException(ErrorCode.AUTH_001, "User not found"));
            }
        }
    }

    /**
     * Records a failed login attempt.
     *
     * <p>Creates a hashed LoginAttempt record and persists it. Also updates User entity fields
     * for backward compatibility with the existing lockout strategy.
     *
     * @param email the email address of the login attempt
     * @param ip the IP address of the login attempt (logged for audit purposes)
     */
    @Transactional
    public void recordFailure(String email, String ip) {
        String emailHash = TokenUtils.hashToken(email.toLowerCase());
        String ipHash = TokenUtils.hashToken(ip != null ? ip : UNKNOWN_IP);

        lockoutStrategy.recordFailure(
                emailHash,
                ipHash,
                passwordProps.maxFailedAttempts(),
                passwordProps.lockDuration(),
                passwordProps.failureWindowSeconds());

        Instant windowStart = Instant.now().minusSeconds(passwordProps.failureWindowSeconds());
        long recentAttempts = loginAttemptRepository.countAttemptsInWindow(emailHash, windowStart);

        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (recentAttempts >= passwordProps.maxFailedAttempts() && user.getStatus() == UserStatus.ACTIVE) {
                user.lockAccount();
            }
            userRepository.save(user);

            log.warn(
                    "Failed login attempt for user {} from IP {}. Total failures in window: {}",
                    user.getEmail(),
                    ip,
                    recentAttempts);
        });
    }

    /**
     * Records successful login - clears all failure state.
     *
     * <p>Deletes all login attempt records for the email and clears User entity lockout fields
     * for backward compatibility.
     *
     * @param email the email address of the successful login
     */
    @Transactional
    public void recordSuccess(String email) {
        String emailHash = TokenUtils.hashToken(email.toLowerCase());

        lockoutStrategy.recordSuccess(emailHash);

        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (user.getStatus() == UserStatus.LOCKED) {
                user.reactivate();
                userRepository.save(user);
            }
        });
    }

    /**
     * Returns true if user is currently locked (LOCKED status + not expired).
     *
     * @param email the email address to check
     * @return true if locked and not auto-unlockable
     */
    public boolean isLocked(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(user -> {
                    String emailHash = TokenUtils.hashToken(email.toLowerCase());
                    return user.getStatus() == UserStatus.LOCKED
                            && !lockoutStrategy.shouldAutoUnlock(emailHash, user.getStatus(), passwordProps.lockDuration(), passwordProps.failureWindowSeconds());
                })
                .orElse(false);
    }
}
