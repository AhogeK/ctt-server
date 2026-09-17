package com.ahogek.cttserver.user.entity;

import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.user.enums.UserStatus;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private User createUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");
        user.setPasswordHash("hashedPassword");
        return user;
    }

    @Test
    void newUserHasPendingVerificationStatus() {
        User user = new User();

        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(user.getEmailVerified()).isFalse();
    }

    @Test
    void verifyEmailTransitionsToActive() {
        User user = createUser();

        user.verifyEmail();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmailVerified()).isTrue();
    }

    @Test
    void verifyEmailFromActiveThrowsException() {
        User user = createUser();
        user.verifyEmail();

        assertThatThrownBy(user::verifyEmail)
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Invalid state transition");
    }

    @Test
    void verifyEmailFromDeletedThrowsException() {
        User user = createUser();
        ReflectionTestUtils.setField(user, "status", UserStatus.DELETED);

        assertThatThrownBy(user::verifyEmail)
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Invalid state transition");
    }

    @Test
    void suspendTransitionsToSuspended() {
        User user = createUser();
        user.verifyEmail();

        user.suspend();

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void suspendFromPendingVerification() {
        User user = createUser();

        user.suspend();

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void reactivateFromSuspended() {
        User user = createUser();
        user.verifyEmail();
        user.suspend();

        user.reactivate();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void cannotTransitionFromDeleted() {
        User user = createUser();
        ReflectionTestUtils.setField(user, "status", UserStatus.DELETED);

        assertThatThrownBy(user::reactivate)
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Invalid state transition");
    }
}
