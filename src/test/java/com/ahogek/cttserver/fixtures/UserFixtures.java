package com.ahogek.cttserver.fixtures;

import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;

import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

/**
 * User test data factory using Object Mother and Builder patterns.
 *
 * <p><b>Usage guidelines:</b>
 *
 * <ul>
 *   <li>Unpersisted scenarios (Service/Controller Slice): Call {@link Builder#build()} or preset
 *       role methods directly
 *   <li>Needs persistence (Repository/Integration): Persist via {@link
 *       PersistedFixtures#user(TestEntityManager, Builder)}
 *   <li>All {@code passwordHash} fields default to BCrypt(cost=4) hash of "Test@1234" for test
 *       speed optimization
 * </ul>
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * // Object Mother - preset roles
 * var regularUser = UserFixtures.regularUser().build();
 * var admin = UserFixtures.admin().build();
 * var locked = UserFixtures.lockedUser().build();
 *
 * // Custom builder
 * var custom = UserFixtures.builder()
 *         .email("custom@test.example")
 *         .displayName("Custom User")
 *         .status(UserStatus.ACTIVE)
 *         .build();
 *
 * // Persisted (Repository test)
 * var persisted = PersistedFixtures.user(em, UserFixtures.regularUser());
 * }</pre>
 *
 * <p><b>Note:</b> User entity uses state machine pattern. The status field is set via
 * ReflectionTestUtils to bypass state transition validation for test data creation.
 *
 * @author AhogeK
 * @since 2026-03-18
 */
public final class UserFixtures {

    /**
     * Default raw password for all test users. BCrypt(cost=4) for fast test execution.
     *
     * <p>Production uses cost=12 (~250ms per hash), test uses cost=4 (~2ms per hash).
     */
    public static final String DEFAULT_RAW_PASSWORD = "Test@1234";

    /**
     * Pre-computed BCrypt(cost=4) hash of "Test@1234".
     *
     * <p>Avoid computing hash repeatedly in tests for performance.
     */
    public static final String DEFAULT_PASSWORD_HASH =
            "$2a$04$NxRH7YlWJkq8Q8ZyFJxJlO8ZqHbGmHvJ7kH9TqVyC8Iz6HJKvMzK.";

    private UserFixtures() {}

    // ==========================================
    // Object Mother - Preset Roles
    // ==========================================

    /**
     * Creates a regular active user with random unique email.
     *
     * <p>Status: ACTIVE, emailVerified: true, failedLoginAttempts: 0
     *
     * @return builder for further customization
     */
    public static Builder regularUser() {
        return builder()
                .email("user_" + shortUuid() + "@test.example")
                .displayName("Test User")
                .status(UserStatus.ACTIVE)
                .emailVerified(true);
    }

    /**
     * Creates an admin user with random unique email.
     *
     * <p>Status: ACTIVE, emailVerified: true
     *
     * @return builder for further customization
     */
    public static Builder admin() {
        return builder()
                .email("admin_" + shortUuid() + "@test.example")
                .displayName("Admin User")
                .status(UserStatus.ACTIVE)
                .emailVerified(true);
    }

    /**
     * Creates a locked user due to too many failed login attempts.
     *
     * <p>Status: LOCKED, failedLoginAttempts: 5
     *
     * @return builder for further customization
     */
    public static Builder lockedUser() {
        return regularUser().status(UserStatus.LOCKED).failedLoginAttempts(5);
    }

    /**
     * Creates a user pending email verification.
     *
     * <p>Status: PENDING_VERIFICATION, emailVerified: false
     *
     * @return builder for further customization
     */
    public static Builder pendingUser() {
        return builder()
                .email("pending_" + shortUuid() + "@test.example")
                .displayName("Pending User")
                .status(UserStatus.PENDING_VERIFICATION)
                .emailVerified(false);
    }

    /**
     * Creates a suspended user.
     *
     * <p>Status: SUSPENDED
     *
     * @return builder for further customization
     */
    public static Builder suspendedUser() {
        return regularUser().status(UserStatus.SUSPENDED);
    }

    /**
     * Creates a deleted user (soft-deleted).
     *
     * <p>Status: DELETED
     *
     * @return builder for further customization
     */
    public static Builder deletedUser() {
        return builder()
                .email(UUID.randomUUID() + "@deleted.local")
                .displayName("Deleted User")
                .status(UserStatus.DELETED);
    }

    // ==========================================
    // Builder
    // ==========================================

    /** Creates a new builder for custom user construction. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for User test data. */
    public static final class Builder {
        private String email = "test_" + shortUuid() + "@test.example";
        private String displayName = "Test User";
        private String passwordHash = DEFAULT_PASSWORD_HASH;
        private UserStatus status = UserStatus.PENDING_VERIFICATION;
        private Integer failedLoginAttempts = 0;
        private Boolean emailVerified = false;

        private Builder() {}

        /**
         * Sets the email address.
         *
         * @param email email address (will be normalized to lowercase on persist)
         * @return this builder
         */
        public Builder email(String email) {
            this.email = email;
            return this;
        }

        /** Sets the display name. */
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /**
         * Sets the raw password (will be hashed with BCrypt cost=4).
         *
         * <p>For test performance, use {@link #encodedPassword(String)} if you have a pre-computed
         * hash.
         */
        public Builder rawPassword(String rawPassword) {
            this.passwordHash = new BCryptPasswordEncoder(4).encode(rawPassword);
            return this;
        }

        /** Sets the pre-computed password hash. Use for performance when hash is reused. */
        public Builder encodedPassword(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        /**
         * Sets the user status.
         *
         * <p><b>Note:</b> User entity uses state machine pattern. This method uses
         * ReflectionTestUtils to bypass state transition validation for test data creation.
         */
        public Builder status(UserStatus status) {
            this.status = status;
            return this;
        }

        /** Sets the failed login attempt count. */
        public Builder failedLoginAttempts(Integer attempts) {
            this.failedLoginAttempts = attempts;
            return this;
        }

        /** Sets whether email is verified. */
        public Builder emailVerified(Boolean verified) {
            this.emailVerified = verified;
            return this;
        }

        /**
         * Builds the User entity.
         *
         * <p>Note: createdAt and updatedAt are managed by JPA (@CreationTimestamp/@UpdateTimestamp)
         * and will be null until persisted.
         *
         * <p><b>Implementation note:</b> Uses ReflectionTestUtils to set status field to bypass
         * state machine validation for test data creation.
         *
         * @return new User instance
         */
        public User build() {
            var user = new User();
            user.setEmail(email);
            user.setDisplayName(displayName);
            user.setPasswordHash(passwordHash);
            user.setFailedLoginAttempts(failedLoginAttempts);
            user.setEmailVerified(emailVerified);

            // Use ReflectionTestUtils to set status field (bypass state machine)
            ReflectionTestUtils.setField(user, "status", status);

            return user;
        }
    }

    // ==========================================
    // Utility Methods
    // ==========================================

    /**
     * Generates an 8-character short UUID for unique email addresses.
     *
     * <p>Ensures test isolation between concurrent test runs.
     */
    private static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
