package com.ahogek.cttserver.auth.twofactor.service;

import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.common.exception.TooManyRequestsException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@BaseIntegrationTest
@DisplayName("Two-factor challenge service")
class TwoFactorChallengeServiceTest {

    @Autowired private TwoFactorChallengeService service;

    @Test
    @DisplayName("Should consume a challenge exactly once")
    void shouldConsumeOnce_whenChallengeIsUsed() {
        UUID userId = UUID.randomUUID();
        TwoFactorChallengeService.ChallengeTicket ticket = service.create(userId, "device-1");

        TwoFactorChallengeService.ChallengePayload payload = service.consume(ticket.token());

        assertThat(payload.userId()).isEqualTo(userId);
        assertThat(payload.deviceId()).isEqualTo("device-1");
        assertThat(payload.attempts()).isZero();
        assertThatThrownBy(() -> service.consume(ticket.token()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("Should reject an unknown challenge")
    void shouldRejectUnknownChallenge_whenTokenDoesNotExist() {
        assertThatThrownBy(() -> service.consume("unknown-token"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("Should keep the challenge for retries and block the fifth failure")
    void shouldKeepChallengeForRetries_whenFailuresAreBelowLimit() {
        UUID userId = UUID.randomUUID();
        String token = service.create(userId, null).token();

        for (int attempt = 1; attempt <= 4; attempt++) {
            TwoFactorChallengeService.ChallengePayload payload = service.consume(token);
            service.recordFailure(token, payload);
        }

        TwoFactorChallengeService.ChallengePayload last = service.consume(token);
        assertThat(last.attempts()).isEqualTo(4);
        assertThatThrownBy(() -> service.recordFailure(token, last))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatThrownBy(() -> service.consume(token)).isInstanceOf(UnauthorizedException.class);
    }
}
