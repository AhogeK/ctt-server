package com.ahogek.cttserver.user.validator;

import com.ahogek.cttserver.common.BaseRepositoryTest;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.fixtures.UserFixtures;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserValidator domain rules tests.
 *
 * <p>Validates business logic for:
 *
 * <ul>
 *   <li>Email uniqueness checks
 *   <li>Login attempt limits
 *   <li>Email verification state transitions
 *   <li>User existence checks
 * </ul>
 */
@BaseRepositoryTest
@DisplayName("UserValidator Domain Rules Tests")
class UserValidatorTest {

    @Autowired private TestEntityManager em;

    @Autowired private UserRepository userRepository;

    private UserValidator userValidator;

    @BeforeEach
    void setUp() {
        SecurityProperties.PasswordProperties passwordProps =
                new SecurityProperties.PasswordProperties(12, 5, Duration.ofMinutes(30), 900, "DB");
        SecurityProperties securityProperties = new SecurityProperties(
                new SecurityProperties.JwtProperties(
                        "test-secret-key-for-testing-purposes-only",
                        "test-issuer",
                        Duration.ofMinutes(15),
                        Duration.ofDays(14),
                        Duration.ofDays(30)),
                passwordProps,
                new SecurityProperties.RateLimitProperties(true, 200),
                new SecurityProperties.AuditProperties(true, null));
        userValidator = new UserValidator(userRepository, securityProperties);
    }

    @Nested
    @DisplayName("assertEmailUnique() - Email Uniqueness Check")
    class AssertEmailUniqueTests {

        @Test
        @DisplayName("Should pass when email is unique")
        void shouldPass_whenEmailIsUnique() {
            String uniqueEmail = "unique_" + System.currentTimeMillis() + "@test.example";

            userValidator.assertEmailUnique(uniqueEmail);
        }

        @Test
        @DisplayName("Should throw ConflictException when email already exists")
        void shouldThrowConflictException_whenEmailExists() {
            User existingUser = UserFixtures.regularUser().email("duplicate@test.example").build();
            em.persistAndFlush(existingUser);

            assertThatThrownBy(() -> userValidator.assertEmailUnique("duplicate@test.example"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Email is already registered");
        }

        @Test
        @DisplayName("Should throw ConflictException when email exists with different case")
        void shouldThrowConflictException_whenEmailExistsWithDifferentCase() {
            User existingUser =
                    UserFixtures.regularUser().email("CaseSensitive@Test.Example").build();
            em.persistAndFlush(existingUser);

            assertThatThrownBy(() -> userValidator.assertEmailUnique("CASESENSITIVE@TEST.EXAMPLE"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Email is already registered");
        }
    }

    @Nested
    @DisplayName("assertLoginAttemptsNotExceeded() - Login Attempt Limit Check")
    class AssertLoginAttemptsNotExceededTests {

        @Test
        @DisplayName("Should pass when user has no failed attempts")
        void shouldPass_whenNoFailedAttempts() {
            User user = UserFixtures.regularUser().failedLoginAttempts(0).build();

            userValidator.assertLoginAttemptsNotExceeded(user);
        }

        @Test
        @DisplayName("Should pass when failed attempts below threshold (4 attempts)")
        void shouldPass_whenFailedAttemptsBelowThreshold() {
            User user = UserFixtures.regularUser().failedLoginAttempts(4).build();

            userValidator.assertLoginAttemptsNotExceeded(user);
        }

        @Test
        @DisplayName(
                "Should throw UnauthorizedException when attempts equal threshold (5 attempts)")
        void shouldThrowUnauthorizedException_whenAttemptsEqualsThreshold() {
            User user = UserFixtures.regularUser().failedLoginAttempts(5).build();

            assertThatThrownBy(() -> userValidator.assertLoginAttemptsNotExceeded(user))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("too many failed attempts");
        }

        @Test
        @DisplayName(
                "Should throw UnauthorizedException when attempts exceed threshold (6 attempts)")
        void shouldThrowUnauthorizedException_whenAttemptsExceedThreshold() {
            User user = UserFixtures.regularUser().failedLoginAttempts(6).build();

            assertThatThrownBy(() -> userValidator.assertLoginAttemptsNotExceeded(user))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("too many failed attempts");
        }

        @Test
        @DisplayName("Should pass when failedLoginAttempts is null")
        void shouldPass_whenFailedAttemptsIsNull() {
            User user = UserFixtures.regularUser().build();
            user.setFailedLoginAttempts(null);

            userValidator.assertLoginAttemptsNotExceeded(user);
        }
    }

    @Nested
    @DisplayName("assertCanVerifyEmail() - Email Verification State Check")
    class AssertCanVerifyEmailTests {

        @Test
        @DisplayName("Should pass when user is in PENDING_VERIFICATION status")
        void shouldPass_whenUserIsPendingVerification() {
            User user = UserFixtures.pendingUser().build();

            userValidator.assertCanVerifyEmail(user);
        }

        @Test
        @DisplayName("Should throw ConflictException when user is ACTIVE")
        void shouldThrowConflictException_whenUserIsActive() {
            User user = UserFixtures.regularUser().build();

            assertThatThrownBy(() -> userValidator.assertCanVerifyEmail(user))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("not in pending verification state");
        }

        @Test
        @DisplayName("Should throw ConflictException when user is LOCKED")
        void shouldThrowConflictException_whenUserIsLocked() {
            User user = UserFixtures.lockedUser().build();

            assertThatThrownBy(() -> userValidator.assertCanVerifyEmail(user))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("not in pending verification state");
        }

        @Test
        @DisplayName("Should throw ConflictException when user is SUSPENDED")
        void shouldThrowConflictException_whenUserIsSuspended() {
            User user = UserFixtures.suspendedUser().build();

            assertThatThrownBy(() -> userValidator.assertCanVerifyEmail(user))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("not in pending verification state");
        }

        @Test
        @DisplayName("Should throw ConflictException when user is DELETED")
        void shouldThrowConflictException_whenUserIsDeleted() {
            User user = UserFixtures.deletedUser().build();

            assertThatThrownBy(() -> userValidator.assertCanVerifyEmail(user))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("not in pending verification state");
        }
    }

    @Nested
    @DisplayName("assertUserExists() - User Existence Check")
    class AssertUserExistsTests {

        @Test
        @DisplayName("Should pass when user exists")
        void shouldPass_whenUserExists() {
            User existingUser = UserFixtures.regularUser().email("exists@test.example").build();
            em.persistAndFlush(existingUser);

            userValidator.assertUserExists("exists@test.example");
        }

        @Test
        @DisplayName("Should pass when user exists with different case")
        void shouldPass_whenUserExistsWithDifferentCase() {
            User existingUser = UserFixtures.regularUser().email("MixedCase@Test.Example").build();
            em.persistAndFlush(existingUser);

            userValidator.assertUserExists("MIXEDCASE@TEST.EXAMPLE");
        }

        @Test
        @DisplayName("Should throw NotFoundException when user does not exist")
        void shouldThrowNotFoundException_whenUserDoesNotExist() {
            String nonExistentEmail = "nonexistent_" + System.currentTimeMillis() + "@test.example";

            assertThatThrownBy(() -> userValidator.assertUserExists(nonExistentEmail))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User not found");
        }
    }
}
