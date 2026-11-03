package com.ahogek.cttserver.auth.passkey.service;

import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.UnauthorizedException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import com.yubico.webauthn.data.ByteArray;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@BaseIntegrationTest
@DisplayName("Passkey challenge service")
class PasskeyChallengeServiceTest {

    @Autowired private PasskeyChallengeService service;

    @Test
    @DisplayName("Should consume a passkey challenge exactly once")
    void shouldConsumeOnce_whenChallengeIsUsed() {
        ByteArray challenge = new ByteArray(new byte[32]);
        UUID userId = UUID.randomUUID();

        long expiresIn =
                service.store(
                        PasskeyChallengeService.Operation.REGISTRATION,
                        userId,
                        challenge,
                        "{\"publicKey\":{}}");

        assertThat(expiresIn).isPositive();
        assertThat(
                        service.consume(
                                        PasskeyChallengeService.Operation.REGISTRATION,
                                        userId,
                                        null,
                                        challenge)
                                .requestJson())
                .isEqualTo("{\"publicKey\":{}}");
        assertThatThrownBy(
                        () ->
                                service.consume(
                                        PasskeyChallengeService.Operation.REGISTRATION,
                                        userId,
                                        null,
                                        challenge))
                .isInstanceOf(UnauthorizedException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_032);
    }

    @Test
    @DisplayName("Should reject a registration challenge bound to another account")
    void shouldRejectChallenge_whenAccountDoesNotMatch() {
        ByteArray challenge = new ByteArray(new byte[32]);
        service.store(
                PasskeyChallengeService.Operation.REGISTRATION,
                UUID.randomUUID(),
                challenge,
                "{\"publicKey\":{}}");

        assertThatThrownBy(
                        () ->
                                service.consume(
                                        PasskeyChallengeService.Operation.REGISTRATION,
                                        UUID.randomUUID(),
                                        null,
                                        challenge))
                .isInstanceOf(UnauthorizedException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_032);
    }

    @Test
    @DisplayName("Should reject a challenge used for another ceremony")
    void shouldRejectChallenge_whenOperationDoesNotMatch() {
        ByteArray challenge = new ByteArray(new byte[32]);
        UUID userId = UUID.randomUUID();
        service.store(PasskeyChallengeService.Operation.REGISTRATION, userId, challenge, "{}");

        assertThatThrownBy(
                        () ->
                                service.consume(
                                        PasskeyChallengeService.Operation.ASSERTION,
                                        null,
                                        null,
                                        challenge))
                .isInstanceOf(UnauthorizedException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_032);
    }

    @Test
    @DisplayName("Should reject a challenge bound to another login token")
    void shouldRejectChallenge_whenBindingDoesNotMatch() {
        ByteArray challenge = new ByteArray(new byte[32]);
        UUID userId = UUID.randomUUID();
        service.store(
                PasskeyChallengeService.Operation.SECOND_FACTOR,
                userId,
                challenge,
                "{}",
                "expected-binding-hash");

        assertThatThrownBy(
                        () ->
                                service.consume(
                                        PasskeyChallengeService.Operation.SECOND_FACTOR,
                                        userId,
                                        "different-binding-hash",
                                        challenge))
                .isInstanceOf(UnauthorizedException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_032);
    }
}
