package com.ahogek.cttserver.auth.oauth.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "GitHub user profile information")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GitHubUserInfo(
        @Schema(description = "GitHub user ID", example = "12345678") long id,
        @Schema(description = "GitHub username", example = "octocat") String login,
        @Schema(description = "Display name", example = "The Octocat")
                @JsonInclude(JsonInclude.Include.NON_NULL)
                String name,
        @Schema(
                        description = "Avatar URL",
                        example = "https://avatars.githubusercontent.com/u/583231?v=4")
                @JsonProperty("avatar_url")
                String avatarUrl,
        @Schema(
                        description = "Primary email address (may be null if not public)",
                        example = "octocat@github.com")
                @JsonInclude(JsonInclude.Include.NON_NULL)
                String email) {
    /**
     * Returns a new GitHubUserInfo with the email replaced. Used for fallback logic when email is
     * not public.
     */
    public GitHubUserInfo withEmail(String email) {
        return new GitHubUserInfo(this.id, this.login, this.name, this.avatarUrl, email);
    }
}
