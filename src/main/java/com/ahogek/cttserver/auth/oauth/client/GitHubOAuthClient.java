package com.ahogek.cttserver.auth.oauth.client;

import com.ahogek.cttserver.auth.oauth.model.GitHubEmail;
import com.ahogek.cttserver.auth.oauth.model.GitHubTokenResponse;
import com.ahogek.cttserver.auth.oauth.model.GitHubUserInfo;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.config.properties.SecurityProperties.OAuthProperties.GitHubProperties;
import com.ahogek.cttserver.common.exception.BadGatewayException;
import com.ahogek.cttserver.common.exception.BusinessException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ValidationException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * GitHub OAuth API client using Spring RestClient.
 *
 * <p>Handles token exchange, user info retrieval, and email fallback logic for private emails.
 */
@Component
public class GitHubOAuthClient {

    private final RestClient restClient;
    private final GitHubProperties props;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    public GitHubOAuthClient(SecurityProperties securityProps) {
        this.props = securityProps.oauth().github();
        this.restClient =
                RestClient.builder()
                        .requestFactory(
                                new JdkClientHttpRequestFactory(
                                        HttpClient.newBuilder()
                                                .connectTimeout(CONNECT_TIMEOUT)
                                                .build()))
                        .build();
    }

    GitHubOAuthClient(SecurityProperties securityProps, RestClient restClient) {
        this.props = securityProps.oauth().github();
        this.restClient = restClient;
    }

    /**
     * Exchanges an authorization code for an access token.
     *
     * @param code the authorization code from GitHub callback
     * @param state the state parameter (validated by caller before invoking this method)
     * @return the token response containing access_token
     * @throws BusinessException if GitHub returns an error
     */
    public GitHubTokenResponse exchangeCodeForToken(String code, String state) {
        return restClient
                .post()
                .uri(props.tokenUri())
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        Map.of(
                                "client_id",
                                props.clientId(),
                                "client_secret",
                                props.clientSecret(),
                                "code",
                                code,
                                "state",
                                state))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        (_, _) -> {
                            throw new BadGatewayException(
                                    ErrorCode.AUTH_015, "GitHub OAuth code exchange failed");
                        })
                .onStatus(
                        HttpStatusCode::is5xxServerError,
                        (_, _) -> {
                            throw new BadGatewayException(
                                    ErrorCode.AUTH_015, "GitHub OAuth server error");
                        })
                .body(GitHubTokenResponse.class);
    }

    /**
     * Fetches user info from GitHub, with email fallback for private emails.
     *
     * <p>If the user's primary email is not public (null in /user response), calls /user/emails to
     * find the primary verified email.
     *
     * @param accessToken the GitHub access token
     * @return user info with resolved email
     * @throws BusinessException if no verified primary email can be found
     */
    public GitHubUserInfo getUserInfo(String accessToken) {
        GitHubUserInfo userInfo = fetchBasicInfo(accessToken);
        if (userInfo != null && userInfo.email() == null) {
            String primaryEmail = fetchPrimaryEmail(accessToken);
            if (primaryEmail != null) {
                userInfo = userInfo.withEmail(primaryEmail);
            }
        }
        return userInfo;
    }

    private List<GitHubEmail> getUserEmails(String accessToken) {
        return restClient
                .get()
                .uri(props.userEmailsUri())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        (_, _) -> {
                            throw new BadGatewayException(
                                    ErrorCode.AUTH_015, "GitHub user emails API failed");
                        })
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private GitHubUserInfo fetchBasicInfo(String accessToken) {
        return restClient
                .get()
                .uri(props.userInfoUri())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        (_, _) -> {
                            throw new BadGatewayException(
                                    ErrorCode.AUTH_015, "GitHub user info API failed");
                        })
                .body(GitHubUserInfo.class);
    }

    private String fetchPrimaryEmail(String accessToken) {
        List<GitHubEmail> emails = getUserEmails(accessToken);
        if (emails == null) {
            return null;
        }

        return emails.stream()
                .filter(e -> e.verified() && e.primary())
                .map(GitHubEmail::email)
                .findFirst()
                .orElseThrow(
                        () ->
                                new ValidationException(
                                        ErrorCode.AUTH_017,
                                        "No verified primary email found in GitHub account"));
    }
}
