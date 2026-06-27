package com.ahogek.cttserver.auth.oauth.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response payload listing all OAuth account bindings for the current user.
 *
 * @param accounts list of OAuth account bindings (empty if user has no bindings)
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-06-28
 */
@Schema(description = "List of OAuth account bindings for the current user")
public record OAuthAccountsResponse(
        @Schema(description = "OAuth account bindings") List<OAuthAccountBinding> accounts) {}