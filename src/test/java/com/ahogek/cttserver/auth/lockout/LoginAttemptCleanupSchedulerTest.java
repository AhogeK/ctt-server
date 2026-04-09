package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.repository.LoginAttemptRepository;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.config.properties.SecurityProperties.PasswordProperties;
import com.ahogek.cttserver.fixtures.UserFixtures;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoginAttemptCleanupSchedulerTest {

    @Mock private LoginAttemptRepository loginAttemptRepository;

    @Mock private UserRepository userRepository;

    @Mock private AuditLogService auditLogService;

    @Mock private PasswordProperties passwordProps;

    @Mock private SecurityProperties securityProperties;

    private LoginAttemptCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        given(securityProperties.password()).willReturn(passwordProps);
        given(passwordProps.retentionDuration()).willReturn(Duration.ofHours(720));
        given(passwordProps.failureWindowSeconds()).willReturn(900);
        scheduler =
                new LoginAttemptCleanupScheduler(
                        loginAttemptRepository,
                        userRepository,
                        auditLogService,
                        securityProperties);
    }

    @Nested
    @DisplayName("cleanupExpiredAttempts")
    class CleanupExpiredAttempts {

        @Test
        @DisplayName("should delete records older than retention duration")
        void shouldDeleteRecordsOlderThanRetention() {
            // Given
            given(loginAttemptRepository.deleteOlderThan(any())).willReturn(5);

            // When
            scheduler.cleanupExpiredAttempts();

            // Then
            verify(loginAttemptRepository).deleteOlderThan(any());
        }

        @Test
        @DisplayName("should not log when no records deleted")
        void shouldNotLogWhenNoRecordsDeleted() {
            // Given
            given(loginAttemptRepository.deleteOlderThan(any())).willReturn(0);

            // When
            scheduler.cleanupExpiredAttempts();

            // Then
            verify(loginAttemptRepository).deleteOlderThan(any());
        }
    }

    @Nested
    @DisplayName("unlockExpiredAccounts")
    class UnlockExpiredAccounts {

        @Test
        @DisplayName("should do nothing when no locked users exist")
        void shouldDoNothing_whenNoLockedUsers() {
            // Given
            given(userRepository.findAllByStatus(UserStatus.LOCKED)).willReturn(List.of());

            // When
            scheduler.unlockExpiredAccounts();

            // Then
            verify(loginAttemptRepository, never()).countAttemptsInWindow(anyString(), any());
        }

        @Test
        @DisplayName("should unlock accounts when no recent attempts exist")
        void shouldUnlockAccounts_whenNoRecentAttempts() {
            // Given
            User lockedUser = UserFixtures.lockedUser().email("locked@test.example").build();
            ReflectionTestUtils.setField(lockedUser, "id", UUID.randomUUID());
            given(userRepository.findAllByStatus(UserStatus.LOCKED))
                    .willReturn(List.of(lockedUser));
            given(loginAttemptRepository.countAttemptsInWindow(anyString(), any(Instant.class)))
                    .willReturn(0L);

            // When
            scheduler.unlockExpiredAccounts();

            // Then
            assertThat(lockedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
            verify(userRepository).save(lockedUser);
            verify(auditLogService)
                    .logSuccess(
                            lockedUser.getId(),
                            AuditAction.ACCOUNT_UNLOCKED,
                            ResourceType.USER,
                            lockedUser.getId().toString());
        }

        @Test
        @DisplayName("should not unlock accounts when recent attempts exist")
        void shouldNotUnlockAccounts_whenRecentAttemptsExist() {
            // Given
            User lockedUser = UserFixtures.lockedUser().email("locked@test.example").build();
            given(userRepository.findAllByStatus(UserStatus.LOCKED))
                    .willReturn(List.of(lockedUser));
            given(loginAttemptRepository.countAttemptsInWindow(anyString(), any(Instant.class)))
                    .willReturn(3L);

            // When
            scheduler.unlockExpiredAccounts();

            // Then
            assertThat(lockedUser.getStatus()).isEqualTo(UserStatus.LOCKED);
            verify(userRepository, never()).save(lockedUser);
        }

        @Test
        @DisplayName("should unlock multiple accounts when all have no recent attempts")
        void shouldUnlockMultipleAccounts_whenNoRecentAttempts() {
            // Given
            User user1 = UserFixtures.lockedUser().email("user1@test.example").build();
            ReflectionTestUtils.setField(user1, "id", UUID.randomUUID());
            User user2 = UserFixtures.lockedUser().email("user2@test.example").build();
            ReflectionTestUtils.setField(user2, "id", UUID.randomUUID());
            given(userRepository.findAllByStatus(UserStatus.LOCKED))
                    .willReturn(List.of(user1, user2));
            given(loginAttemptRepository.countAttemptsInWindow(anyString(), any(Instant.class)))
                    .willReturn(0L);

            // When
            scheduler.unlockExpiredAccounts();

            // Then
            assertThat(user1.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user2.getStatus()).isEqualTo(UserStatus.ACTIVE);
            verify(userRepository).save(user1);
            verify(userRepository).save(user2);
            verify(auditLogService)
                    .logSuccess(
                            user1.getId(),
                            AuditAction.ACCOUNT_UNLOCKED,
                            ResourceType.USER,
                            user1.getId().toString());
            verify(auditLogService)
                    .logSuccess(
                            user2.getId(),
                            AuditAction.ACCOUNT_UNLOCKED,
                            ResourceType.USER,
                            user2.getId().toString());
        }

        @Test
        @DisplayName("should selectively unlock only accounts without recent attempts")
        void shouldSelectivelyUnlock_whenMixedAttempts() {
            // Given
            User cleanUser = UserFixtures.lockedUser().email("clean@test.example").build();
            ReflectionTestUtils.setField(cleanUser, "id", UUID.randomUUID());
            User activeUser = UserFixtures.lockedUser().email("active@test.example").build();
            ReflectionTestUtils.setField(activeUser, "id", UUID.randomUUID());
            given(userRepository.findAllByStatus(UserStatus.LOCKED))
                    .willReturn(List.of(cleanUser, activeUser));
            given(loginAttemptRepository.countAttemptsInWindow(anyString(), any(Instant.class)))
                    .willReturn(0L)
                    .willReturn(5L);

            // When
            scheduler.unlockExpiredAccounts();

            // Then
            assertThat(cleanUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(activeUser.getStatus()).isEqualTo(UserStatus.LOCKED);
            verify(userRepository).save(cleanUser);
            verify(userRepository, never()).save(activeUser);
            verify(auditLogService)
                    .logSuccess(
                            cleanUser.getId(),
                            AuditAction.ACCOUNT_UNLOCKED,
                            ResourceType.USER,
                            cleanUser.getId().toString());
            verify(auditLogService, never())
                    .logSuccess(
                            eq(activeUser.getId()),
                            eq(AuditAction.ACCOUNT_UNLOCKED),
                            eq(ResourceType.USER),
                            anyString());
        }
    }
}
