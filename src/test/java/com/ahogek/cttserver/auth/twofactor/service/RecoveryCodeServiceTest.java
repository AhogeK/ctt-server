package com.ahogek.cttserver.auth.twofactor.service;

import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@BaseIntegrationTest
@DisplayName("Recovery code service")
class RecoveryCodeServiceTest {

    @Autowired private RecoveryCodeService service;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcClient jdbcClient;

    private UUID userId;

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM user_recovery_codes").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    @Test
    @DisplayName("Should issue ten codes and store only hashes")
    void shouldIssueTenCodes_whenRequested() {
        userId = createUser();

        List<String> codes = service.issue(userId);

        assertThat(codes)
                .hasSize(10)
                .allMatch(code -> code.matches("[A-Z2-7]{4}(-[A-Z2-7]{4}){3}"));
        assertThat(service.countRemaining(userId)).isEqualTo(10);
        String firstRaw = codes.getFirst().replace("-", "");
        List<String> hashes =
                jdbcClient
                        .sql("SELECT code_hash FROM user_recovery_codes WHERE user_id = ?")
                        .param(userId)
                        .query(String.class)
                        .list();
        assertThat(hashes).noneMatch(firstRaw::equals);
    }

    @Test
    @DisplayName("Should consume a code exactly once")
    void shouldConsumeOnce_whenCodeIsUsed() {
        userId = createUser();
        String code = service.issue(userId).getFirst();

        assertThat(service.consume(userId, code)).isTrue();
        assertThat(service.consume(userId, code)).isFalse();
        assertThat(service.countRemaining(userId)).isEqualTo(9);
    }

    @Test
    @DisplayName("Should invalidate the previous batch when codes are regenerated")
    void shouldInvalidateOldBatch_whenRegenerating() {
        userId = createUser();
        String oldCode = service.issue(userId).getFirst();

        service.issue(userId);

        assertThat(service.consume(userId, oldCode)).isFalse();
        assertThat(service.countRemaining(userId)).isEqualTo(10);
    }

    private UUID createUser() {
        User user = new User();
        user.setEmail("recovery-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName("Recovery Test");
        return userRepository.save(user).getId();
    }
}
