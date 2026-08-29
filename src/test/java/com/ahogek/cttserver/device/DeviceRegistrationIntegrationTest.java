package com.ahogek.cttserver.device;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * End-to-end integration tests for device registration.
 *
 * <p>Verifies the sync prerequisite contract: a plugin registers its device with a SYNC-scoped API
 * key, the device becomes visible to the key's owner, subsequent pull/push pass the ownership
 * check, ownership conflicts are rejected, and the authenticating key is bound to the device.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-28
 */
@BaseIntegrationTest
@TestPropertySource(properties = {"ctt.mail.outbox.poll-interval-ms=999999999"})
@DisplayName("Device Registration Integration Tests")
class DeviceRegistrationIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "DeviceTestUser";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

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
        jdbcClient.sql("DELETE FROM audit_logs").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    private String uniqueEmail() {
        return "device-test." + UUID.randomUUID() + "@test.example";
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

    private String registerVerifyAndLogin(String email) throws Exception {
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

    private String registerDeviceBody(UUID deviceId) {
        return """
                {
                  "deviceId": "%s",
                  "deviceName": "MacBook Pro",
                  "platform": "macOS",
                  "ideName": "IntelliJ IDEA",
                  "ideVersion": "2026.1",
                  "appVersion": "1.2.0"
                }
                """
                .formatted(deviceId);
    }

    private String pullBody(UUID deviceId) {
        return """
                {"deviceId": "%s", "lastPulledChangeId": 0}
                """
                .formatted(deviceId);
    }

    private int countKeysBoundToDevice(UUID deviceId) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM api_keys WHERE device_id = ?")
                .param(deviceId)
                .query(Integer.class)
                .single();
    }

    @Nested
    @DisplayName("POST /api/v1/devices")
    class RegisterDeviceTests {

        @Test
        @DisplayName("Should register device with SYNC key, expose it via GET, and bind the key")
        void shouldRegisterDevice_whenSyncKey() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            String rawKey = createApiKey(jwt, "Sync Key", "SYNC");
            UUID deviceId = UUID.randomUUID();

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/devices")
                                    .header("Authorization", "Bearer " + rawKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(registerDeviceBody(deviceId))
                                    .exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.data.id")
                    .isEqualTo(deviceId.toString());

            assertThat(
                            mvc.get()
                                    .uri("/api/v1/devices")
                                    .header("Authorization", "Bearer " + rawKey)
                                    .exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.data[0].id")
                    .isEqualTo(deviceId.toString());

            assertThat(countKeysBoundToDevice(deviceId)).isEqualTo(1);
        }

        @Test
        @DisplayName("Should allow pull after registration (no COMMON_002)")
        void shouldAllowPull_afterRegistration() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            String rawKey = createApiKey(jwt, "Sync Key", "SYNC");
            UUID deviceId = UUID.randomUUID();

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/devices")
                                    .header("Authorization", "Bearer " + rawKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(registerDeviceBody(deviceId))
                                    .exchange())
                    .hasStatusOk();

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/sync/pull")
                                    .header("Authorization", "Bearer " + rawKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(pullBody(deviceId))
                                    .exchange())
                    .hasStatusOk();
        }

        @Test
        @DisplayName("Should return 409 DEVICE_001 when device belongs to another user")
        void shouldReturn409_whenDeviceOwnedByAnotherUser() throws Exception {
            String ownerEmail = uniqueEmail();
            String ownerJwt = registerVerifyAndLogin(ownerEmail);
            String ownerKey = createApiKey(ownerJwt, "Owner Key", "SYNC");
            UUID deviceId = UUID.randomUUID();

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/devices")
                                    .header("Authorization", "Bearer " + ownerKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(registerDeviceBody(deviceId))
                                    .exchange())
                    .hasStatusOk();

            String otherEmail = uniqueEmail();
            String otherJwt = registerVerifyAndLogin(otherEmail);
            String otherKey = createApiKey(otherJwt, "Other Key", "SYNC");

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/devices")
                                    .header("Authorization", "Bearer " + otherKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(registerDeviceBody(deviceId))
                                    .exchange())
                    .hasStatus(409)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("DEVICE_001");
        }

        @Test
        @DisplayName("Should register device when authenticated with JWT")
        void shouldRegisterDevice_whenJwt() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            UUID deviceId = UUID.randomUUID();

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/devices")
                                    .header("Authorization", "Bearer " + jwt)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(registerDeviceBody(deviceId))
                                    .exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.data.id")
                    .isEqualTo(deviceId.toString());
        }

        @Test
        @DisplayName("Should return 403 AUTH_020 when API key lacks SYNC scope")
        void shouldReturn403_whenKeyLacksSyncScope() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            String readKey = createApiKey(jwt, "Read Key", "READ");
            UUID deviceId = UUID.randomUUID();

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/devices")
                                    .header("Authorization", "Bearer " + readKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(registerDeviceBody(deviceId))
                                    .exchange())
                    .hasStatus(403)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_020");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/devices - scope")
    class ListDeviceScopeTests {

        @Test
        @DisplayName("Should return 200 when API key has SYNC scope")
        void shouldReturn200_whenSyncKey() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            String syncKey = createApiKey(jwt, "Sync Key", "SYNC");

            assertThat(
                            mvc.get()
                                    .uri("/api/v1/devices")
                                    .header("Authorization", "Bearer " + syncKey)
                                    .exchange())
                    .hasStatusOk();
        }

        @Test
        @DisplayName("Should return 200 when API key has READ scope only (any-of semantics)")
        void shouldReturn200_whenReadOnlyKey() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            String readKey = createApiKey(jwt, "Read Key", "READ");

            assertThat(
                            mvc.get()
                                    .uri("/api/v1/devices")
                                    .header("Authorization", "Bearer " + readKey)
                                    .exchange())
                    .hasStatusOk();
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/devices - revocation status")
    class RevokeDeviceTests {

        @Test
        @DisplayName("Should expose revokedAt after device revocation")
        void shouldExposeRevokedAt_afterRevocation() throws Exception {
            String email = uniqueEmail();
            String jwt = registerVerifyAndLogin(email);
            UUID deviceId = UUID.randomUUID();

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/devices")
                                    .header("Authorization", "Bearer " + jwt)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(registerDeviceBody(deviceId))
                                    .exchange())
                    .hasStatusOk();

            assertThat(
                            mvc.delete()
                                    .uri("/api/v1/devices/{deviceId}", deviceId.toString())
                                    .header("Authorization", "Bearer " + jwt)
                                    .exchange())
                    .hasStatusOk();

            assertThat(
                            mvc.get()
                                    .uri("/api/v1/devices")
                                    .header("Authorization", "Bearer " + jwt)
                                    .exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.data[0].revokedAt")
                    .isNotNull();
        }
    }
}
