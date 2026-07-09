package com.ahogek.cttserver.auth.apikey.service;

import com.ahogek.cttserver.auth.apikey.dto.ApiKeyResponse;
import com.ahogek.cttserver.common.exception.NotFoundException;

import java.util.List;
import java.util.UUID;

/**
 * Read-side contract for API key lookup.
 *
 * <p>All methods enforce BOLA protection by requiring the caller's {@code userId} and matching it
 * against the persisted owner. A key that exists but belongs to a different user is reported as not
 * found.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-09
 */
public interface ApiKeyQueryService {

    /**
     * Lists every API key (active, expired, revoked) owned by the given user.
     *
     * @param userId the owning user id
     * @return all keys owned by the user; never {@code null}; an empty list when the user has no
     *     keys
     */
    List<ApiKeyResponse> listApiKeys(UUID userId);

    /**
     * Returns a single API key when both id and owner match.
     *
     * @param userId the expected owner id
     * @param id the API key id
     * @return the metadata snapshot
     * @throws NotFoundException (AUTH_010) when no key with the given id is owned by {@code userId}
     */
    ApiKeyResponse getApiKey(UUID userId, UUID id);
}
