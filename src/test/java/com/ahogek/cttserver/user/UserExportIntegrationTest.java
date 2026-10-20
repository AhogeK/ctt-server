package com.ahogek.cttserver.user;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * End-to-end tests for the account data export.
 *
 * <p>The point of this file is compatibility with the desktop client's importer, so it asserts the
 * importer's rules rather than our own preferences: the version it accepts, the field names it
 * reads, and — the one that fails loudly in production — the timestamp format it parses. Its model
 * is mirrored here with unknown-field tolerance, exactly as Gson reads it, so a change that breaks
 * importability fails this test instead of failing a user's import.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-19
 */
@BaseIntegrationTest
@TestPropertySource(
        properties = {
            "ctt.mail.outbox.poll-interval-ms=999999999",
            "ctt.security.rate-limit.enabled=true"
        })
@DisplayName("Account data export")
class UserExportIntegrationTest {

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "ExportTestUser";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");
    private static final String EXPORT_URL = "/api/v1/users/me/export";

    /** The field set the desktop importer reads; a missing one breaks a user's import. */
    private static final List<String> PLUGIN_SESSION_FIELDS =
            List.of(
                    "sessionUuid",
                    "userId",
                    "projectName",
                    "language",
                    "platform",
                    "ideName",
                    "startTime",
                    "endTime",
                    "lastModified");

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;
    @LocalServerPort private int port;

