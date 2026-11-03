package com.ahogek.cttserver.auth.passkey;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.auth.twofactor.service.TotpService;
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

import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@BaseIntegrationTest
@TestPropertySource(properties = {"ctt.mail.outbox.poll-interval-ms=999999999"})
@DisplayName("Passkey end-to-end")
class PasskeyIntegrationTest {

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "PasskeyUser";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TotpService totpService;

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
    @DisplayName("Should register a passkey and sign in with it")
    void shouldRegisterAndSignIn_whenCredentialIsValid() throws Exception {
        String email = register();
        String accessToken = login(email);

        JsonNode options = body(options(accessToken));
        PasskeyTestAuthenticator.Registration registration =
                authenticator.register(options, userId(email));

        JsonNode created =
                createdBody(
                        mvc.post()
                                .uri("/api/v1/users/me/passkey")
                                .with(csrf())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationRequest(registration))
                                .exchange());
        assertThat(created.path("name").asText()).isEqualTo("Integration Passkey");
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM user_passkeys").query(Long.class).single())
                .isEqualTo(1);
        assertThat(
                        jdbcClient
                                .sql(
                                        "SELECT COUNT(*) FROM mail_outbox"
                                                + " WHERE recipient = ? AND biz_type = 'PASSKEY_ADDED'")
                                .param(email)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);

