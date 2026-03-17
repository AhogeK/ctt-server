package com.ahogek.cttserver.user.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class UserStatusTest {

    @ParameterizedTest
    @CsvSource({
        "PENDING_VERIFICATION, ACTIVE, true",
        "PENDING_VERIFICATION, SUSPENDED, true",
        "PENDING_VERIFICATION, DELETED, true",
        "PENDING_VERIFICATION, LOCKED, false",
        "PENDING_VERIFICATION, PENDING_VERIFICATION, false"
    })
    void pendingVerificationTransitions(
            UserStatus from, UserStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "ACTIVE, LOCKED, true",
        "ACTIVE, SUSPENDED, true",
        "ACTIVE, DELETED, true",
        "ACTIVE, ACTIVE, false",
        "ACTIVE, PENDING_VERIFICATION, false"
    })
    void activeTransitions(UserStatus from, UserStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "LOCKED, ACTIVE, true",
        "LOCKED, SUSPENDED, true",
        "LOCKED, DELETED, true",
        "LOCKED, LOCKED, false",
        "LOCKED, PENDING_VERIFICATION, false"
    })
    void lockedTransitions(UserStatus from, UserStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "SUSPENDED, ACTIVE, true",
        "SUSPENDED, DELETED, true",
        "SUSPENDED, SUSPENDED, false",
        "SUSPENDED, LOCKED, false",
        "SUSPENDED, PENDING_VERIFICATION, false"
    })
    void suspendedTransitions(UserStatus from, UserStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @Test
    void deletedIsTerminalState() {
        assertThat(UserStatus.DELETED.canTransitionTo(UserStatus.ACTIVE)).isFalse();
        assertThat(UserStatus.DELETED.canTransitionTo(UserStatus.LOCKED)).isFalse();
        assertThat(UserStatus.DELETED.canTransitionTo(UserStatus.SUSPENDED)).isFalse();
        assertThat(UserStatus.DELETED.canTransitionTo(UserStatus.PENDING_VERIFICATION)).isFalse();
        assertThat(UserStatus.DELETED.canTransitionTo(UserStatus.DELETED)).isFalse();
    }

    @Test
    void allStatesHaveDefinedTransitions() {
        for (UserStatus status : UserStatus.values()) {
            assertThat(status).isNotNull();
            // Verify no null pointer exceptions when checking transitions
            for (UserStatus target : UserStatus.values()) {
                assertThat(status.canTransitionTo(target)).isNotNull();
            }
        }
    }
}
