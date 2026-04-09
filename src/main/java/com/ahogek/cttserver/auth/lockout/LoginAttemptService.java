package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.config.properties.SecurityProperties.PasswordProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Facade service for login attempt tracking and account lockout management.
 *
 * <p>Coordinates between the lockout strategy, user repository, and security properties to provide
 * a unified API for recording login attempts and checking lock status.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-09
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final LockoutStrategyPort lockoutStrategy;
    private final UserRepository userRepository;
    private final PasswordProperties passwordProps;

    public LoginAttemptService(
            LockoutStrategyPort lockoutStrategy,
            UserRepository userRepository,
            SecurityProperties securityProperties) {
        this.lockoutStrategy = lockoutStrategy;
        this.userRepository = userRepository;
        this.passwordProps = securityProperties.password();
    }

    /**
     * Checks if account is locked and throws ForbiddenException if so.
     *
     * <p>Also handles auto-unlock: if lock expired, unlocks and clears failure state.
     *
     * @param email the email address to check
     * @throws NotFoundException if user not found
     * @throws ForbiddenException with AUTH_004 if account is locked and not expired
     */
    public void checkLockStatus(String email) {
        User user = userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AUTH_001, "User not found"));

        if (lockoutStrategy.shouldAutoUnlock(user)) {
            lockoutStrategy.recordSuccess(user);
            userRepository.save(user);
            return;
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new ForbiddenException(
                    ErrorCode.AUTH_004,
                    "Account is locked due to too many failed attempts");
        }

        if (user.getFailedLoginAttempts() != null
                && user.getFailedLoginAttempts() >= passwordProps.maxFailedAttempts()) {
            throw new ForbiddenException(
                    ErrorCode.AUTH_004,
                    "Account is locked due to too many failed attempts");
        }
    }

    /**
     * Records a failed login attempt.
     *
     * <p>Delegates to lockoutStrategy.recordFailure() and persists the user entity.
     *
     * @param user the user entity
     * @param ip the IP address of the login attempt (logged for audit purposes)
     */
    public void recordFailure(User user, String ip) {
        lockoutStrategy.recordFailure(
                user,
                passwordProps.maxFailedAttempts(),
                passwordProps.lockDuration(),
                passwordProps.failureWindowSeconds());
        userRepository.save(user);

        log.warn(
                "Failed login attempt for user {} from IP {}. Total failures: {}",
                user.getEmail(),
                ip,
                user.getFailedLoginAttempts());
    }

    /**
     * Records successful login - clears all failure state.
     *
     * <p>Delegates to lockoutStrategy.recordSuccess() and persists the user entity.
     *
     * @param user the user entity
     */
    public void recordSuccess(User user) {
        lockoutStrategy.recordSuccess(user);
        userRepository.save(user);
    }

    /**
     * Returns true if user is currently locked (LOCKED status + not expired).
     *
     * @param user the user entity to check
     * @return true if locked and not auto-unlockable
     */
    public boolean isLocked(User user) {
        return user.getStatus() == UserStatus.LOCKED
                && !lockoutStrategy.shouldAutoUnlock(user);
    }
}
