package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.auth.repository.LoginAttemptRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for lockout strategy selection.
 * <p>
 * Provides conditional bean definitions based on the {@code ctt.security.password.storage} property.
 * Defaults to database storage if the property is not specified.
 * </p>
 *
 * @author AhogeK
 * @since 1.0.0
 */
@Configuration
public class LockoutConfig {

    /**
     * Database-backed lockout strategy.
     * Active when {@code ctt.security.password.lockout.storage} is "DB" or missing (default).
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "ctt.security.password.lockout",
            name = "storage",
            havingValue = "DB",
            matchIfMissing = true)
    public LockoutStrategyPort dbLockoutStrategy(LoginAttemptRepository loginAttemptRepository) {
        return new DbLockoutStrategy(loginAttemptRepository);
    }
}