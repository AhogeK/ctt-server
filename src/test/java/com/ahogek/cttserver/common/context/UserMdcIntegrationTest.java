package com.ahogek.cttserver.common.context;

import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.config.properties.TermsProperties;
import com.ahogek.cttserver.user.enums.UserStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * End-to-end checks for the user dimension of the logging context.
 *
 * <p>Asserts the observable contracts: a line logged while an authenticated user is on the thread
 * carries that user's identifier, a rejection raised after authentication keeps it, and no key
 * survives the response — the next request on the same thread must not inherit the previous user.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-14
 */
@BaseIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("User MDC integration")
class UserMdcIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Autowired private MockMvcTester mvc;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private SecurityProperties securityProperties;
    @Autowired private TermsProperties termsProperties;

    @Test
    void shouldLogTheUserId_whenTheRequestIsAuthenticated(CapturedOutput output) {
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                                .with(authentication(authenticatedUser())))
                .hasStatus(400);

        assertThat(output).contains("[U:" + USER_ID + "]");
        assertThat(MDC.get(MdcKey.USER_ID)).isNull();
    }

    @Test
    void shouldNotCarryThePreviousUserId_whenTheNextRequestIsAnonymous(CapturedOutput output) {
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                                .with(authentication(authenticatedUser())))
                .hasStatus(400);

        int afterFirstRequest = output.getOut().length();

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .hasStatus(400);

        assertThat(output.getOut().substring(afterFirstRequest))
                .contains("[U:anonymous]")
                .doesNotContain(USER_ID.toString());
        assertThat(MDC.get(MdcKey.USER_ID)).isNull();
    }

    @Test
    void shouldKeepTheUserId_whenTheTermsGateRejectsTheRequest(CapturedOutput output) {
        assertThat(
                        mvc.get()
                                .uri("/api/v1/users/me")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + tokenWithOutdatedTerms())
                                .exchange())
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.data.code")
                .isEqualTo("AUTH_019");

        assertThat(output)
                .contains("[U:" + USER_ID + "] - Terms version mismatch for user")
                .contains(
                        "[U:"
                                + USER_ID
                                + "] - HTTP error response: GET /api/v1/users/me returned 403")
                .contains("[U:" + USER_ID + "] - GET /api/v1/users/me 403");
        assertThat(MDC.get(MdcKey.USER_ID)).isNull();
    }

    private String tokenWithOutdatedTerms() {
        Instant now = Instant.now();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(securityProperties.jwt().issuer())
                        .issuedAt(now)
                        .expiresAt(now.plus(Duration.ofMinutes(5)))
                        .subject(USER_ID.toString())
                        .claim("email", "mdc.integration@test.example")
                        .claim("status", UserStatus.ACTIVE.name())
                        .claim("authorities", "ROLE_USER")
                        .claim("termsVersion", termsProperties.currentVersion() + "-outdated")
                        .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static Authentication authenticatedUser() {
        CurrentUser user =
                new CurrentUser(
                        USER_ID,
                        "mdc.integration@test.example",
                        UserStatus.ACTIVE,
                        Set.of("SCOPE_READ"),
                        CurrentUser.AuthenticationType.WEB_SESSION);
        return new UsernamePasswordAuthenticationToken(user, null, List.of());
    }
}
