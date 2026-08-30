package com.ahogek.cttserver.leaderboard;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.leaderboard.service.LeaderboardService;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * End-to-end tests for the global leaderboard.
 *
 * <p>Seeds users with coding sessions, computes their scores through {@link LeaderboardService},
 * and verifies the Redis-backed ranking through the HTTP endpoint including ranking ties,
 * concurrent updates, and auth/scope boundaries.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@BaseIntegrationTest
@TestPropertySource(properties = {"ctt.mail.outbox.poll-interval-ms=999999999"})
@DisplayName("Leaderboard Integration Tests")
class LeaderboardIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LeaderboardService leaderboardService;
    @Autowired private StringRedisTemplate redisTemplate;

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "LeaderboardUser";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    private record RegisteredUser(UUID id, String jwt) {}

    @AfterEach
    void tearDown() {
        Set<String> keys = redisTemplate.keys("leaderboard:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
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

    private String uniqueEmail() {
        return "leaderboard." + UUID.randomUUID() + "@test.example";
    }

    private RegisteredUser registerAndLogin(String email) throws Exception {
        UserRegisterRequest request =
                new UserRegisterRequest(email, DISPLAY_NAME, PASSWORD, "1.0.0", null);
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
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

        LoginRequest login = new LoginRequest(email, PASSWORD, "device-lb", null);
        String loginResponse =
                mvc.post()
                        .uri("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login))
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String accessToken =
                objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        UUID userId =
                jdbcClient
                        .sql("SELECT id FROM users WHERE email = ?")
                        .param(email)
                        .query(UUID.class)
                        .single();
        return new RegisteredUser(userId, accessToken);
    }

    private String createReadApiKey(String jwt) throws Exception {
        String response =
                mvc.post()
                        .uri("/api/v1/auth/api-keys")
                        .with(csrf())
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Read Key\", \"scopes\": [\"READ\"]}")
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String rawKey = objectMapper.readTree(response).path("data").path("rawKey").asText();
        assertThat(rawKey).isNotBlank();
        return rawKey;
    }

    private String createApiKey(String jwt, String... scopes) throws Exception {
        String scopesJson =
                "["
                        + String.join(
                                ", ",
                                java.util.Arrays.stream(scopes).map(s -> "\"" + s + "\"").toList())
                        + "]";
        String response =
                mvc.post()
                        .uri("/api/v1/auth/api-keys")
                        .with(csrf())
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Key\", \"scopes\": " + scopesJson + "}")
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String rawKey = objectMapper.readTree(response).path("data").path("rawKey").asText();
        assertThat(rawKey).isNotBlank();
        return rawKey;
    }

    private UUID insertDevice(UUID userId) {
        UUID deviceId = UUID.randomUUID();
        jdbcClient
                .sql(
                        """
                        INSERT INTO devices
                            (id, user_id, device_name, platform, ide_name, ide_version, app_version,
                             last_ip, created_at, last_seen_at, updated_at)
                        VALUES (?, ?, 'lb-device', 'macos', 'IntelliJ IDEA', '2026.1', '1.0.0',
                                '127.0.0.1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .param(deviceId)
                .param(userId)
                .update();
        return deviceId;
    }

    private void insertSession(UUID userId, String start, String end, String project) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO coding_sessions
                            (id, user_id, session_uuid, project_name, language, start_time, end_time,
                             client_modified_at, client_version, server_version, updated_by_device_id,
                             is_deleted, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'Java', ?, ?, ?, 1, 1, NULL, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .param(UUID.randomUUID())
                .param(userId)
                .param(UUID.randomUUID())
                .param(project)
                .param(Timestamp.from(Instant.parse(start)))
                .param(Timestamp.from(Instant.parse(end)))
                .param(Timestamp.from(Instant.parse(end)))
                .update();
    }

    private void updateScore(UUID userId) {
        leaderboardService.updateUserScores(userId);
    }

    @Nested
    @DisplayName("ranking")
    class RankingTests {

        @Test
        @DisplayName("Should rank users by total coding duration")
        void shouldRankUsersByTotal() throws Exception {
            RegisteredUser alice = registerAndLogin(uniqueEmail());
            RegisteredUser bob = registerAndLogin(uniqueEmail());
            insertSession(alice.id(), "2026-08-30T10:00:00Z", "2026-08-30T12:00:00Z", "ctt-server");
            insertSession(bob.id(), "2026-08-30T14:00:00Z", "2026-08-30T15:00:00Z", "ctt-web");
            updateScore(alice.id());
            updateScore(bob.id());

            RegisteredUser viewer = registerAndLogin(uniqueEmail());
            String readKey = createReadApiKey(viewer.jwt());

            var result =
                    mvc.get()
                            .uri("/api/v1/leaderboard?dimension=TOTAL&limit=10&offset=0")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.entries[0].userId")
                    .isEqualTo(alice.id().toString());
            assertThat(result).bodyJson().extractingPath("$.data.entries[0].score").isEqualTo(7200);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.entries[1].userId")
                    .isEqualTo(bob.id().toString());
            assertThat(result).bodyJson().extractingPath("$.data.entries[1].score").isEqualTo(3600);
        }

        @Test
        @DisplayName("Should rank by streak dimension")
        void shouldRankByStreak() throws Exception {
            RegisteredUser threeDays = registerAndLogin(uniqueEmail());
            RegisteredUser oneDay = registerAndLogin(uniqueEmail());
            for (int i = 0; i < 3; i++) {
                insertSession(
                        threeDays.id(),
                        "2026-08-" + (28 + i) + "T10:00:00Z",
                        "2026-08-" + (28 + i) + "T11:00:00Z",
                        "ctt-server");
            }
            insertSession(
                    oneDay.id(), "2026-08-30T10:00:00Z", "2026-08-30T11:00:00Z", "ctt-server");
            updateScore(threeDays.id());
            updateScore(oneDay.id());

            RegisteredUser viewer = registerAndLogin(uniqueEmail());
            String readKey = createReadApiKey(viewer.jwt());

            var result =
                    mvc.get()
                            .uri("/api/v1/leaderboard?dimension=STREAK&limit=10&offset=0")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.entries[0].userId")
                    .isEqualTo(threeDays.id().toString());
            assertThat(result).bodyJson().extractingPath("$.data.entries[0].score").isEqualTo(3);
            assertThat(result).bodyJson().extractingPath("$.data.entries[1].score").isEqualTo(1);
        }

        @Test
        @DisplayName("Should assign the same score to tied durations")
        void shouldTieScores() throws Exception {
            RegisteredUser userA = registerAndLogin(uniqueEmail());
            RegisteredUser userB = registerAndLogin(uniqueEmail());
            insertSession(userA.id(), "2026-08-30T10:00:00Z", "2026-08-30T12:00:00Z", "ctt-server");
            insertSession(userB.id(), "2026-08-30T14:00:00Z", "2026-08-30T16:00:00Z", "ctt-web");
            updateScore(userA.id());
            updateScore(userB.id());

            RegisteredUser viewer = registerAndLogin(uniqueEmail());
            String readKey = createReadApiKey(viewer.jwt());

            var result =
                    mvc.get()
                            .uri("/api/v1/leaderboard?dimension=TOTAL&limit=10&offset=0")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.data.entries").asArray().hasSize(2);
            assertThat(result).bodyJson().extractingPath("$.data.entries[0].score").isEqualTo(7200);
            assertThat(result).bodyJson().extractingPath("$.data.entries[1].score").isEqualTo(7200);
            // competition ranking: tied scores share the same rank
            assertThat(result).bodyJson().extractingPath("$.data.entries[0].rank").isEqualTo(1);
            assertThat(result).bodyJson().extractingPath("$.data.entries[1].rank").isEqualTo(1);
        }

        @Test
        @DisplayName("Should keep the ranking correct under concurrent score updates")
        void shouldSurviveConcurrentUpdates() throws Exception {
            RegisteredUser user = registerAndLogin(uniqueEmail());
            int threadCount = 8;
            try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
                CountDownLatch ready = new CountDownLatch(threadCount);
                CountDownLatch go = new CountDownLatch(1);
                for (int t = 0; t < threadCount; t++) {
                    final int idx = t;
                    executor.submit(
                            () -> {
                                try {
                                    insertSession(
                                            user.id(),
                                            "2026-08-30T" + (10 + idx) + ":00:00Z",
                                            "2026-08-30T" + (11 + idx) + ":00:00Z",
                                            "p" + idx);
                                    ready.countDown();
                                    go.await();
                                    leaderboardService.updateUserScores(user.id());
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                go.countDown();
            }

            // 8 sessions of 1h each, no overlaps -> 28800s total
            RegisteredUser viewer = registerAndLogin(uniqueEmail());
            String readKey = createReadApiKey(viewer.jwt());
            var result =
                    mvc.get()
                            .uri("/api/v1/leaderboard?dimension=TOTAL&limit=10&offset=0")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();
            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.entries[0].userId")
                    .isEqualTo(user.id().toString());
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.entries[0].score")
                    .isEqualTo(28800);
        }

        @Test
        @DisplayName("Should update the leaderboard after a real push")
        void shouldUpdateLeaderboardAfterRealPush() throws Exception {
            RegisteredUser user = registerAndLogin(uniqueEmail());
            String syncKey = createApiKey(user.jwt(), "SYNC");
            UUID deviceId = insertDevice(user.id());
            String sessionUuid = UUID.randomUUID().toString();
            String pushBody =
                    """
                    {
                      "deviceId": "%s",
                      "sessions": [
                        {
                          "sessionUuid": "%s",
                          "projectName": "ctt-server",
                          "language": "Java",
                          "startTime": "2026-08-30T10:00:00Z",
                          "endTime": "2026-08-30T11:00:00Z",
                          "clientModifiedAt": "2026-08-30T11:00:00Z",
                          "clientVersion": 1,
                          "deleted": false
                        }
                      ]
                    }
                    """
                            .formatted(deviceId, sessionUuid);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/sync/push")
                                    .with(csrf())
                                    .header("Authorization", "Bearer " + syncKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(pushBody)
                                    .exchange())
                    .hasStatusOk();

            // the pushing user queries with their own READ key, so the current rank is theirs
            String readKey = createApiKey(user.jwt(), "READ");
            var result =
                    mvc.get()
                            .uri("/api/v1/leaderboard?dimension=TOTAL&limit=10&offset=0")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.entries[0].userId")
                    .isEqualTo(user.id().toString());
            assertThat(result).bodyJson().extractingPath("$.data.entries[0].score").isEqualTo(3600);
            assertThat(result).bodyJson().extractingPath("$.data.currentUserRank").isEqualTo(1);
        }

        @Test
        @DisplayName("Should rank by the current week period")
        void shouldRankByWeekPeriod_whenSessionsInCurrentWeek() throws Exception {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            String thisWeek = today.toString();
            String lastWeek = today.minusWeeks(1).toString();

            RegisteredUser alice = registerAndLogin(uniqueEmail());
            RegisteredUser bob = registerAndLogin(uniqueEmail());
            // alice: 1h this week + 2h last week; bob: 3h this week
            insertSession(
                    alice.id(), thisWeek + "T10:00:00Z", thisWeek + "T11:00:00Z", "ctt-server");
            insertSession(
                    alice.id(), lastWeek + "T10:00:00Z", lastWeek + "T12:00:00Z", "ctt-server");
            insertSession(bob.id(), thisWeek + "T14:00:00Z", thisWeek + "T17:00:00Z", "ctt-web");
            updateScore(alice.id());
            updateScore(bob.id());

            RegisteredUser viewer = registerAndLogin(uniqueEmail());
            String readKey = createReadApiKey(viewer.jwt());

            var result =
                    mvc.get()
                            .uri(
                                    "/api/v1/leaderboard?dimension=TOTAL&period=WEEK&limit=10&offset=0")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.entries[0].userId")
                    .isEqualTo(bob.id().toString());
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.entries[0].score")
                    .isEqualTo(10800);
            assertThat(result).bodyJson().extractingPath("$.data.entries[1].score").isEqualTo(3600);
        }

        @Test
        @DisplayName("Should rank by the night-owl window")
        void shouldRankByNightOwl_whenSessionsInWindow() throws Exception {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            RegisteredUser nightOwl = registerAndLogin(uniqueEmail());
            // 23:00-24:00 is inside the 22:00-05:00 window
            insertSession(
                    nightOwl.id(),
                    today + "T23:00:00Z",
                    today.plusDays(1) + "T00:00:00Z",
                    "ctt-server");
            updateScore(nightOwl.id());

            RegisteredUser viewer = registerAndLogin(uniqueEmail());
            String readKey = createReadApiKey(viewer.jwt());

            var result =
                    mvc.get()
                            .uri("/api/v1/leaderboard?dimension=NIGHT_OWL&limit=10&offset=0")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.data.entries[0].score").isEqualTo(3600);
        }

        @Test
        @DisplayName("Should rank by the early-bird window")
        void shouldRankByEarlyBird_whenSessionsInWindow() throws Exception {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            RegisteredUser earlyBird = registerAndLogin(uniqueEmail());
            // 07:00-08:00 is inside the 06:00-09:00 window
            insertSession(earlyBird.id(), today + "T07:00:00Z", today + "T08:00:00Z", "ctt-server");
            updateScore(earlyBird.id());

            RegisteredUser viewer = registerAndLogin(uniqueEmail());
            String readKey = createReadApiKey(viewer.jwt());

            var result =
                    mvc.get()
                            .uri("/api/v1/leaderboard?dimension=EARLY_BIRD&limit=10&offset=0")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.data.entries[0].score").isEqualTo(3600);
        }

        @Test
        @DisplayName("Should rank by week-over-week growth")
        void shouldRankByGrowth_whenWeekOverWeekDiffers() throws Exception {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            String thisWeek = today.toString();
            String lastWeek = today.minusWeeks(1).toString();

            RegisteredUser growing = registerAndLogin(uniqueEmail());
            RegisteredUser shrinking = registerAndLogin(uniqueEmail());
            // growing: +1h this week (2h this week vs 1h last week)
            insertSession(
                    growing.id(), thisWeek + "T10:00:00Z", thisWeek + "T12:00:00Z", "ctt-server");
            insertSession(
                    growing.id(), lastWeek + "T10:00:00Z", lastWeek + "T11:00:00Z", "ctt-server");
            // shrinking: -2h this week (1h this week vs 3h last week)
            insertSession(
                    shrinking.id(), thisWeek + "T10:00:00Z", thisWeek + "T11:00:00Z", "ctt-web");
            insertSession(
                    shrinking.id(), lastWeek + "T10:00:00Z", lastWeek + "T13:00:00Z", "ctt-web");
            updateScore(growing.id());
            updateScore(shrinking.id());

            RegisteredUser viewer = registerAndLogin(uniqueEmail());
            String readKey = createReadApiKey(viewer.jwt());

            var result =
                    mvc.get()
                            .uri("/api/v1/leaderboard?dimension=GROWTH&limit=10&offset=0")
                            .header("Authorization", "Bearer " + readKey)
                            .exchange();

            assertThat(result).hasStatusOk();
            // growing +3600s, shrinking -7200s
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.entries[0].userId")
                    .isEqualTo(growing.id().toString());
            assertThat(result).bodyJson().extractingPath("$.data.entries[0].score").isEqualTo(3600);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.entries[1].score")
                    .isEqualTo(-7200);
        }
    }

    @Nested
    @DisplayName("auth boundaries")
    class AuthBoundaryTests {

        @Test
        @DisplayName("Should return 401 when unauthenticated")
        void shouldReturn401_whenNotAuthenticated() {
            assertThat(mvc.get().uri("/api/v1/leaderboard?dimension=TOTAL").exchange())
                    .hasStatus(401);
        }

        @Test
        @DisplayName("Should return 400 for an invalid dimension")
        void shouldReturn400_whenDimensionInvalid() throws Exception {
            RegisteredUser viewer = registerAndLogin(uniqueEmail());
            String readKey = createReadApiKey(viewer.jwt());

            assertThat(
                            mvc.get()
                                    .uri("/api/v1/leaderboard?dimension=BOGUS")
                                    .header("Authorization", "Bearer " + readKey)
                                    .exchange())
                    .hasStatus(400);
        }

        @Test
        @DisplayName("Should return 400 for an unsupported dimension/period combination")
        void shouldReturn400_whenPeriodUnsupported() throws Exception {
            RegisteredUser viewer = registerAndLogin(uniqueEmail());
            String readKey = createReadApiKey(viewer.jwt());

            assertThat(
                            mvc.get()
                                    .uri("/api/v1/leaderboard?dimension=STREAK&period=WEEK")
                                    .header("Authorization", "Bearer " + readKey)
                                    .exchange())
                    .hasStatus(400);
            assertThat(
                            mvc.get()
                                    .uri("/api/v1/leaderboard?dimension=GROWTH&period=ALL")
                                    .header("Authorization", "Bearer " + readKey)
                                    .exchange())
                    .hasStatus(400);
        }
    }
}
