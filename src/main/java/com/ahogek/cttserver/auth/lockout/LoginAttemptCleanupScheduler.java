package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.auth.repository.LoginAttemptRepository;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

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
    private final Duration retentionDuration;

    public LoginAttemptCleanupScheduler(
            LoginAttemptRepository loginAttemptRepository, SecurityProperties securityProperties) {
        this.loginAttemptRepository = loginAttemptRepository;
        this.retentionDuration = securityProperties.password().retentionDuration();
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
}
