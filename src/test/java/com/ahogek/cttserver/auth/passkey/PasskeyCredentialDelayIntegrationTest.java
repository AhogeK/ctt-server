package com.ahogek.cttserver.auth.passkey;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@BaseIntegrationTest
@TestPropertySource(
        properties = {
            "ctt.mail.outbox.poll-interval-ms=999999999",
            "ctt.security.passkey.new-credential-delay=1h"
        })
@DisplayName("Passkey new-credential delay")
class PasskeyCredentialDelayIntegrationTest {

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "PasskeyDelayUser";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;

    private final PasskeyTestAuthenticator authenticator =
            new PasskeyTestAuthenticator("localhost", "http://localhost:5173");

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM user_passkeys").update();
        jdbcClient.sql("DELETE FROM devices").update();
        jdbcClient.sql("DELETE FROM mail_outbox").update();
        jdbcClient.sql("DELETE FROM email_verification_tokens").update();
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM audit_logs").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    @Test
    @DisplayName("Should reject an assertion before the configured delay elapses")
    void shouldRejectAssertion_whenCredentialIsYoungerThanDelay() throws Exception {
        String email = register();
        String accessToken = login(email);
        PasskeyTestAuthenticator.Registration registration =
                authenticator.register(body(options(accessToken)), userId(email));
        createdBody(createPasskey(accessToken, registration));

        JsonNode assertionOptions = body(anonymousOptions());
        String assertion = authenticator.assertCredential(assertionOptions, registration, 1);

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/passkey/assert")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(assertionRequest(assertion))
                                .exchange())
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_033");
    }

    private MvcTestResult options(String accessToken) {
        return mvc.post()
                .uri("/api/v1/users/me/passkey/options")
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken)
                .exchange();
    }

    private MvcTestResult anonymousOptions() {
        return mvc.post().uri("/api/v1/auth/passkey/options").with(csrf()).exchange();
    }

    private MvcTestResult createPasskey(
            String accessToken, PasskeyTestAuthenticator.Registration registration)
            throws Exception {
        return mvc.post()
                .uri("/api/v1/users/me/passkey")
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationRequest(registration))
                .exchange();
    }

    private String registrationRequest(PasskeyTestAuthenticator.Registration registration)
            throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.set("credential", registration.responseJson());
        request.put("name", "Delay Passkey");
        return objectMapper.writeValueAsString(request);
    }

    private String assertionRequest(String assertion) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.set("credential", objectMapper.readTree(assertion));
        return objectMapper.writeValueAsString(request);
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        assertThat(result).hasStatusOk();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode createdBody(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(201);
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String register() throws Exception {
        String email = "passkey-delay." + UUID.randomUUID() + "@test.example";
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
                                "SELECT body_html FROM mail_outbox WHERE recipient = ?"
                                        + " ORDER BY created_at DESC LIMIT 1")
                        .param(email)
                        .query(String.class)
                        .single();
        Matcher matcher = TOKEN_PATTERN.matcher(bodyHtml);
        assertThat(matcher.find()).as("verification token missing for %s", email).isTrue();
        assertThat(mvc.get().uri("/api/v1/auth/verify-email?token=" + matcher.group(1)))
                .hasStatus(200);
        return email;
    }

    private String login(String email) throws Exception {
        LoginRequest request =
                new LoginRequest(email, PASSWORD, UUID.randomUUID().toString(), null);
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/auth/login")
                        .with(csrf())
                        .header("User-Agent", "ctt-test/1.0 (passkey-delay)")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .exchange();
        assertThat(result).hasStatusOk();
        return body(result).path("accessToken").asText();
    }

    private UUID userId(String email) {
        return jdbcClient
                .sql("SELECT id FROM users WHERE email = ?")
                .param(email)
                .query(UUID.class)
                .single();
    }
}