        JsonNode assertionOptions = body(anonymousOptions());
        String assertion = authenticator.assertCredential(assertionOptions, registration, 1);
        MvcTestResult loginResult =
                mvc.post()
                        .uri("/api/v1/auth/passkey/assert")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assertionRequest(assertion))
                        .exchange();
        assertThat(loginResult).hasStatusOk();
        JsonNode tokens = body(loginResult);
        assertThat(tokens.path("accessToken").asText()).isNotBlank();
        assertThat(tokens.path("refreshToken").asText()).isNotBlank();
        assertThat(loginResult.getResponse().getHeaders("Set-Cookie")).isNotEmpty();
        assertThat(loginResult.getResponse().getHeader("Authorization")).startsWith("Bearer ");

        assertThat(
                        jdbcClient
                                .sql("SELECT sign_count FROM user_passkeys")
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Should list, rename and delete a passkey")
    void shouldManageCredential_whenSessionIsRecent() throws Exception {
        String email = register();
        String accessToken = login(email);
        PasskeyTestAuthenticator.Registration registration =
                authenticator.register(body(options(accessToken)), userId(email));
        createdBody(createPasskey(accessToken, registration));

        JsonNode list = body(managementGet(accessToken));
        assertThat(list).hasSize(1);
        String id = list.get(0).path("id").asText();

        JsonNode renamed =
                body(
                        mvc.patch()
                                .uri("/api/v1/users/me/passkey/" + id)
                                .with(csrf())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Renamed passkey\"}")
                                .exchange());
        assertThat(renamed.path("name").asText()).isEqualTo("Renamed passkey");

        assertThat(
                        mvc.delete()
                                .uri("/api/v1/users/me/passkey/" + id)
                                .with(csrf())
                                .header("Authorization", "Bearer " + accessToken)
                                .exchange())
                .hasStatus(204);
        assertThat(body(managementGet(accessToken))).isEmpty();
    }

    @Test
    @DisplayName("Should reject an assertion with the wrong signature")
    void shouldRejectAssertion_whenSignatureIsWrong() throws Exception {
        String email = register();
        String accessToken = login(email);
        PasskeyTestAuthenticator.Registration registration =
                authenticator.register(body(options(accessToken)), userId(email));
        createdBody(createPasskey(accessToken, registration));
        JsonNode assertionOptions = body(anonymousOptions());

        ObjectNode assertion =
                (ObjectNode)
                        objectMapper.readTree(
                                authenticator.assertCredential(assertionOptions, registration, 1));
        ((ObjectNode) assertion.path("response")).put("signature", "AA");
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/auth/passkey/assert")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assertionRequest(assertion.toString()))
                        .exchange();
        assertThat(result).hasStatus(401).bodyJson().extractingPath("$.code").isEqualTo("AUTH_033");
    }

    @Test
    @DisplayName("Should reject an assertion without user verification")
    void shouldRejectAssertion_whenUserVerificationIsMissing() throws Exception {
        String email = register();
        String accessToken = login(email);
        PasskeyTestAuthenticator.Registration registration =
                authenticator.register(body(options(accessToken)), userId(email));
        createdBody(createPasskey(accessToken, registration));
        JsonNode assertionOptions = body(anonymousOptions());
        String assertion = authenticator.assertCredential(assertionOptions, registration, 1, false);

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

    @Test
    @DisplayName("Should reject registration when the origin is not allowed")
    void shouldRejectRegistration_whenOriginIsWrong() throws Exception {
        String email = register();
        String accessToken = login(email);
        JsonNode options = body(options(accessToken));
        PasskeyTestAuthenticator wrongOrigin =
                new PasskeyTestAuthenticator("localhost", "http://evil.example");
        PasskeyTestAuthenticator.Registration registration =
                wrongOrigin.register(options, userId(email));

        assertThat(
                        mvc.post()
                                .uri("/api/v1/users/me/passkey")
                                .with(csrf())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationRequest(registration))
                                .exchange())
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_033");
    }

    @Test
    @DisplayName("Should complete the second factor with a passkey")
    void shouldCompleteSecondFactor_whenPasskeyAssertionIsValid() throws Exception {
        String email = register();
        String accessToken = login(email);
        PasskeyTestAuthenticator.Registration registration =
                authenticator.register(body(options(accessToken)), userId(email));
        createdBody(createPasskey(accessToken, registration));
        enableTwoFactor(accessToken);

        JsonNode challenge = challenge(email);
        assertThat(challenge.path("passkeyCount").asLong()).isEqualTo(1);

        JsonNode assertionOptions =
                body(secondFactorOptions(challenge.path("challengeToken").asText()));
        JsonNode allowed = assertionOptions.path("publicKey").path("allowCredentials");
        assertThat(allowed.size()).isEqualTo(1);
        assertThat(allowed.get(0).path("id").asText())
                .isEqualTo(
                        Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(registration.credentialId()));

        String assertion = authenticator.assertCredential(assertionOptions, registration, 1);
        MvcTestResult completed =
                secondFactorAssert(challenge.path("challengeToken").asText(), assertion);
        assertThat(completed).hasStatusOk();
        JsonNode tokens = body(completed);
        assertThat(tokens.path("accessToken").asText()).isNotBlank();
        assertThat(completed.getResponse().getHeaders("Set-Cookie")).isNotEmpty();

        assertThat(secondFactorAssert(challenge.path("challengeToken").asText(), assertion))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_026");
    }

    @Test
    @DisplayName("Should keep TOTP usable after a rejected passkey attempt")
    void shouldKeepTotpFallback_whenPasskeyAssertionFails() throws Exception {
        String email = register();
        String accessToken = login(email);
        PasskeyTestAuthenticator.Registration registration =
                authenticator.register(body(options(accessToken)), userId(email));
        createdBody(createPasskey(accessToken, registration));
        String secret = enableTwoFactor(accessToken);

        JsonNode challenge = challenge(email);
        JsonNode assertionOptions =
                body(secondFactorOptions(challenge.path("challengeToken").asText()));
        ObjectNode assertion =
                (ObjectNode)
                        objectMapper.readTree(
                                authenticator.assertCredential(assertionOptions, registration, 1));
        ((ObjectNode) assertion.path("response")).put("signature", "AA");

        assertThat(
                        secondFactorAssert(
                                challenge.path("challengeToken").asText(), assertion.toString()))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_033");

        assertThat(
                        secondFactorAssert(
                                challenge.path("challengeToken").asText(), assertion.toString()))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_032");

        String code = totpService.codeAt(secret, totpService.currentStep() + 1);
        assertThat(secondStep(challenge.path("challengeToken").asText(), code))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.accessToken")
                .isNotEmpty();
    }

    @Test
    @DisplayName("Should expose no passkey option when the account has none")
    void shouldReturnZeroPasskeyCount_whenNoPasskeyIsRegistered() throws Exception {
        String email = register();
        String accessToken = login(email);
        enableTwoFactor(accessToken);

        JsonNode challenge = challenge(email);
        assertThat(challenge.path("passkeyCount").asLong()).isZero();
        assertThat(secondFactorOptions(challenge.path("challengeToken").asText()))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_033");
    }

    @Test
    @DisplayName("Should reject a credential owned by another account")
    void shouldRejectCredential_whenItBelongsToAnotherAccount() throws Exception {
        String emailA = register();
        String accessTokenA = login(emailA);
        PasskeyTestAuthenticator.Registration registrationA =
                authenticator.register(body(options(accessTokenA)), userId(emailA));
        createdBody(createPasskey(accessTokenA, registrationA));

        String emailB = register();
        String accessTokenB = login(emailB);
        PasskeyTestAuthenticator.Registration registrationB =
                authenticator.register(body(options(accessTokenB)), userId(emailB));
        createdBody(createPasskey(accessTokenB, registrationB));

        enableTwoFactor(accessTokenA);
        JsonNode challenge = challenge(emailA);
        JsonNode assertionOptions =
                body(secondFactorOptions(challenge.path("challengeToken").asText()));
        String assertion = authenticator.assertCredential(assertionOptions, registrationB, 1);

        assertThat(secondFactorAssert(challenge.path("challengeToken").asText(), assertion))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_033");
    }

    @Test
    @DisplayName("Should reject a duplicate credential")
    void shouldRejectDuplicate_whenCredentialIsRegisteredAgain() throws Exception {
        String email = register();
        String accessToken = login(email);
        PasskeyTestAuthenticator.Registration registration =
                authenticator.register(body(options(accessToken)), userId(email));
        createdBody(createPasskey(accessToken, registration));

        JsonNode secondOptions = body(options(accessToken));
        PasskeyTestAuthenticator.Registration duplicate =
                authenticator.reRegister(secondOptions, registration);

        assertThat(
                        mvc.post()
                                .uri("/api/v1/users/me/passkey")
                                .with(csrf())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationRequest(duplicate))
                                .exchange())
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_034");
    }

    @Test
    @DisplayName("Should reject deleting the only login credential")
    void shouldRejectDelete_whenLastCredentialIsTheOnlyLoginPath() throws Exception {
        String email = register();
        String accessToken = login(email);
        PasskeyTestAuthenticator.Registration registration =
                authenticator.register(body(options(accessToken)), userId(email));
        createdBody(createPasskey(accessToken, registration));
        String id = body(managementGet(accessToken)).get(0).path("id").asText();

        jdbcClient
                .sql("UPDATE users SET password_hash = NULL WHERE id = ?")
                .param(userId(email))
                .update();

        assertThat(
                        mvc.delete()
                                .uri("/api/v1/users/me/passkey/" + id)
                                .with(csrf())
                                .header("Authorization", "Bearer " + accessToken)
                                .exchange())
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_018");
    }

    @Test
    @DisplayName("Should reject a non-increasing signature counter")
    void shouldRejectAssertion_whenCounterDoesNotIncrease() throws Exception {
        String email = register();
        String accessToken = login(email);
        PasskeyTestAuthenticator.Registration registration =
                authenticator.register(body(options(accessToken)), userId(email));
        createdBody(createPasskey(accessToken, registration));

        JsonNode firstOptions = body(anonymousOptions());
        String firstAssertion = authenticator.assertCredential(firstOptions, registration, 1);
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/passkey/assert")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(assertionRequest(firstAssertion))
                                .exchange())
                .hasStatusOk();

        JsonNode secondOptions = body(anonymousOptions());
        String secondAssertion = authenticator.assertCredential(secondOptions, registration, 1);
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/passkey/assert")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(assertionRequest(secondAssertion))
                                .exchange())
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_033");
    }

    @Test
    @DisplayName("Should keep registration working when the notification rate limit is hit")
    void shouldKeepRegistration_whenNotificationIsRateLimited() throws Exception {
        String email = register();
        String accessToken = login(email);

        for (int attempt = 1; attempt <= 4; attempt++) {
            PasskeyTestAuthenticator.Registration registration =
                    authenticator.register(body(options(accessToken)), userId(email));
            createdBody(createPasskey(accessToken, registration));
        }

        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM user_passkeys").query(Long.class).single())
                .isEqualTo(4);
        assertThat(
                        jdbcClient
                                .sql(
                                        "SELECT COUNT(*) FROM mail_outbox"
                                                + " WHERE recipient = ? AND biz_type = 'PASSKEY_ADDED'")
                                .param(email)
                                .query(Long.class)
                                .single())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("Should reject API-key callers on passkey management")
    void shouldRejectApiKey_whenManagingPasskeys() throws Exception {
        String email = register();
        String accessToken = login(email);
        String rawKey = createApiKey(accessToken);

        assertThat(
                        mvc.get()
                                .uri("/api/v1/users/me/passkey")
                                .header("Authorization", "Bearer " + rawKey)
                                .exchange())
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_025");
    }

    private JsonNode challenge(String email) throws Exception {
        LoginRequest request =
                new LoginRequest(email, PASSWORD, UUID.randomUUID().toString(), null);
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/auth/login")
                        .with(csrf())
                        .header("User-Agent", "ctt-test/1.0 (passkey-2fa)")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .exchange();
        assertThat(result).hasStatus(202);
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private MvcTestResult secondFactorOptions(String challengeToken) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("challengeToken", challengeToken);
        return mvc.post()
                .uri("/api/v1/auth/2fa/passkey/options")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .exchange();
    }

    private MvcTestResult secondFactorAssert(String challengeToken, String assertion)
            throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("challengeToken", challengeToken);
        request.set("credential", objectMapper.readTree(assertion));
        return mvc.post()
                .uri("/api/v1/auth/2fa/passkey/assert")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .exchange();
    }

    private MvcTestResult secondStep(String challengeToken, String code) {
        return mvc.post()
                .uri("/api/v1/auth/login/2fa")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"challengeToken\":\"" + challengeToken + "\",\"code\":\"" + code + "\"}")
                .exchange();
    }

    private String enableTwoFactor(String accessToken) throws Exception {
        JsonNode started =
                body(
                        mvc.post()
                                .uri("/api/v1/users/me/2fa/totp/start")
                                .with(csrf())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"" + PASSWORD + "\"}")
                                .exchange());
        String secret = started.path("secret").asText();
        String code = totpService.codeAt(secret, totpService.currentStep());
        assertThat(
                        mvc.post()
                                .uri("/api/v1/users/me/2fa/totp/confirm")
                                .with(csrf())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"" + code + "\"}")
                                .exchange())
                .hasStatusOk();
        return secret;
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

    private MvcTestResult managementGet(String accessToken) {
        return mvc.get()
                .uri("/api/v1/users/me/passkey")
                .header("Authorization", "Bearer " + accessToken)
                .exchange();
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
        request.put("name", "Integration Passkey");
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
        String email = "passkey." + UUID.randomUUID() + "@test.example";
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
                        .header("User-Agent", "ctt-test/1.0 (passkey)")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .exchange();
        assertThat(result).hasStatusOk();
        return body(result).path("accessToken").asText();
    }

    private String createApiKey(String accessToken) throws Exception {
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/auth/api-keys")
                        .with(csrf())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"passkey-test\",\"scopes\":[\"READ\"]}")
                        .exchange();
        assertThat(result).hasStatus(201);
        return createdBody(result).path("rawKey").asText();
    }

    private UUID userId(String email) {
        return jdbcClient
                .sql("SELECT id FROM users WHERE email = ?")
                .param(email)
                .query(UUID.class)
                .single();
    }
}
