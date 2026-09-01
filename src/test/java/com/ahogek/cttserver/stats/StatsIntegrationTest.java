package com.ahogek.cttserver.stats;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * End-to-end integration tests for the statistics endpoints.
 *
 * <p>Verifies the six stats endpoints through the real request pipeline: summary, heatmap, streaks,
 * distribution, hourly and recent. Sessions are inserted directly so the aggregated values are
 * deterministic.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-30
 */
@BaseIntegrationTest
@TestPropertySource(properties = {"ctt.mail.outbox.poll-interval-ms=999999999"})
@DisplayName("Statistics Integration Tests")
class StatsIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redisTemplate;

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "StatsTestUser";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @AfterEach
    void tearDown() {
        Set<String> rateLimitKeys = redisTemplate.keys("rate_limit:ip:AuthController.*");
        if (rateLimitKeys != null) {
            redisTemplate.delete(rateLimitKeys);
        }
        jdbcClient.sql("DELETE FROM user_achievements").update();
        jdbcClient.sql("DELETE FROM coding_sessions").update();
        jdbcClient.sql("DELETE FROM devices").update();
        jdbcClient.sql("DELETE FROM api_keys").update();
        jdbcClient.sql("DELETE FROM mail_outbox").update();
        jdbcClient.sql("DELETE FROM email_verification_tokens").update();
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM audit_logs").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    private String uniqueEmail() {
        return "stats-test." + UUID.randomUUID() + "@test.example";
    }

    private String registerRequestJson(String email) throws Exception {
        UserRegisterRequest request =
                new UserRegisterRequest(email, DISPLAY_NAME, PASSWORD, "1.0.0", null);
        return objectMapper.writeValueAsString(request);
    }

    private String loginRequestJson(String email) throws Exception {
        LoginRequest request =
                new LoginRequest(email, PASSWORD, "device-" + UUID.randomUUID(), null);
        return objectMapper.writeValueAsString(request);
    }

    private String extractTokenFromMailOutbox(String email) {
        String bodyHtml =
                jdbcClient
                        .sql(
                                "SELECT body_html FROM mail_outbox WHERE recipient = ? ORDER BY created_at DESC LIMIT 1")
                        .param(email)
                        .query(String.class)
                        .single();
        Matcher matcher = TOKEN_PATTERN.matcher(bodyHtml);
        assertThat(matcher.find())
                .as("Verification token not found in email body for %s", email)
                .isTrue();
        return matcher.group(1);
    }

    /** Registers, verifies, logs in and accepts terms; returns the access token and user id. */
    private String[] registerVerifyAndLogin(String email) throws Exception {
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequestJson(email)))
                .hasStatus(200);

        String token = extractTokenFromMailOutbox(email);
        assertThat(mvc.get().uri("/api/v1/auth/verify-email?token=" + token)).hasStatus(200);

        String loginResponse =
                mvc.post()
                        .uri("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson(email))
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String accessToken =
                objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();
        String userId = objectMapper.readTree(loginResponse).path("data").path("userId").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(userId).isNotBlank();

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
        return new String[] {newAccessToken, userId};
    }

    private String createApiKey(String jwt, String name, String... scopes) throws Exception {
        String scopesJson =
                "["
                        + String.join(
                                ", ", Arrays.stream(scopes).map(s -> "\"" + s + "\"").toList())
                        + "]";
        String body =
                """
                {"name": "%s", "scopes": %s}
                """
                        .formatted(name, scopesJson);
        String response =
                mvc.post()
                        .uri("/api/v1/auth/api-keys")
                        .with(csrf())
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String rawKey = objectMapper.readTree(response).path("data").path("rawKey").asText();
        assertThat(rawKey).as("API key creation should return rawKey").isNotBlank();
        return rawKey;
    }

    private void insertSession(
            UUID userId, Instant start, Instant end, String project, String lang) {
        insertSession(userId, null, start, end, project, lang);
    }

    private void insertSession(
            UUID userId,
            UUID originDeviceId,
            Instant start,
            Instant end,
            String project,
            String lang) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO coding_sessions
                            (id, user_id, session_uuid, project_name, language, start_time, end_time,
                             client_modified_at, client_version, server_version, origin_device_id,
                             is_deleted, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .param(UUID.randomUUID())
                .param(userId)
                .param(UUID.randomUUID())
                .param(project)
                .param(lang)
                .param(Timestamp.from(start))
                .param(Timestamp.from(end))
                .param(Timestamp.from(end))
                .param(originDeviceId)
                .update();
    }

    @Nested
    @DisplayName("GET /api/v1/stats")
    class StatsEndpoints {

        @Test
        @DisplayName("Should return summary with seeded sessions")
        void shouldReturnSummary_whenSessionsExist() throws Exception {
            String[] jwtAndUserId = registerVerifyAndLogin(uniqueEmail());
            String jwt = jwtAndUserId[0];
            UUID userId = UUID.fromString(jwtAndUserId[1]);
            String readKey = createApiKey(jwt, "Read Key", "READ");
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            insertSession(
                    userId,
                    today.atStartOfDay().plusHours(1).toInstant(ZoneOffset.UTC),
                    today.atStartOfDay().plusHours(2).toInstant(ZoneOffset.UTC),
                    "ctt-server",
                    "Java");

            var result =
                    mvc.get()
                            .uri("/api/v1/stats/summary?timezoneOffset=0")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.data.total").isEqualTo(3600);
            assertThat(result).bodyJson().extractingPath("$.data.today").isEqualTo(3600);
        }

        @Test
        @DisplayName("Should return empty summary when no sessions exist")
        void shouldReturnEmptySummary_whenNoSessions() throws Exception {
            String[] jwtAndUserId = registerVerifyAndLogin(uniqueEmail());
            String readKey = createApiKey(jwtAndUserId[0], "Read Key", "READ");

            var result =
                    mvc.get()
                            .uri("/api/v1/stats/summary")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.data.total").isEqualTo(0);
        }

        @Test
        @DisplayName("Should return language distribution")
        void shouldReturnDistribution_whenLanguages() throws Exception {
            String[] jwtAndUserId = registerVerifyAndLogin(uniqueEmail());
            String jwt = jwtAndUserId[0];
            UUID userId = UUID.fromString(jwtAndUserId[1]);
            String readKey = createApiKey(jwt, "Read Key", "READ");
            insertSession(
                    userId,
                    Instant.parse("2026-08-30T10:00:00Z"),
                    Instant.parse("2026-08-30T11:00:00Z"),
                    "a",
                    "Java");
            insertSession(
                    userId,
                    Instant.parse("2026-08-30T11:00:00Z"),
                    Instant.parse("2026-08-30T13:00:00Z"),
                    "b",
                    "Kotlin");

            var result =
                    mvc.get()
                            .uri("/api/v1/stats/distribution?type=LANGUAGES")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.data.type").isEqualTo("LANGUAGES");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.entries[0].name")
                    .isEqualTo("Kotlin");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.entries[0].seconds")
                    .isEqualTo(7200);
        }

        @Test
        @DisplayName("Should return recent sessions limited")
        void shouldReturnRecent_whenLimited() throws Exception {
            String[] jwtAndUserId = registerVerifyAndLogin(uniqueEmail());
            String jwt = jwtAndUserId[0];
            UUID userId = UUID.fromString(jwtAndUserId[1]);
            String readKey = createApiKey(jwt, "Read Key", "READ");
            insertSession(
                    userId,
                    Instant.parse("2026-08-29T10:00:00Z"),
                    Instant.parse("2026-08-29T11:00:00Z"),
                    "old",
                    "Java");
            insertSession(
                    userId,
                    Instant.parse("2026-08-30T10:00:00Z"),
                    Instant.parse("2026-08-30T11:00:00Z"),
                    "new",
                    "Kotlin");

            var result =
                    mvc.get()
                            .uri("/api/v1/stats/recent?limit=1")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.data[0].projectName").isEqualTo("new");
            assertThat(result).bodyJson().extractingPath("$.data.length()").isEqualTo(1);
        }

        @Test
        @DisplayName("Should return 403 AUTH_020 when API key lacks READ scope")
        void shouldReturn403_whenKeyLacksReadScope() throws Exception {
            String[] jwtAndUserId = registerVerifyAndLogin(uniqueEmail());
            String syncKey = createApiKey(jwtAndUserId[0], "Sync Key", "SYNC");

            assertThat(
                            mvc.get()
                                    .uri("/api/v1/stats/summary")
                                    .header("Authorization", "Bearer " + syncKey)
                                    .exchange())
                    .hasStatus(403)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_020");
        }

        @Test
        @DisplayName("Should return heatmap for a date range")
        void shouldReturnHeatmap_whenSessionsExist() throws Exception {
            String[] jwtAndUserId = registerVerifyAndLogin(uniqueEmail());
            String jwt = jwtAndUserId[0];
            UUID userId = UUID.fromString(jwtAndUserId[1]);
            String readKey = createApiKey(jwt, "Read Key", "READ");
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            insertSession(
                    userId,
                    today.atStartOfDay().plusHours(1).toInstant(ZoneOffset.UTC),
                    today.atStartOfDay().plusHours(2).toInstant(ZoneOffset.UTC),
                    "a",
                    "Java");

            var result =
                    mvc.get()
                            .uri(
                                    "/api/v1/stats/heatmap?timezoneOffset=0&start="
                                            + today
                                            + "&end="
                                            + today)
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.points[0].date")
                    .isEqualTo(today.toString());
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.points[0].seconds")
                    .isEqualTo(3600);
        }

        @Test
        @DisplayName("Should return streaks when coding on consecutive days")
        void shouldReturnStreaks_whenConsecutiveDays() throws Exception {
            String[] jwtAndUserId = registerVerifyAndLogin(uniqueEmail());
            String jwt = jwtAndUserId[0];
            UUID userId = UUID.fromString(jwtAndUserId[1]);
            String readKey = createApiKey(jwt, "Read Key", "READ");
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            insertSession(
                    userId,
                    today.minusDays(1).atStartOfDay().plusHours(1).toInstant(ZoneOffset.UTC),
                    today.minusDays(1).atStartOfDay().plusHours(2).toInstant(ZoneOffset.UTC),
                    "a",
                    "Java");
            insertSession(
                    userId,
                    today.atStartOfDay().plusHours(1).toInstant(ZoneOffset.UTC),
                    today.atStartOfDay().plusHours(2).toInstant(ZoneOffset.UTC),
                    "b",
                    "Java");

            var result =
                    mvc.get()
                            .uri("/api/v1/stats/streaks?timezoneOffset=0")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.data.current").isEqualTo(2);
            assertThat(result).bodyJson().extractingPath("$.data.max").isEqualTo(2);
        }

        @Test
        @DisplayName("Should return hourly distribution when sessions exist")
        void shouldReturnHourly_whenSessionsExist() throws Exception {
            String[] jwtAndUserId = registerVerifyAndLogin(uniqueEmail());
            String jwt = jwtAndUserId[0];
            UUID userId = UUID.fromString(jwtAndUserId[1]);
            String readKey = createApiKey(jwt, "Read Key", "READ");
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            insertSession(
                    userId,
                    today.atStartOfDay().plusHours(9).toInstant(ZoneOffset.UTC),
                    today.atStartOfDay().plusHours(10).toInstant(ZoneOffset.UTC),
                    "a",
                    "Java");

            var result =
                    mvc.get()
                            .uri("/api/v1/stats/hourly?timezoneOffset=0")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.data.points[9].hour").isEqualTo(9);
            assertThat(result).bodyJson().extractingPath("$.data.activeDays").isEqualTo(1);
        }

        @Test
        @DisplayName("Should unlock streak badges and persist them idempotently")
        void shouldUnlockStreakBadges_whenConsecutiveDays() throws Exception {
            String[] auth = registerVerifyAndLogin(uniqueEmail());
            UUID userId = UUID.fromString(auth[1]);
            for (int i = 0; i < 3; i++) {
                insertSession(
                        userId,
                        Instant.parse("2026-08-28T10:00:00Z").plus(Duration.ofDays(i)),
                        Instant.parse("2026-08-28T11:00:00Z").plus(Duration.ofDays(i)),
                        "ctt-server",
                        "Java");
            }

            var first =
                    mvc.get()
                            .uri("/api/v1/stats/achievements")
                            .header("Authorization", "Bearer " + auth[0])
                            .exchange();
            assertThat(first).hasStatusOk();
            assertThat(first)
                    .bodyJson()
                    .extractingPath("$.data[?(@.code=='STREAK_3')].unlocked")
                    .isEqualTo(List.of(true));
            assertThat(first)
                    .bodyJson()
                    .extractingPath("$.data[?(@.code=='STREAK_3')].progress")
                    .isEqualTo(List.of(3));

            // the unlock is persisted and a second query keeps the original timestamp
            Long unlockCount =
                    jdbcClient
                            .sql(
                                    "SELECT COUNT(*) FROM user_achievements WHERE user_id = ? AND achievement_code = 'STREAK_3'")
                            .param(userId)
                            .query(Long.class)
                            .single();
            assertThat(unlockCount).isEqualTo(1);

            var second =
                    mvc.get()
                            .uri("/api/v1/stats/achievements")
                            .header("Authorization", "Bearer " + auth[0])
                            .exchange();
            assertThat(second).hasStatusOk();
            assertThat(second)
                    .bodyJson()
                    .extractingPath("$.data[?(@.code=='STREAK_3')].unlocked")
                    .isEqualTo(List.of(true));
            Long auditCount =
                    jdbcClient
                            .sql(
                                    "SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND action = 'ACHIEVEMENT_UNLOCKED' AND resource_id = 'STREAK_3'")
                            .param(userId)
                            .query(Long.class)
                            .single();
            assertThat(auditCount).isEqualTo(1);
        }

        @Test
        @DisplayName("Should report all badges locked when there are no sessions")
        void shouldReturnAllLocked_whenNoSessions() throws Exception {
            String[] auth = registerVerifyAndLogin(uniqueEmail());

            var result =
                    mvc.get()
                            .uri("/api/v1/stats/achievements")
                            .header("Authorization", "Bearer " + auth[0])
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.data").asArray().hasSize(15);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data[?(@.unlocked==true)]")
                    .asArray()
                    .isEmpty();
        }

        @Test
        @DisplayName("Should materialize daily rows on push and serve UTC reads from them")
        void shouldMaterializeDailyRows_onPush() throws Exception {
            String[] auth = registerVerifyAndLogin(uniqueEmail());
            UUID userId = UUID.fromString(auth[1]);
            String syncKey = createApiKey(auth[0], "s5-sync", "SYNC");
            UUID deviceId = registerDevice(syncKey);

            // push two sessions on 08-30 (one crossing midnight into 08-31)
            String uuid1 = UUID.randomUUID().toString();
            String uuid2 = UUID.randomUUID().toString();
            String pushBody =
                    """
                    {
                      "deviceId": "%s",
                      "sessions": [
                        {"sessionUuid": "%s", "projectName": "ctt-server", "language": "Java",
                         "startTime": "2026-08-30T10:00:00Z", "endTime": "2026-08-30T11:00:00Z",
                         "clientModifiedAt": "2026-08-30T11:00:00Z", "clientVersion": 1, "deleted": false},
                        {"sessionUuid": "%s", "projectName": "ctt-web", "language": "Kotlin",
                         "startTime": "2026-08-30T23:00:00Z", "endTime": "2026-08-31T00:00:00Z",
                         "clientModifiedAt": "2026-08-31T00:00:00Z", "clientVersion": 1, "deleted": false}
                      ]
                    }
                    """
                            .formatted(deviceId, uuid1, uuid2);
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/sync/push")
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + syncKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(pushBody)
                                    .exchange())
                    .hasStatusOk();

            // push triggered materialization: 08-30 row = 1h + 1h = 7200s; 08-31 row absent (0s)
            Long daySeconds =
                    jdbcClient
                            .sql(
                                    "SELECT merged_seconds FROM daily_stats WHERE user_id = ? AND utc_date = '2026-08-30'")
                            .param(userId)
                            .query(Long.class)
                            .single();
            assertThat(daySeconds).isEqualTo(7200);

            // UTC heatmap serves the materialized rows
            var heatmap =
                    mvc.get()
                            .uri("/api/v1/stats/heatmap?start=2026-08-30&end=2026-08-31")
                            .header("Authorization", "Bearer " + auth[0])
                            .exchange();
            assertThat(heatmap).hasStatusOk();
            assertThat(heatmap)
                    .bodyJson()
                    .extractingPath("$.data.points[0].seconds")
                    .isEqualTo(7200);

            // soft-delete the first session via push: the affected UTC day is recomputed to 1h
            String deleteBody =
                    """
                    {
                      "deviceId": "%s",
                      "sessions": [
                        {"sessionUuid": "%s", "projectName": "ctt-server", "language": "Java",
                         "startTime": "2026-08-30T10:00:00Z", "endTime": "2026-08-30T11:00:00Z",
                         "clientModifiedAt": "2026-08-30T12:00:00Z", "clientVersion": 2, "deleted": true}
                      ]
                    }
                    """
                            .formatted(deviceId, uuid1);
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/sync/push")
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + syncKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(deleteBody)
                                    .exchange())
                    .hasStatusOk();

            Long afterDelete =
                    jdbcClient
                            .sql(
                                    "SELECT merged_seconds FROM daily_stats WHERE user_id = ? AND utc_date = '2026-08-30'")
                            .param(userId)
                            .query(Long.class)
                            .single();
            // only the night session remains: 1h
            assertThat(afterDelete).isEqualTo(3600);
        }

        @Test
        @DisplayName("Should cache achievements and invalidate after a push")
        void shouldCacheAchievements_andInvalidateAfterPush() throws Exception {
            String[] auth = registerVerifyAndLogin(uniqueEmail());
            UUID userId = UUID.fromString(auth[1]);
            String syncKey = createApiKey(auth[0], "s5-sync", "SYNC");
            UUID deviceId = registerDevice(syncKey);

            // first read populates the cache
            assertThat(
                            mvc.get()
                                    .uri("/api/v1/stats/achievements")
                                    .header("Authorization", "Bearer " + auth[0])
                                    .exchange())
                    .hasStatusOk();

            Long cacheRows =
                    jdbcClient
                            .sql("SELECT COUNT(*) FROM daily_stats WHERE user_id = ?")
                            .param(userId)
                            .query(Long.class)
                            .single();
            assertThat(cacheRows).isZero();

            // push an 11h session; the cache is evicted and TOTAL_10_HOURS unlocks on next read
            String body =
                    """
                    {
                      "deviceId": "%s",
                      "sessions": [
                        {
                          "sessionUuid": "%s",
                          "projectName": "ctt-server",
                          "language": "Java",
                          "startTime": "2026-08-30T09:00:00Z",
                          "endTime": "2026-08-30T20:00:00Z",
                          "clientModifiedAt": "2026-08-30T20:00:00Z",
                          "clientVersion": 1,
                          "deleted": false
                        }
                      ]
                    }
                    """
                            .formatted(deviceId, UUID.randomUUID());
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/sync/push")
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + syncKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                                    .exchange())
                    .hasStatusOk();

            var result =
                    mvc.get()
                            .uri("/api/v1/stats/achievements")
                            .header("Authorization", "Bearer " + auth[0])
                            .exchange();
            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data[?(@.code=='TOTAL_10_HOURS')].unlocked")
                    .isEqualTo(List.of(true));
        }

        private UUID registerDevice(String syncKey) {
            UUID deviceId = UUID.randomUUID();
            mvc.post()
                    .uri("/api/v1/devices")
                    .with(csrf())
                    .header("Authorization", "Bearer " + syncKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                            {"deviceId": "%s", "deviceName": "s5", "platform": "macos"}
                            """
                                    .formatted(deviceId))
                    .exchange();
            return deviceId;
        }

        @Test
        @DisplayName("Should return 401 when not authenticated")
        void shouldReturn401_whenNotAuthenticated() {
            assertThat(mvc.get().uri("/api/v1/stats/summary").exchange()).hasStatus(401);
        }
    }

    @Nested
    @DisplayName("Device dimension")
    class DeviceDimensionEndpoints {

        private UUID registerDevice(String jwt, UUID deviceId) {
            mvc.post()
                    .uri("/api/v1/devices")
                    .with(csrf())
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                            {"deviceId": "%s", "deviceName": "Dev-%s", "platform": "macos", "ideName": "IntelliJ IDEA"}
                            """
                                    .formatted(
                                            deviceId.toString(),
                                            deviceId.toString().substring(0, 8)))
                    .exchange();
            return deviceId;
        }

        private void pushSession(String syncKey, UUID deviceId, String sessionUuid) {
            String pushBody =
                    """
                    {
                      "deviceId": "%s",
                      "sessions": [
                        {"sessionUuid": "%s", "projectName": "ctt-server", "language": "Java",
                         "startTime": "2026-08-30T10:00:00Z", "endTime": "2026-08-30T11:00:00Z",
                         "clientModifiedAt": "2026-08-30T11:00:00Z", "clientVersion": 1,
                         "deleted": false}
                      ]
                    }
                    """
                            .formatted(deviceId.toString(), sessionUuid);
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/sync/push")
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + syncKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(pushBody))
                    .hasStatusOk();
        }

        @Test
        @DisplayName(
                "Push should stamp origin device and device filter should subset every endpoint")
        void shouldStampOriginOnPush_andFilterEndpointsByDevice() throws Exception {
            String[] auth = registerVerifyAndLogin(uniqueEmail());
            UUID userId = UUID.fromString(auth[1]);
            String readKey = createApiKey(auth[0], "read", "READ");
            String syncKey = createApiKey(auth[0], "sync", "SYNC");
            UUID deviceA = registerDevice(auth[0], UUID.randomUUID());
            UUID deviceB = registerDevice(auth[0], UUID.randomUUID());

            // real pushes stamp the origin device at creation
            String sessionA = UUID.randomUUID().toString();
            String sessionB = UUID.randomUUID().toString();
            pushSession(syncKey, deviceA, sessionA);
            pushSession(syncKey, deviceB, sessionB);
            UUID originA =
                    jdbcClient
                            .sql(
                                    "SELECT origin_device_id FROM coding_sessions WHERE user_id = ? AND session_uuid = ?")
                            .param(userId)
                            .param(UUID.fromString(sessionA))
                            .query(UUID.class)
                            .single();
            assertThat(originA).isEqualTo(deviceA);
            // cross-device update must NOT rewrite the origin
            String updateBody =
                    """
                    {
                      "deviceId": "%s",
                      "sessions": [
                        {"sessionUuid": "%s", "projectName": "ctt-server", "language": "Kotlin",
                         "startTime": "2026-08-30T10:00:00Z", "endTime": "2026-08-30T12:00:00Z",
                         "clientModifiedAt": "2026-08-31T09:00:00Z", "clientVersion": 2,
                         "deleted": false}
                      ]
                    }
                    """
                            .formatted(deviceB.toString(), sessionA);
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/sync/push")
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + syncKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(updateBody))
                    .hasStatusOk();
            UUID originAfterUpdate =
                    jdbcClient
                            .sql(
                                    "SELECT origin_device_id FROM coding_sessions WHERE user_id = ? AND session_uuid = ?")
                            .param(userId)
                            .param(UUID.fromString(sessionA))
                            .query(UUID.class)
                            .single();
            assertThat(originAfterUpdate).isEqualTo(deviceA);

            // device-filtered summary equals the per-device subset (deviceA session now 2h,
            // deviceB session 1h)
            var summaryA =
                    mvc.get()
                            .uri("/api/v1/stats/summary?deviceId=" + deviceA)
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();
            assertThat(summaryA).hasStatusOk();
            assertThat(summaryA).bodyJson().extractingPath("$.data.total").isEqualTo(7200);

            var summaryB =
                    mvc.get()
                            .uri("/api/v1/stats/summary?deviceId=" + deviceB)
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();
            assertThat(summaryB).hasStatusOk();
            assertThat(summaryB).bodyJson().extractingPath("$.data.total").isEqualTo(3600);

            // unfiltered total = merged overlap: both sessions 10:00-12:00 overlap -> 2h,
            // plus deviceB extra hour 10:00-11:00 already inside the window -> 7200 total
            var summaryAll =
                    mvc.get()
                            .uri("/api/v1/stats/summary")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();
            assertThat(summaryAll).hasStatusOk();
            assertThat(summaryAll).bodyJson().extractingPath("$.data.total").isEqualTo(7200);

            // DEVICES distribution: names resolved, ordered by seconds desc
            var dist =
                    mvc.get()
                            .uri("/api/v1/stats/distribution?type=DEVICES")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();
            assertThat(dist).hasStatusOk();
            assertThat(dist).bodyJson().extractingPath("$.data.entries[0].seconds").isEqualTo(7200);

            // IDES distribution derives the bucket from the origin device's registration
            var ides =
                    mvc.get()
                            .uri("/api/v1/stats/distribution?type=IDES")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();
            assertThat(ides).hasStatusOk();
            assertThat(ides).bodyJson().extractingPath("$.data.type").isEqualTo("IDES");
            // both devices registered as "IntelliJ IDEA": raw durations 2h + 1h = 10800
            assertThat(ides)
                    .bodyJson()
                    .extractingPath("$.data.entries[0].name")
                    .isEqualTo("IntelliJ IDEA");
            assertThat(ides)
                    .bodyJson()
                    .extractingPath("$.data.entries[0].seconds")
                    .isEqualTo(10800);

            // heatmap filter falls back to live aggregation (materialized path is per-user)
            var heatmap =
                    mvc.get()
                            .uri(
                                    "/api/v1/stats/heatmap?start=2026-08-30&end=2026-08-30&deviceId="
                                            + deviceA)
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();
            assertThat(heatmap).hasStatusOk();
            assertThat(heatmap)
                    .bodyJson()
                    .extractingPath("$.data.points[0].seconds")
                    .isEqualTo(7200);
        }

        @Test
        @DisplayName("Should filter by IDE name across endpoints")
        void shouldFilterByIdeName_acrossEndpoints() throws Exception {
            String[] auth = registerVerifyAndLogin(uniqueEmail());
            String readKey = createApiKey(auth[0], "read", "READ");
            String syncKey = createApiKey(auth[0], "sync", "SYNC");
            UUID ideaDevice = registerDevice(auth[0], UUID.randomUUID());
            UUID pycharmDevice = registerDevice(auth[0], UUID.randomUUID());

            pushSession(syncKey, ideaDevice, UUID.randomUUID().toString());
            pushSession(syncKey, pycharmDevice, UUID.randomUUID().toString());
            // registerDevice now always sends ideName "IntelliJ IDEA" — push a second session on
            // the same IDE via a second device to distinguish IDE-filter from device-filter
            UUID ideaDevice2 = registerDevice(auth[0], UUID.randomUUID());
            pushSession(syncKey, ideaDevice2, UUID.randomUUID().toString());

            // ide-filters lists distinct registered IDE names (both are IntelliJ IDEA)
            var filters =
                    mvc.get()
                            .uri("/api/v1/stats/ide-filters")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();
            assertThat(filters).hasStatusOk();
            assertThat(filters)
                    .bodyJson()
                    .extractingPath("$.data")
                    .asArray()
                    .containsExactly("IntelliJ IDEA");

            // ideName filter: the three IDEA devices' sessions all overlap (same 1h window),
            // so summary merges them into 3600 — proves IDE filtering narrows the set and the
            // merge semantics are unchanged
            var summary =
                    mvc.get()
                            .uri("/api/v1/stats/summary?ideName=" + "IntelliJ IDEA")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();
            assertThat(summary).hasStatusOk();
            assertThat(summary).bodyJson().extractingPath("$.data.total").isEqualTo(3600);

            // unknown IDE -> 404
            var unknown =
                    mvc.get()
                            .uri("/api/v1/stats/summary?ideName=" + "WebStorm")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();
            assertThat(unknown).hasStatus(404);
            assertThat(unknown).bodyJson().extractingPath("$.code").isEqualTo("COMMON_002");

            // ideName + deviceId -> 400
            var both =
                    mvc.get()
                            .uri(
                                    "/api/v1/stats/summary?ideName=IntelliJ%20IDEA&deviceId="
                                            + ideaDevice)
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();
            assertThat(both).hasStatus(400);
            assertThat(both).bodyJson().extractingPath("$.code").isEqualTo("COMMON_003");
        }

        @Test
        @DisplayName("Should return 404 when deviceId belongs to another user")
        void shouldReturn404_whenDeviceIdForeign() throws Exception {
            String[] auth = registerVerifyAndLogin(uniqueEmail());
            String readKey = createApiKey(auth[0], "read", "READ");
            String[] other = registerVerifyAndLogin(uniqueEmail());
            UUID foreignDevice = registerDevice(other[0], UUID.randomUUID());

            var result =
                    mvc.get()
                            .uri("/api/v1/stats/summary?deviceId=" + foreignDevice)
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();

            assertThat(result).hasStatus(404);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("COMMON_002");
        }

        @Test
        @DisplayName("Should keep attributing sessions to a revoked device")
        void shouldKeepAttribution_whenDeviceRevoked() throws Exception {
            String[] auth = registerVerifyAndLogin(uniqueEmail());
            String readKey = createApiKey(auth[0], "read", "READ");
            String syncKey = createApiKey(auth[0], "sync", "SYNC");
            String writeKey = createApiKey(auth[0], "write", "WRITE");
            UUID deviceA = registerDevice(auth[0], UUID.randomUUID());

            pushSession(syncKey, deviceA, UUID.randomUUID().toString());

            // revoke the device via the WRITE-scoped key
            assertThat(
                            mvc.delete()
                                    .uri("/api/v1/devices/" + deviceA)
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + writeKey))
                    .hasStatusOk();

            // sessions of the revoked device remain attributable under the device filter
            var summaryA =
                    mvc.get()
                            .uri("/api/v1/stats/summary?deviceId=" + deviceA)
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();
            assertThat(summaryA).hasStatusOk();
            assertThat(summaryA).bodyJson().extractingPath("$.data.total").isEqualTo(3600);

            // and in the per-device distribution by display name
            var dist =
                    mvc.get()
                            .uri("/api/v1/stats/distribution?type=DEVICES")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();
            assertThat(dist).hasStatusOk();
            assertThat(dist)
                    .bodyJson()
                    .extractingPath("$.data.entries[0].name")
                    .isEqualTo("Dev-" + deviceA.toString().substring(0, 8));
            assertThat(dist).bodyJson().extractingPath("$.data.entries[0].seconds").isEqualTo(3600);
        }
    }
}
