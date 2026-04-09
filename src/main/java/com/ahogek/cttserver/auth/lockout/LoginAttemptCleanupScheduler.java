package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.auth.repository.LoginAttemptRepository;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.config.properties.SecurityProperties.PasswordProperties;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled cleanup of expired login attempt records.
 *
 * <p>Periodically deletes {@code login_attempts} rows older than the configured retention period to
 * prevent unbounded table growth. Uses the {@code idx_login_attempts_attempt_at} index for
 * efficient time-based deletion.
 *
 * <p>Design characteristics:
 *
 * <ul>
 *   <li>Fixed-delay scheduling prevents overlapping executions
 *   <li>Single transaction per cleanup run
 *   <li>Configurable retention period via {@code ctt.security.password.retention-duration}
 *   <li>Configurable interval via {@code ctt.security.lockout.cleanup-interval-ms}
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-09
 * @see LoginAttemptRepository#deleteOlderThan
 */
@Component
public class LoginAttemptCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptCleanupScheduler.class);

    private final LoginAttemptRepository loginAttemptRepository;
    private final UserRepository userRepository;
    private final Duration retentionDuration;
    private final int failureWindowSeconds;

    public LoginAttemptCleanupScheduler(
            LoginAttemptRepository loginAttemptRepository,
            UserRepository userRepository,
            SecurityProperties securityProperties) {
        this.loginAttemptRepository = loginAttemptRepository;
        this.userRepository = userRepository;
        PasswordProperties passwordProps = securityProperties.password();
        this.retentionDuration = passwordProps.retentionDuration();
        this.failureWindowSeconds = passwordProps.failureWindowSeconds();
    }

    /**
     * Deletes login attempt records older than the configured retention period.
     *
     * <p>Runs at a fixed delay to prevent unbounded growth of the login_attempts table. Only logs
     * when records are actually deleted to reduce noise.
     */
    @Scheduled(fixedDelayString = "${ctt.security.lockout.cleanup-interval-ms:3600000}")
    @Transactional
    public void cleanupExpiredAttempts() {
        Instant cutoff = Instant.now().minus(retentionDuration);
        int deleted = loginAttemptRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info(
                    "Cleaned up {} expired login attempts (older than {})",
                    deleted,
                    retentionDuration);
        }
    }

    /**
     * Automatically unlocks accounts that have been LOCKED but have no recent login attempts within
     * the failure window.
     *
     * <p>This serves as a safety-net for accounts that were locked due to brute-force attempts but
     * the user has not tried to log in again since the lockout period expired. The actual unlock
     * timing during active login attempts is handled by {@code
     * LoginAttemptService.checkLockStatus()} with precise sliding window logic.
     *
     * <p>Only logs when accounts are actually unlocked to reduce noise.
     */
    @Scheduled(fixedDelayString = "${ctt.security.lockout.cleanup-interval-ms:3600000}")
    @Transactional
    public void unlockExpiredAccounts() {
        List<User> lockedUsers = userRepository.findAllByStatus(UserStatus.LOCKED);
        if (lockedUsers.isEmpty()) {
            return;
        }

        Instant windowStart = Instant.now().minusSeconds(failureWindowSeconds);
        int unlocked = 0;

        for (User user : lockedUsers) {
            String emailHash = TokenUtils.hashToken(user.getEmail().toLowerCase());
            long recentAttempts =
                    loginAttemptRepository.countAttemptsInWindow(emailHash, windowStart);

            if (recentAttempts == 0) {
                user.reactivate();
                userRepository.save(user);
                unlocked++;
            }
        }

        if (unlocked > 0) {
            log.info("Auto-unlocked {} expired locked accounts", unlocked);
        }
    }
}
