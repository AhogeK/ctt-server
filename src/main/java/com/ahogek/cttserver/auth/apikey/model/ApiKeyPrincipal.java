package com.ahogek.cttserver.auth.apikey.model;

import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;

import java.util.Set;
import java.util.UUID;

/**
 * Security principal for API key authentication.
 *
 * @param userId the owning user's UUID
 * @param keyId the API key's UUID
 * @param scopes the granted scopes
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-10
 */
public record ApiKeyPrincipal(UUID userId, UUID keyId, Set<ApiKeyScope> scopes) {}
