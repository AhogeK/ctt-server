package com.ahogek.cttserver.user.service;

import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.user.dto.UserProfileResponse;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserProfileService}.
 *
 * <p>Validates read-only profile retrieval: happy path, boundary conditions
 * (null emailVerifiedAt, null lastLoginAt), and the defensive NotFoundException
 * thrown when the user no longer exists.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-01
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String TEST_EMAIL = "user@example.com";
    private static final String TEST_DISPLAY_NAME = "John Doe";
    private static final String TEST_TERMS_VERSION = "1.0.0";
    private static final Instant TEST_INSTANT = Instant.parse("2026-01-15T10:30:00Z");

    @Mock private UserRepository userRepository;

    @InjectMocks private UserProfileService userProfileService;

    private User createActiveUser() {
        return createUserWithEmailVerified(TEST_INSTANT);
    }

    private User createUserWithEmailVerified(Instant verifiedAt) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.setEmail(TEST_EMAIL);
        user.setDisplayName(TEST_DISPLAY_NAME);
        ReflectionTestUtils.setField(user, "emailVerifiedAt", verifiedAt);
        ReflectionTestUtils.setField(user, "lastLoginAt", TEST_INSTANT);
        user.setTermsVersion(TEST_TERMS_VERSION);
        return user;
    }

    @Nested
    @DisplayName("getCurrentUserProfile")
    class GetCurrentUserProfile {

        @Test
        @DisplayName("should return complete profile when user exists")
        void shouldReturnProfile_whenUserExists() {
            User user = createActiveUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            UserProfileResponse result = userProfileService.getCurrentUserProfile(USER_ID);

            assertThat(result.id()).isEqualTo(USER_ID);
            assertThat(result.email()).isEqualTo(TEST_EMAIL);
            assertThat(result.displayName()).isEqualTo(TEST_DISPLAY_NAME);
            assertThat(result.emailVerified()).isTrue();
            assertThat(result.createdAt()).isEqualTo(user.getCreatedAt());
            assertThat(result.lastLoginAt()).isEqualTo(user.getLastLoginAt());
            assertThat(result.termsVersion()).isEqualTo(TEST_TERMS_VERSION);
        }

        @Test
        @DisplayName("should return emailVerified false when emailVerifiedAt is null")
        void shouldReturnFalseEmailVerified_whenEmailVerifiedAtIsNull() {
            User user = createUserWithEmailVerified(null);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            UserProfileResponse result = userProfileService.getCurrentUserProfile(USER_ID);

            assertThat(result.emailVerified()).isFalse();
        }

        @Test
        @DisplayName("should return null lastLoginAt when user never logged in")
        void shouldReturnNullLastLoginAt_whenNeverLoggedIn() {
            User user = createActiveUser();
            ReflectionTestUtils.setField(user, "lastLoginAt", null);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            UserProfileResponse result = userProfileService.getCurrentUserProfile(USER_ID);

            assertThat(result.lastLoginAt()).isNull();
        }

        @Test
        @DisplayName("should throw NotFoundException when user does not exist")
        void shouldThrowNotFound_whenUserDoesNotExist() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userProfileService.getCurrentUserProfile(USER_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_004);
        }
    }
}
