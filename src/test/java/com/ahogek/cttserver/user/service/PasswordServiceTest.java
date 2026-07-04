package com.ahogek.cttserver.user.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditLogService auditLogService;

    private PasswordService service;

    @BeforeEach
    void setUp() {
        service = new PasswordService(userRepository, passwordEncoder, auditLogService);
    }

    private User createOAuthUser(UUID userId) {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
        user.setEmail("oauth@example.com");
        user.setDisplayName("OAuth User");
        return user;
    }

    private User createUserWithPassword(UUID userId) {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
        user.setEmail("user@example.com");
        user.setDisplayName("User With Password");
        user.setPasswordHash("existingEncodedPassword");
        return user;
    }

    @Nested
    @DisplayName("setPassword")
    class SetPassword {

        @Test
        @DisplayName("should set password when OAuth user")
        void shouldSetPassword_whenOAuthUser() {
            UUID userId = UUID.randomUUID();
            String newPassword = "newSecurePassword123";
            String encodedPassword = "encodedNewPassword";

            User user = createOAuthUser(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(newPassword)).thenReturn(encodedPassword);

            service.setPassword(userId, newPassword);

            assertThat(user.getPasswordHash()).isEqualTo(encodedPassword);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo(encodedPassword);

            verify(auditLogService)
                    .logSuccess(
                            userId, AuditAction.PASSWORD_SET, ResourceType.USER, userId.toString());
        }

        @Test
        @DisplayName("should throw ConflictException when password already set")
        void shouldThrowConflictException_whenPasswordAlreadySet() {
            UUID userId = UUID.randomUUID();
            String newPassword = "newSecurePassword123";

            User user = createUserWithPassword(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.setPassword(userId, newPassword))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_015);

            verify(passwordEncoder, never()).encode(any());
            verify(userRepository, never()).save(any());
            verify(auditLogService, never()).logSuccess(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw NotFoundException when user not found")
        void shouldThrowNotFoundException_whenUserNotFound() {
            UUID userId = UUID.randomUUID();
            String newPassword = "newSecurePassword123";

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.setPassword(userId, newPassword))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_004);

            verify(passwordEncoder, never()).encode(any());
            verify(userRepository, never()).save(any());
            verify(auditLogService, never()).logSuccess(any(), any(), any(), any());
        }
    }
}
