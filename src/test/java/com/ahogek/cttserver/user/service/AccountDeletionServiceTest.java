package com.ahogek.cttserver.user.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AccountDeletionService unit tests.
 *
 * <p>Deletion is a row delete: everything that hangs off the user goes with it through the schema's
 * cascade foreign keys, which is why this service has so little to do. What these tests pin is the
 * part the cascade cannot express — the proof that the caller knows the password — and that the
 * audit event is emitted in a shape the foreign key still accepts after the row is gone.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountDeletionService")
class AccountDeletionServiceTest {

    private static final UUID USER_ID = UUID.fromString("f1905584-27dd-4cc9-a3bf-0e63950212c0");
    private static final String PASSWORD = "StrongPass123!";
    private static final String PASSWORD_HASH = "$2a$10$hashed";

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private AccountDeletionService service;

    /**
     * An active account. Status comes from the entity's own transition, because it exposes no
     * setter.
     *
     * @param passwordHash the stored hash, or {@code null} for an OAuth-only account
     * @return the account
     */
    private User activeUser(String passwordHash) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("coder@example.com");
        user.setDisplayName("Coder");
        user.setPasswordHash(passwordHash);
        user.verifyEmail();
        return user;
    }

    @Nested
    @DisplayName("deleteAccount")
    class DeleteAccountTests {

        @Test
        @DisplayName("should delete the account and audit it without a dangling user reference")
        void shouldDeleteAccount_whenPasswordMatches() {
            User user = activeUser(PASSWORD_HASH);
            BDDMockito.given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            BDDMockito.given(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).willReturn(true);

            service.deleteAccount(USER_ID, PASSWORD);

            verify(userRepository).delete(user);
            // The listener writes after commit, so the row is already gone; the event keeps the id
            // as its resource because the user reference cannot survive it.
            verify(auditLogService)
                    .logSuccess(
                            isNull(),
                            org.mockito.ArgumentMatchers.eq(AuditAction.ACCOUNT_DELETED),
                            org.mockito.ArgumentMatchers.eq(ResourceType.USER),
                            org.mockito.ArgumentMatchers.eq(USER_ID.toString()));
        }

        @Test
        @DisplayName("should delete an account that has no password to prove")
        void shouldDeleteAccount_whenAccountHasNoPassword() {
            User user = activeUser(null);
            BDDMockito.given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

            service.deleteAccount(USER_ID, null);

            verify(userRepository).delete(user);
            verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        @DisplayName("should refuse a wrong password without deleting anything")
        void shouldRefuse_whenPasswordIsWrong() {
            User user = activeUser(PASSWORD_HASH);
            BDDMockito.given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            BDDMockito.given(passwordEncoder.matches("wrong", PASSWORD_HASH)).willReturn(false);

            assertThatThrownBy(() -> service.deleteAccount(USER_ID, "wrong"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Invalid password");

            verify(userRepository, never()).delete(any());
            verify(auditLogService, never()).logSuccess(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should ask for a password when the account has one and none was given")
        void shouldRequirePassword_whenAccountHasOne() {
            User user = activeUser(PASSWORD_HASH);
            BDDMockito.given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> service.deleteAccount(USER_ID, "   "))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Password verification required");

            verify(userRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should report an account that is already gone")
        void shouldThrowNotFound_whenAccountIsGone() {
            BDDMockito.given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteAccount(USER_ID, PASSWORD))
                    .isInstanceOf(NotFoundException.class);

            verify(userRepository, never()).delete(any());
        }
    }
}
