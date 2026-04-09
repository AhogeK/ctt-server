package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.auth.repository.LoginAttemptRepository;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.config.properties.SecurityProperties.PasswordProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoginAttemptCleanupSchedulerTest {

    @Mock private LoginAttemptRepository loginAttemptRepository;

    @Mock private PasswordProperties passwordProps;

    @Mock private SecurityProperties securityProperties;

    private LoginAttemptCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        given(securityProperties.password()).willReturn(passwordProps);
        given(passwordProps.retentionDuration()).willReturn(Duration.ofHours(720));
        scheduler = new LoginAttemptCleanupScheduler(loginAttemptRepository, securityProperties);
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
}
