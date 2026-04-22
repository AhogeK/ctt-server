package com.ahogek.cttserver.auth.oauth.client;

import com.ahogek.cttserver.auth.oauth.model.GitHubTokenResponse;
import com.ahogek.cttserver.auth.oauth.model.GitHubUserInfo;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.config.properties.SecurityProperties.OAuthProperties.GitHubProperties;
import com.ahogek.cttserver.common.exception.BadGatewayException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubOAuthClientTest {

    private static final String TOKEN_URI = "https://github.com/login/oauth/access_token";
    private static final String USER_INFO_URI = "https://api.github.com/user";
    private static final String USER_EMAILS_URI = "https://api.github.com/user/emails";
    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String ACCESS_TOKEN = "gho_test_token_123";

    private MockRestServiceServer mockServer;
    private GitHubOAuthClient client;

    @BeforeEach
    void setUp() {
        GitHubProperties props =
                new GitHubProperties(
                        CLIENT_ID,
                        CLIENT_SECRET,
                        TOKEN_URI,
                        USER_INFO_URI,
                        USER_EMAILS_URI,
                        "read:user,user:email");
        SecurityProperties.OAuthProperties oauthProps =
                new SecurityProperties.OAuthProperties("https://example.com", "test-key", props);
        SecurityProperties securityProps =
                new SecurityProperties(null, null, null, null, oauthProps);

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        client = new GitHubOAuthClient(securityProps, builder.build());
    }

    @Nested
    @DisplayName("exchangeCodeForToken")
    class ExchangeCodeForToken {

        @Test
        @DisplayName("should exchange code for access token when code is valid")
        void shouldExchangeCodeForToken_whenCodeIsValid() {
            String tokenJson =
                    """
                    {"access_token":"gho_abc123","token_type":"bearer","scope":"read:user,user:email"}
                    """;

            mockServer
                    .expect(requestTo(TOKEN_URI))
                    .andExpect(
                            content()
                                    .json(
                                            """
                            {"client_id":"test-client-id","client_secret":"test-client-secret","code":"auth_code","state":"csrf_state"}
                            """))
                    .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                    .andRespond(withSuccess(tokenJson, MediaType.APPLICATION_JSON));

            GitHubTokenResponse response = client.exchangeCodeForToken("auth_code", "csrf_state");

            assertThat(response.accessToken()).isEqualTo("gho_abc123");
            assertThat(response.tokenType()).isEqualTo("bearer");
            assertThat(response.scope()).isEqualTo("read:user,user:email");
            mockServer.verify();
        }

        @Test
        @DisplayName("should throw BadGatewayException when GitHub returns 400")
        void shouldThrowBadGatewayException_whenGitHubReturns400() {
            mockServer
                    .expect(requestTo(TOKEN_URI))
                    .andRespond(withBadRequest().body("{\"error\":\"bad_verification_code\"}"));

            assertThatThrownBy(() -> client.exchangeCodeForToken("invalid_code", "state"))
                    .isInstanceOf(BadGatewayException.class)
                    .satisfies(
                            thrown -> {
                                BadGatewayException ex = (BadGatewayException) thrown;
                                assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_015);
                            });
        }

        @Test
        @DisplayName("should throw BadGatewayException when GitHub returns 500")
        void shouldThrowBadGatewayException_whenGitHubReturns500() {
            mockServer.expect(requestTo(TOKEN_URI)).andRespond(withServerError());

            assertThatThrownBy(() -> client.exchangeCodeForToken("code", "state"))
                    .isInstanceOf(BadGatewayException.class)
                    .satisfies(
                            thrown -> {
                                BadGatewayException ex = (BadGatewayException) thrown;
                                assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_015);
                            });
        }
    }

    @Nested
    @DisplayName("getUserInfo")
    class GetUserInfo {

        @Test
        @DisplayName("should return user info when email is present in /user response")
        void shouldReturnUserInfo_whenEmailIsPresent() {
            String userInfoJson =
                    """
                    {"id":123456,"login":"octocat","name":"The Octocat","avatar_url":"https://example.com/avatar.png","email":"octocat@github.com"}
                    """;

            mockServer
                    .expect(requestTo(USER_INFO_URI))
                    .andExpect(header("Authorization", "Bearer " + ACCESS_TOKEN))
                    .andRespond(withSuccess(userInfoJson, MediaType.APPLICATION_JSON));

            GitHubUserInfo userInfo = client.getUserInfo(ACCESS_TOKEN);

            assertThat(userInfo.id()).isEqualTo(123456L);
            assertThat(userInfo.login()).isEqualTo("octocat");
            assertThat(userInfo.name()).isEqualTo("The Octocat");
            assertThat(userInfo.avatarUrl()).isEqualTo("https://example.com/avatar.png");
            assertThat(userInfo.email()).isEqualTo("octocat@github.com");
            mockServer.verify();
        }

        @Test
        @DisplayName("should fallback to /user/emails when email is null in /user response")
        void shouldFallbackToUserEmails_whenEmailIsNull() {
            String userInfoJson =
                    """
                    {"id":123456,"login":"octocat","name":"The Octocat","avatar_url":"https://example.com/avatar.png","email":null}
                    """;
            String emailsJson =
                    """
                    [{"email":"private@github.com","primary":true,"verified":true,"visibility":null},
                     {"email":"other@example.com","primary":false,"verified":true,"visibility":"public"}]
                    """;

            mockServer
                    .expect(requestTo(USER_INFO_URI))
                    .andExpect(header("Authorization", "Bearer " + ACCESS_TOKEN))
                    .andRespond(withSuccess(userInfoJson, MediaType.APPLICATION_JSON));

            mockServer
                    .expect(requestTo(USER_EMAILS_URI))
                    .andExpect(header("Authorization", "Bearer " + ACCESS_TOKEN))
                    .andRespond(withSuccess(emailsJson, MediaType.APPLICATION_JSON));

            GitHubUserInfo userInfo = client.getUserInfo(ACCESS_TOKEN);

            assertThat(userInfo.email()).isEqualTo("private@github.com");
            assertThat(userInfo.login()).isEqualTo("octocat");
            mockServer.verify();
        }

        @Test
        @DisplayName("should throw ValidationException when /user/emails returns empty list")
        void shouldThrowValidationException_whenEmailsListIsEmpty() {
            String userInfoJson =
                    """
                    {"id":123456,"login":"octocat","name":"Test","avatar_url":"https://example.com/avatar.png","email":null}
                    """;

            mockServer
                    .expect(requestTo(USER_INFO_URI))
                    .andRespond(withSuccess(userInfoJson, MediaType.APPLICATION_JSON));

            mockServer
                    .expect(requestTo(USER_EMAILS_URI))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.getUserInfo(ACCESS_TOKEN))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(
                            thrown -> {
                                ValidationException ex = (ValidationException) thrown;
                                assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_017);
                            });
        }

        @Test
        @DisplayName("should throw BadGatewayException when /user/emails fails during fallback")
        void shouldThrowBadGatewayException_whenEmailsApiFailsDuringFallback() {
            String userInfoJson =
                    """
                    {"id":123456,"login":"octocat","name":"Test","avatar_url":"https://example.com/avatar.png","email":null}
                    """;

            mockServer
                    .expect(requestTo(USER_INFO_URI))
                    .andRespond(withSuccess(userInfoJson, MediaType.APPLICATION_JSON));

            mockServer.expect(requestTo(USER_EMAILS_URI)).andRespond(withServerError());

            assertThatThrownBy(() -> client.getUserInfo(ACCESS_TOKEN))
                    .isInstanceOf(BadGatewayException.class)
                    .satisfies(
                            thrown -> {
                                BadGatewayException ex = (BadGatewayException) thrown;
                                assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_015);
                            });
        }

        @Test
        @DisplayName("should throw ValidationException when no verified primary email exists")
        void shouldThrowValidationException_whenNoVerifiedPrimaryEmail() {
            String userInfoJson =
                    """
                    {"id":123456,"login":"octocat","name":"Test","avatar_url":"https://example.com/avatar.png","email":null}
                    """;
            String emailsJson =
                    """
                    [{"email":"unverified@example.com","primary":true,"verified":false,"visibility":null}]
                    """;

            mockServer
                    .expect(requestTo(USER_INFO_URI))
                    .andRespond(withSuccess(userInfoJson, MediaType.APPLICATION_JSON));

            mockServer
                    .expect(requestTo(USER_EMAILS_URI))
                    .andRespond(withSuccess(emailsJson, MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.getUserInfo(ACCESS_TOKEN))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(
                            thrown -> {
                                ValidationException ex = (ValidationException) thrown;
                                assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_017);
                            });
        }

        @Test
        @DisplayName("should throw BadGatewayException when /user API fails")
        void shouldThrowBadGatewayException_whenUserApiFails() {
            mockServer.expect(requestTo(USER_INFO_URI)).andRespond(withServerError());

            assertThatThrownBy(() -> client.getUserInfo(ACCESS_TOKEN))
                    .isInstanceOf(BadGatewayException.class)
                    .satisfies(
                            thrown -> {
                                BadGatewayException ex = (BadGatewayException) thrown;
                                assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_015);
                            });
        }
    }

    @Nested
    @DisplayName("getUserEmails via getUserInfo fallback")
    class GetUserEmailsViaFallback {

        @Test
        @DisplayName("should return emails when API succeeds")
        void shouldReturnEmails_whenApiSucceeds() {
            String userInfoJson =
                    """
                    {"id":123456,"login":"octocat","name":"Test","avatar_url":"https://example.com/avatar.png","email":null}
                    """;
            String emailsJson =
                    """
                    [{"email":"primary@github.com","primary":true,"verified":true,"visibility":null},
                     {"email":"public@example.com","primary":false,"verified":true,"visibility":"public"}]
                    """;

            mockServer
                    .expect(requestTo(USER_INFO_URI))
                    .andRespond(withSuccess(userInfoJson, MediaType.APPLICATION_JSON));

            mockServer
                    .expect(requestTo(USER_EMAILS_URI))
                    .andExpect(header("Authorization", "Bearer " + ACCESS_TOKEN))
                    .andRespond(withSuccess(emailsJson, MediaType.APPLICATION_JSON));

            GitHubUserInfo userInfo = client.getUserInfo(ACCESS_TOKEN);

            assertThat(userInfo.email()).isEqualTo("primary@github.com");
            mockServer.verify();
        }

        @Test
        @DisplayName("should throw BadGatewayException when emails API fails")
        void shouldThrowBadGatewayException_whenEmailsApiFails() {
            String userInfoJson =
                    """
                    {"id":123456,"login":"octocat","name":"Test","avatar_url":"https://example.com/avatar.png","email":null}
                    """;

            mockServer
                    .expect(requestTo(USER_INFO_URI))
                    .andRespond(withSuccess(userInfoJson, MediaType.APPLICATION_JSON));

            mockServer.expect(requestTo(USER_EMAILS_URI)).andRespond(withServerError());

            assertThatThrownBy(() -> client.getUserInfo(ACCESS_TOKEN))
                    .isInstanceOf(BadGatewayException.class)
                    .satisfies(
                            thrown -> {
                                BadGatewayException ex = (BadGatewayException) thrown;
                                assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_015);
                            });
        }
    }
}
