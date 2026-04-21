package com.ahogek.cttserver.auth.oauth.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "GitHub OAuth access token response")
public record GitHubTokenResponse(
        @Schema(description = "Access token for GitHub API", example = "gho_xxxxxxxxxxxx")
                @JsonProperty("access_token")
                String accessToken,
        @Schema(description = "Token type, typically 'bearer'", example = "bearer")
                @JsonProperty("token_type")
                String tokenType,
        @Schema(description = "Granted scopes", example = "read:user,user:email")
                @JsonProperty("scope")
                String scope) {}
