package com.ahogek.cttserver.sync;

import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * End-to-end tests for pull paging: a fresh device pulling a large change log must receive bounded
 * pages flagged with {@code hasMore} and converge on the full history by continuing with the
 * returned cursor.
 *
 * <p>Runs with {@code ctt.sync.pull-batch-size=5} so a handful of pushed sessions exercises
 * multiple pages without bulk fixtures.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-09-02
 */
@BaseIntegrationTest
@TestPropertySource(
        properties = {"ctt.sync.pull-batch-size=5", "ctt.mail.outbox.poll-interval-ms=999999999"})
@DisplayName("Sync Pull Paging Integration Tests")
class SyncPullPagingIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "SyncPagingUser";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    private static final String PULL_ENDPOINT = "/api/v1/sync/pull";
    private static final String PUSH_ENDPOINT = "/api/v1/sync/push";
    private static final int SESSION_COUNT = 12;

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM session_changes").update();
        jdbcClient.sql("DELETE FROM sync_cursors").update();
        jdbcClient.sql("DELETE FROM coding_sessions").update();
        jdbcClient.sql("DELETE FROM devices").update();
        jdbcClient.sql("DELETE FROM api_keys").update();
        jdbcClient.sql("DELETE FROM mail_outbox").update();
        jdbcClient.sql("DELETE FROM email_verification_tokens").update();
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    @Test
    @DisplayName("Should page the change log with hasMore flags until fully drained")
    void shouldPageChangeLogUntilDrained() throws Exception {
        String email = "sync-paging." + UUID.randomUUID() + "@test.example";
        String jwt = registerVerifyAndLogin(email);
        UUID userId =
                jdbcClient
                        .sql("SELECT id FROM users WHERE email = ?")
                        .param(email)
                        .query(UUID.class)
                        .single();
        UUID deviceId = insertDevice(userId);

        String pushResponse =
                mvc.post()
                        .uri(PUSH_ENDPOINT)
                        .with(csrf())
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pushBody(deviceId, sessionsJson(SESSION_COUNT)))
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        long pushCursor =
                objectMapper.readTree(pushResponse).path("data").path("nextCursor").asLong();
        assertThat(pushCursor).isGreaterThanOrEqualTo(SESSION_COUNT);

        List<Long> seenChangeIds = new ArrayList<>();
        long cursor = 0;
        int rounds = 0;
        boolean drained = false;
        while (!drained && rounds < 10) {
            JsonNode page = pull("Bearer " + jwt, deviceId, cursor);
            JsonNode changes = page.path("changes");
            boolean hasMore = page.path("hasMore").asBoolean();
            long nextCursor = page.path("nextCursor").asLong();

            if (changes.isArray() && !changes.isEmpty()) {
                assertThat(changes.size()).isLessThanOrEqualTo(5);
                for (JsonNode change : changes) {
                    seenChangeIds.add(change.path("changeId").asLong());
                }
            }
            assertThat(nextCursor).isGreaterThanOrEqualTo(cursor);
            cursor = nextCursor;
            drained = !hasMore;
            rounds++;
        }

        assertThat(drained).as("paging should terminate within the round cap").isTrue();
        assertThat(seenChangeIds).hasSize(SESSION_COUNT);
        assertThat(seenChangeIds).doesNotHaveDuplicates();
        assertThat(seenChangeIds).isSorted();
        assertThat(cursor).isEqualTo(pushCursor);
    }

    private String registerVerifyAndLogin(String email) throws Exception {
        String registerBody =
                """
                {"email": "%s", "displayName": "%s", "password": "%s", "termsVersion": "1.0.0"}
                """
                        .formatted(email, DISPLAY_NAME, PASSWORD);
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody))
                .hasStatus(200);

        String bodyHtml =
                jdbcClient
                        .sql(
                                "SELECT body_html FROM mail_outbox WHERE recipient = ? ORDER BY created_at DESC LIMIT 1")
                        .param(email)
                        .query(String.class)
                        .single();
        Matcher matcher = TOKEN_PATTERN.matcher(bodyHtml);
        assertThat(matcher.find()).isTrue();
        assertThat(mvc.get().uri("/api/v1/auth/verify-email?token=" + matcher.group(1)))
                .hasStatus(200);

        String loginBody =
                """
                {"email": "%s", "password": "%s", "deviceId": "paging-device"}
                """
                        .formatted(email, PASSWORD);
        String loginResponse =
                mvc.post()
                        .uri("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody)
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String accessToken =
                objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();

        String termsResponse =
                mvc.post()
                        .uri("/api/v1/auth/terms/accept")
                        .with(csrf())
                        .header("Authorization", "Bearer " + accessToken)
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String newAccessToken =
                objectMapper.readTree(termsResponse).path("data").path("accessToken").asText();
        assertThat(newAccessToken).isNotBlank();
        return newAccessToken;
    }

    private UUID insertDevice(UUID userId) {
        UUID deviceId = UUID.randomUUID();
        jdbcClient
                .sql(
                        """
                        INSERT INTO devices
                            (id, user_id, device_name, platform, ide_name, ide_version, app_version,
                             last_ip, created_at, last_seen_at, updated_at)
                        VALUES (?, ?, 'paging-device', 'macos', 'IntelliJ IDEA', '2026.1', '1.0.0',
                                '127.0.0.1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .param(deviceId)
                .param(userId)
                .update();
        return deviceId;
    }

    private String sessionsJson(int count) {
        List<String> sessions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            sessions.add(
                    """
                    {"sessionUuid": "%s", "projectName": "paging", "language": "Java",
                     "startTime": "2026-09-01T09:%02d:00Z", "endTime": "2026-09-01T10:%02d:00Z",
                     "clientModifiedAt": "2026-09-01T10:%02d:00Z", "clientVersion": 1, "deleted": false}
                    """
                            .formatted(UUID.randomUUID(), i % 60, i % 60, i % 60));
        }
        return String.join(", ", sessions);
    }

    private String pushBody(UUID deviceId, String sessionsJson) {
        return """
                {"deviceId": "%s", "sessions": [%s]}
                """
                .formatted(deviceId, sessionsJson);
    }

    private JsonNode pull(String authHeader, UUID deviceId, long cursor) throws Exception {
        String response =
                mvc.post()
                        .uri(PULL_ENDPOINT)
                        .with(csrf())
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"deviceId": "%s", "lastPulledChangeId": %d}
                                """
                                        .formatted(deviceId, cursor))
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(response).path("data");
    }
}
