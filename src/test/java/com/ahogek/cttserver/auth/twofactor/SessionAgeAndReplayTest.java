package com.ahogek.cttserver.auth.twofactor;

import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@BaseIntegrationTest
@DisplayName("Session age and TOTP replay guard")
class SessionAgeAndReplayTest {

    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcClient jdbcClient;

    private UUID userId;

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    @Test
    @DisplayName("Should report the first issuance of a session")
    void shouldReportSessionCreatedAt_whenSessionHasSeveralRows() {
        userId = createUser();
        UUID sessionId = UUID.randomUUID();
        Instant first = Instant.parse("2026-10-30T00:00:00Z");
        insertToken(sessionId, first);
        insertToken(sessionId, first.plusSeconds(60));

        assertThat(refreshTokenRepository.findSessionCreatedAt(userId, sessionId)).isEqualTo(first);
    }

    @Test
    @Transactional
    @DisplayName("Should accept each TOTP step once")
    void shouldRejectReplay_whenStepIsNotIncreasing() {
        userId = createUser();

        assertThat(userRepository.markTotpStepUsed(userId, 100L)).isEqualTo(1);
        assertThat(userRepository.markTotpStepUsed(userId, 100L)).isZero();
        assertThat(userRepository.markTotpStepUsed(userId, 99L)).isZero();
        assertThat(userRepository.markTotpStepUsed(userId, 101L)).isEqualTo(1);
    }

    private UUID createUser() {
        User user = new User();
        user.setEmail("session-age-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName("Session Age");
        return userRepository.save(user).getId();
    }

    private void insertToken(UUID sessionId, Instant createdAt) {
        jdbcClient
                .sql(
                        "INSERT INTO refresh_tokens"
                                + " (user_id, token_hash, issued_for, session_id, expires_at,"
                                + " created_at) VALUES (?, ?, 'WEB', ?, ?, ?)")
                .params(
                        userId,
                        UUID.randomUUID().toString().replace("-", "")
                                + UUID.randomUUID().toString().replace("-", ""),
                        sessionId,
                        Timestamp.from(createdAt.plusSeconds(3600)),
                        Timestamp.from(createdAt))
                .update();
    }
}
