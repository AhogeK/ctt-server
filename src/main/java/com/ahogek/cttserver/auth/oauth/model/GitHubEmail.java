package com.ahogek.cttserver.auth.oauth.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "GitHub email address information")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GitHubEmail(
        @Schema(description = "Email address", example = "octocat@github.com") String email,
        @Schema(description = "Whether this is the primary email", example = "true")
                boolean primary,
        @Schema(description = "Whether the email is verified", example = "true") boolean verified,
        @Schema(description = "Email visibility setting", example = "public")
                @JsonInclude(JsonInclude.Include.NON_NULL)
                String visibility) {}
