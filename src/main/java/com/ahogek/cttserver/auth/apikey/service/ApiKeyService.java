package com.ahogek.cttserver.auth.apikey.service;

import com.ahogek.cttserver.auth.apikey.dto.CreateApiKeyRequest;
import com.ahogek.cttserver.auth.apikey.dto.CreateApiKeyResponse;
import com.ahogek.cttserver.auth.apikey.entity.ApiKey;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;

import java.util.UUID;

/**
 * Write-side contract for API key management.
 *
 * <p>Responsibilities: issuing new keys (returning the raw secret exactly once) and revoking
 * existing keys. The {@link ApiKeyQueryService} handles read-only lookups; this interface is
 * concerned exclusively with state-mutating operations that must run inside a write transaction.
 *
 * <p>BOLA protection: every method takes the owning {@code userId} explicitly. Implementations must
 * never accept a request to mutate another user's key — when the {@code userId} does not match the
 * persisted owner, {@link NotFoundException} is raised so the response remains indistinguishable
 * from "key never existed".
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-09
 */
public interface ApiKeyService {

    /**
     * Issues a new API key for the given user and returns the raw secret exactly once.
     *
     * <p>Implementations must enforce the per-user issuance limit (default 20 active keys). When
     * the limit is exceeded, {@link ConflictException} (AUTH_014) is raised.
     *
     * @param userId the owning user; must not be {@code null}
     * @param request the creation payload (name, scopes, optional expiration)
     * @return a {@link CreateApiKeyResponse} containing both the raw key (shown only here) and the
     *     persisted metadata snapshot
     * @throws ConflictException (AUTH_014) when the user already owns the maximum allowed number of
     *     non-revoked keys
     */
    CreateApiKeyResponse createApiKey(UUID userId, CreateApiKeyRequest request);

    /**
     * Revokes an API key owned by the given user.
     *
     * <p>The {@code id} must belong to {@code userId} — when the lookup fails the implementation
     * raises {@link NotFoundException} rather than distinguishing between "does not exist" and
     * "belongs to another user". Idempotency is intentional: revoking an already-revoked key is a
     * successful no-op.
     *
     * @param userId the expected owner
     * @param id the API key id
     * @throws NotFoundException (AUTH_010) when no key with the given id is owned by {@code
     *     userId}; using AUTH_010 keeps the response indistinguishable from "key never existed" to
     *     prevent enumeration attacks
     */
    void revokeApiKey(UUID userId, UUID id);

    /**
     * Validates an API key by its raw value and updates the last-used timestamp.
     *
     * <p>Used by the authentication filter to verify a presented API key. The raw key is hashed
     * internally and looked up by hash. If the key is active (not revoked, not expired), the {@code
     * lastUsedAt} field is updated synchronously within the same transaction and the persisted
     * entity is returned.
     *
     * @param rawKey the raw API key value presented in the Authorization header
     * @return the validated {@link ApiKey} entity
     * @throws NotFoundException (AUTH_010) when the key hash does not match any stored key
     * @throws UnauthorizedException (AUTH_011) when the key has expired
     * @throws ForbiddenException (AUTH_012) when the key has been revoked
     */
    ApiKey validateAndTouch(String rawKey);
}
