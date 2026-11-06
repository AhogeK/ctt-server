package com.ahogek.cttserver.common;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Proves the response-compression configuration negotiates instead of forcing.
 *
 * <p>The probe is the point: the JetBrains plugin talks to this server through the JDK {@link
 * HttpClient}, which sends no {@code Accept-Encoding} and does not decompress. If compression were
 * unconditional, the plugin would receive gzip bytes and try to parse them as JSON. This test
 * drives a real embedded Tomcat (not MockMvc, which bypasses the servlet container's compression)
 * with exactly that client, then repeats the call as a browser would.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-05
 */
@BaseIntegrationTest
@DisplayName("Response compression negotiation")
class ResponseCompressionProbeTest {

    private static final String PASSWORD = "StrongPass123!";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;
    @LocalServerPort private int port;

    @Test
    @DisplayName("Should leave an Accept-Encodingless client uncompressed and gzip a browser")
    void shouldNegotiateCompression_perRequest() throws Exception {
        String jwt = registerAndLogin("compress-" + UUID.randomUUID() + "@example.com");
        URI uri =
                URI.create(
                        "http://127.0.0.1:"
                                + port
                                + "/ctt-server/api/v1/leaderboard/languages?includeEmpty=true");

        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        HttpRequest.Builder base =
                HttpRequest.newBuilder(uri)
                        .header("Authorization", "Bearer " + jwt)
                        .header("Accept", "application/json");

        HttpResponse<String> plain =
                client.send(base.GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(plain.statusCode()).isEqualTo(200);
        // A response below the configured minimum would make the gzip assertion vacuous, so the
        // precondition is asserted rather than assumed.
        assertThat(plain.body().length()).isGreaterThan(2048);
        assertThat(plain.headers().firstValue("Content-Encoding"))
                .as("an Accept-Encodingless client must receive identity encoding")
                .isEmpty();

        HttpResponse<byte[]> compressed =
                client.send(
                        HttpRequest.newBuilder(uri)
                                .header("Authorization", "Bearer " + jwt)
                                .header("Accept", "application/json")
                                .header("Accept-Encoding", "gzip")
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofByteArray());

        assertThat(compressed.statusCode()).isEqualTo(200);
        assertThat(compressed.headers().firstValue("Content-Encoding")).hasValue("gzip");

        String decompressed;
        try (GZIPInputStream gzip =
                new GZIPInputStream(new ByteArrayInputStream(compressed.body()))) {
            decompressed = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Byte-for-byte equality would be flaky: hasMembers reads a Redis set that the scheduled
        // recompute drain can touch between the two calls. The probe must show that the gzip body
        // is the same document and that it really is smaller, not that two calls are identical.
        assertThat(objectMapper.readTree(decompressed).path("success").asBoolean()).isTrue();
        assertThat(objectMapper.readTree(decompressed).path("data").path("languages").size())
                .isEqualTo(
                        objectMapper.readTree(plain.body()).path("data").path("languages").size());
        assertThat(compressed.body().length).isLessThan(plain.body().length());
    }

    private String registerAndLogin(String email) throws Exception {
        UserRegisterRequest request =
                new UserRegisterRequest(email, "CompressionProbe", PASSWORD, "1.0.0", null);
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

        LoginRequest login = new LoginRequest(email, PASSWORD, "compression-probe", null);
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
        return accessToken;
    }
}
