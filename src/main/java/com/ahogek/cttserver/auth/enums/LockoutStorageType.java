package com.ahogek.cttserver.auth.enums;

/**
 * Lockout storage strategy enumeration.
 *
 * <p>Defines where account lockout tracking data is stored:
 *
 * <ul>
 *   <li>DB: PostgreSQL-based storage (default, simpler, good for single-instance deployments)
 *   <li>REDIS: Redis-based storage (future enhancement, better for distributed systems)
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 0.2.0
 */
public enum LockoutStorageType {
    DB,
    REDIS
}