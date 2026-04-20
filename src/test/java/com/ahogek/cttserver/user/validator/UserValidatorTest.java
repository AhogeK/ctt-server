package com.ahogek.cttserver.user.validator;

import com.ahogek.cttserver.common.BaseRepositoryTest;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.fixtures.UserFixtures;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserValidator domain rules tests.
 *
 * <p>Validates business logic for:
 *
 * <ul>
 *   <li>Email uniqueness checks
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
        userValidator = new UserValidator(userRepository);
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
                    .hasMessageContaining("Email already verified");
        }

        @Test
        @DisplayName("Should throw ConflictException when user is LOCKED")
        void shouldThrowConflictException_whenUserIsLocked() {
            User user = UserFixtures.lockedUser().build();

            assertThatThrownBy(() -> userValidator.assertCanVerifyEmail(user))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Email already verified");
        }

        @Test
        @DisplayName("Should throw ConflictException when user is SUSPENDED")
        void shouldThrowConflictException_whenUserIsSuspended() {
            User user = UserFixtures.suspendedUser().build();

            assertThatThrownBy(() -> userValidator.assertCanVerifyEmail(user))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Email already verified");
        }

        @Test
        @DisplayName("Should throw ConflictException when user is DELETED")
        void shouldThrowConflictException_whenUserIsDeleted() {
            User user = UserFixtures.deletedUser().build();

            assertThatThrownBy(() -> userValidator.assertCanVerifyEmail(user))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Email already verified");
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
