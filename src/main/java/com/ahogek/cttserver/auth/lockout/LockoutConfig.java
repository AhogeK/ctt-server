package com.ahogek.cttserver.auth.lockout;

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
     * Creates a database-based lockout strategy.
     * <p>
     * This bean is activated when {@code ctt.security.password.storage} is set to "DB"
     * or when the property is missing (default behavior).
     * </p>
     *
     * @return a database-backed lockout strategy implementation
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "ctt.security.password",
        name = "storage",
        havingValue = "DB",
        matchIfMissing = true)
    public LockoutStrategyPort dbLockoutStrategy() {
        return new DbLockoutStrategy();
    }

    /**
     * Creates a Redis-based lockout strategy.
     * <p>
     * This bean is activated when {@code ctt.security.password.storage} is set to "REDIS".
     * </p>
     *
     * @return a Redis-backed lockout strategy implementation
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "ctt.security.password",
        name = "storage",
        havingValue = "REDIS")
    public LockoutStrategyPort redisLockoutStrategy() {
        return new RedisLockoutStrategy();
    }
}