    /** Real HTTP includes the application's context path, which MockMvc never sees. */
    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /** Real HTTP, because a {@code StreamingResponseBody} is not observable through MockMvc. */
    private final RestClient rest = RestClient.create();

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM coding_sessions").update();
        jdbcClient.sql("DELETE FROM devices").update();
        jdbcClient.sql("DELETE FROM mail_outbox").update();
        jdbcClient.sql("DELETE FROM email_verification_tokens").update();
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM audit_logs").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    @Test
    @DisplayName("Should produce a document the desktop importer can read")
    void shouldProduceImportableDocument_whenExporting() throws Exception {
        UUID userId = register(userEmail());
        UUID deviceId = UUID.randomUUID();
        registerDevice(userId, deviceId);
        String accessToken = login(userId, deviceId.toString())[0];

        // 00:30 UTC is 08:30 in the +08:00 zone the request asks for; the importer reads the
        // string as a local time, so a UTC-looking value would silently shift every session.
        insertSession(userId, deviceId, "alpha-project", "Kotlin", "2026-10-19T00:30:00Z", false);
        insertSession(userId, null, "beta-project", "Java", "2026-10-19T02:00:00Z", false);
        insertSession(userId, null, "deleted-project", "Rust", "2026-10-19T03:00:00Z", true);

        ResponseEntity<String> response = export(accessToken, 480);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains(".json");

        JsonNode document = objectMapper.readTree(response.getBody());

        // The importer rejects any other version outright.
        assertThat(document.path("exportVersion").asText()).isEqualTo("1.0");
        // Soft-deleted sessions are not exported: the importer inserts everything as live.
        assertThat(document.path("totalSessions").asInt()).isEqualTo(2);
        assertThat(document.path("sessions")).hasSize(2);

        for (JsonNode session : document.path("sessions")) {
            for (String field : PLUGIN_SESSION_FIELDS) {
                assertThat(session.hasNonNull(field))
                        .as("session field %s missing in %s", field, session)
                        .isTrue();
            }
            // The importer parses these with ISO_LOCAL_DATE_TIME: a trailing Z fails the whole
            // file.
            LocalDateTime.parse(
                    session.get("startTime").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            LocalDateTime.parse(
                    session.get("endTime").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            LocalDateTime.parse(
                    session.get("lastModified").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        JsonNode withDevice = sessionOf(document, "alpha-project");
        assertThat(withDevice.get("startTime").asText()).isEqualTo("2026-10-19T08:30:00");
        assertThat(withDevice.get("platform").asText()).isEqualTo("macOS");
        assertThat(withDevice.get("ideName").asText()).isEqualTo("IntelliJ IDEA");
        assertThat(withDevice.get("language").asText()).isEqualTo("Kotlin");
        assertThat(withDevice.get("userId").asText()).isEqualTo(userId.toString());
        // A session whose device was never registered still needs a non-null value on import.
        assertThat(sessionOf(document, "beta-project").get("platform").asText()).isEmpty();

        // Read it exactly as the importer does: unknown fields ignored, timestamps parsed locally.
        ExportMirror mirror = objectMapper.readValue(response.getBody(), ExportMirror.class);
        assertThat(mirror.exportVersion()).isEqualTo("1.0");
        assertThat(mirror.sessions()).hasSize(2);
        // Sessions are ordered by write order (an id), which says nothing to a client: locate by
        // project rather than assuming a position, or the assertion depends on UUID luck.
        assertThat(mirrorSession(mirror, "alpha-project").startTime())
                .isEqualTo(LocalDateTime.of(2026, 10, 19, 8, 30));
        assertThat(mirrorSession(mirror, "beta-project").platform()).isEmpty();

        assertThat(document.path("serverExport").isObject()).isTrue();
        assertThat(document.path("serverExport").path("account").path("email").asText())
                .isEqualTo(
                        jdbcClient
                                .sql("SELECT email FROM users WHERE id = ?")
                                .param(userId)
                                .query(String.class)
                                .single());
        assertThat(document.path("serverExport").path("loginSessions")).hasSize(1);

        // No credential leaves the building, whatever the extension block carries.
        assertThat(response.getBody())
                .doesNotContain("passwordHash")
                .doesNotContain("keyHash")
                .doesNotContain("tokenHash");
    }

    @Test
    @DisplayName("Should read every session across pages when the history exceeds one page")
    void shouldPageThroughAllSessions_whenHistoryExceedsOnePage() throws Exception {
        UUID userId = register(userEmail());
        String accessToken = login(userId, UUID.randomUUID().toString())[0];

        // 2500 rows is two and a half pages at the 1000-row page size: a single un-paged read
        // would return 1000 and the count would betray it.
        jdbcClient
                .sql(
                        """
                        INSERT INTO coding_sessions
                            (id, user_id, session_uuid, project_name, language, start_time,
                             end_time, client_modified_at, client_version, server_version, is_deleted)
                        SELECT gen_random_uuid(), ?, gen_random_uuid(), 'bulk-' || g, 'Java',
                               timestamptz '2026-10-01 00:00:00+00' + (g * interval '1 minute'),
                               timestamptz '2026-10-01 00:00:30+00' + (g * interval '1 minute'),
                               timestamptz '2026-10-01 00:00:30+00' + (g * interval '1 minute'),
                               0, 0, false
                        FROM generate_series(1, 2500) AS g
                        """)
                .param(userId)
                .update();

        ResponseEntity<String> response = export(accessToken, 480);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode document = objectMapper.readTree(response.getBody());
        assertThat(document.path("totalSessions").asInt()).isEqualTo(2500);
        assertThat(document.path("sessions")).hasSize(2500);
    }

    @Test
    @DisplayName("Should rate limit the fourth export within an hour")
    void shouldRateLimitFourthExport_whenExportingRepeatedly() throws Exception {
        UUID userId = register(userEmail());
        String accessToken = login(userId, UUID.randomUUID().toString())[0];

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(export(accessToken, 480).getStatusCode())
                    .as("export %d of 3", attempt)
                    .isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<String> limited = export(accessToken, 480);

        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(objectMapper.readTree(limited.getBody()).path("code").asText())
                .isEqualTo("RATE_LIMIT_001");
    }

    @Test
    @DisplayName("Should reject an out-of-range timezone offset")
    void shouldRejectOffset_whenOutOfRange() throws Exception {
        UUID userId = register(userEmail());
        String accessToken = login(userId, UUID.randomUUID().toString())[0];

        ResponseEntity<String> response = export(accessToken, 800);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).path("code").asText())
                .isEqualTo("COMMON_003");
    }

    @Test
    @DisplayName("Should require authentication")
    void shouldRequireAuthentication_whenExporting() {
        ResponseEntity<String> response = export(null, 480);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<String> export(String accessToken, int timezoneOffset) {
        return rest.get()
                .uri(
                        "http://localhost:"
                                + port
                                + contextPath
                                + EXPORT_URL
                                + "?timezoneOffset="
                                + timezoneOffset)
                .headers(
                        headers -> {
                            if (accessToken != null) {
                                headers.setBearerAuth(accessToken);
                            }
                        })
                .exchange(
                        (request, response) ->
                                ResponseEntity.status(response.getStatusCode())
                                        .headers(response.getHeaders())
                                        .body(
                                                new String(
                                                        response.getBody().readAllBytes(),
                                                        StandardCharsets.UTF_8)));
    }

    private MirrorSession mirrorSession(ExportMirror mirror, String projectName) {
        return mirror.sessions().stream()
                .filter(session -> projectName.equals(session.projectName()))
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("no exported session for project " + projectName));
    }

    private JsonNode sessionOf(JsonNode document, String projectName) {
        for (JsonNode session : document.path("sessions")) {
            if (projectName.equals(session.path("projectName").asText())) {
                return session;
            }
        }
        throw new AssertionError("no exported session for project " + projectName);
    }

    private void insertSession(
            UUID userId,
            UUID deviceId,
            String projectName,
            String language,
            String startTimeIso,
            boolean deleted) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO coding_sessions
                            (id, user_id, session_uuid, project_name, language, start_time,
                             end_time, client_modified_at, client_version, server_version,
                             origin_device_id, is_deleted)
                        VALUES (?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz + interval '30 minutes',
                                ?::timestamptz, 0, 0, ?, ?)
                        """)
                .params(
                        UUID.randomUUID(),
                        userId,
                        UUID.randomUUID(),
                        projectName,
                        language,
                        startTimeIso,
                        startTimeIso,
                        startTimeIso,
                        deviceId,
                        deleted)
                .update();
    }

    private void registerDevice(UUID userId, UUID deviceId) {
        jdbcClient
                .sql(
                        "INSERT INTO devices (id, user_id, device_name, platform, ide_name) VALUES (?, ?, ?, ?, ?)")
                .params(deviceId, userId, "test-device", "macOS", "IntelliJ IDEA")
                .update();
    }

    private String userEmail() {
        return "export." + UUID.randomUUID() + "@test.example";
    }

    /** Registers and verifies an account, returning its id. */
    private UUID register(String email) throws Exception {
        UserRegisterRequest register =
                new UserRegisterRequest(email, DISPLAY_NAME, PASSWORD, "1.0.0", null);
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(register)))
                .hasStatus(200);

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
        assertThat(mvc.get().uri("/api/v1/auth/verify-email?token=" + matcher.group(1)))
                .hasStatus(200);

        return jdbcClient
                .sql("SELECT id FROM users WHERE email = ?")
                .param(email)
                .query(UUID.class)
                .single();
    }

    /** Returns the tokens a login issues: {@code [accessToken, refreshToken]}. */
    private String[] login(UUID userId, String deviceId) throws Exception {
        String email =
                jdbcClient
                        .sql("SELECT email FROM users WHERE id = ?")
                        .param(userId)
                        .query(String.class)
                        .single();
        LoginRequest request = new LoginRequest(email, PASSWORD, deviceId, null);
        String response =
                mvc.post()
                        .uri("/api/v1/auth/login")
                        .with(csrf())
                        .header("User-Agent", "ctt-test/1.0 (export)")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String accessToken =
                objectMapper.readTree(response).path("data").path("accessToken").asText();
        assertThat(accessToken).as("login failed: %s", response).isNotBlank();
        return new String[] {
            accessToken, objectMapper.readTree(response).path("data").path("refreshToken").asText()
        };
    }

    /** The desktop importer's model, read the way Gson reads it: unknown fields ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExportMirror(
            String exportVersion,
            String exportTime,
            int totalSessions,
            List<MirrorSession> sessions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MirrorSession(
            String sessionUuid,
            String userId,
            String projectName,
            String language,
            String platform,
            String ideName,
            LocalDateTime startTime,
            LocalDateTime endTime,
            LocalDateTime lastModified) {}
}
