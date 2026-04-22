package com.ahogek.cttserver.auth.oauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OAuth authorization URL response.
 *
 * @param authUrl the provider authorization URL to redirect the user to
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-22
 */
@Schema(description = "OAuth authorization URL response")
public record AuthorizeResponse(
        @Schema(
                        description = "Provider authorization URL for user redirect",
                        example =
                                "https://github.com/login/oauth/authorize?client_id=xxx&scope=read:user&state=yyy")
                String authUrl) {}
