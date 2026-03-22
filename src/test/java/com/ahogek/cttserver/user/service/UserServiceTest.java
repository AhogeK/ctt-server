package com.ahogek.cttserver.user.service;

import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.auth.repository.EmailVerificationTokenRepository;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.mail.service.MailOutboxService;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;
import com.ahogek.cttserver.user.validator.UserValidator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService application logic tests.
 *
 * <p>Validates orchestration logic for user registration following Developer Handbook patterns.
 * Service layer tests are pure unit tests with Mockito, fully isolated from Spring container.
 *
 * @see <a href="docs/developer-handbook.md#adding-public-exceptions">Developer Handbook - Adding
 *     Public Exceptions</a>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Application Logic Tests")
class UserServiceTest {

    @Mock private UserRepository userRepository;

    @Mock private UserValidator userValidator;

    @Mock private PasswordEncoder passwordEncoder;

    @Mock private AuditLogService auditLogService;

    @Mock private EmailVerificationTokenRepository tokenRepository;

    @Mock private MailOutboxService mailOutboxService;

    @InjectMocks private UserService userService;

    @Nested
    @DisplayName("registerUser() - User Registration Flow")
    class RegisterUserTests {

        @Test
        @DisplayName("Should register user successfully with encoded password")
        void shouldRegisterUserSuccessfully() {
            // Given
            String rawPassword = "Test@1234";
            String encodedPassword = "$2a$12$encodedPasswordHash";
            UUID userId = UUID.randomUUID();

            UserRegisterRequest request =
                    new UserRegisterRequest("test@example.com", "Test User", rawPassword);

            User savedUser = new User();
            savedUser.setEmail(request.email());
            savedUser.setDisplayName(request.displayName());
            savedUser.setPasswordHash(encodedPassword);
            org.springframework.test.util.ReflectionTestUtils.setField(savedUser, "id", userId);

            when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // When
            userService.registerUser(request);

            // Then
            verify(userValidator).assertEmailUnique(request.email());
            verify(passwordEncoder).encode(rawPassword);
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw ConflictException when email is not unique")
        void shouldThrowConflictException_whenEmailNotUnique() {
            // Given
            UserRegisterRequest request =
                    new UserRegisterRequest("duplicate@example.com", "Test User", "Test@1234");

            doThrow(new ConflictException(ErrorCode.AUTH_002, "Email is already registered"))
                    .when(userValidator)
                    .assertEmailUnique(request.email());

            // When & Then
            assertThatThrownBy(() -> userService.registerUser(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Email is already registered");

            verify(userRepository, never()).save(any());
            verify(auditLogService, never()).logSuccess(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should encode password before saving user")
        void shouldEncodePasswordBeforeSaving() {
            // Given
            String rawPassword = "SecureP@ss123";
            String encodedPassword = "$2a$12$someEncodedHash";
            UUID userId = UUID.randomUUID();

            UserRegisterRequest request =
                    new UserRegisterRequest("test@example.com", "Test User", rawPassword);

            when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
            when(userRepository.save(any(User.class)))
                    .thenAnswer(
                            invocation -> {
                                User user = invocation.getArgument(0);
                                assertThat(user.getPasswordHash()).isEqualTo(encodedPassword);
                                assertThat(user.getPasswordHash()).isNotEqualTo(rawPassword);
                                org.springframework.test.util.ReflectionTestUtils.setField(
                                        user, "id", userId);
                                return user;
                            });

            // When
            userService.registerUser(request);

            // Then - assertions are in the mock answer
        }

        @Test
        @DisplayName("Should set default status to PENDING_VERIFICATION")
        void shouldSetDefaultStatusToPendingVerification() {
            // Given
            UUID userId = UUID.randomUUID();
            UserRegisterRequest request =
                    new UserRegisterRequest("test@example.com", "Test User", "Test@1234");

            when(passwordEncoder.encode("Test@1234")).thenReturn("$2a$12$encoded");
            when(userRepository.save(any(User.class)))
                    .thenAnswer(
                            invocation -> {
                                User user = invocation.getArgument(0);
                                assertThat(user.getStatus())
                                        .isEqualTo(UserStatus.PENDING_VERIFICATION);
                                org.springframework.test.util.ReflectionTestUtils.setField(
                                        user, "id", userId);
                                return user;
                            });

            // When
            userService.registerUser(request);

            // Then - assertions are in the mock answer
        }
    }
}
