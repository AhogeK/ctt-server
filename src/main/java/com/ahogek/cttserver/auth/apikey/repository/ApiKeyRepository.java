package com.ahogek.cttserver.auth.apikey.repository;

import com.ahogek.cttserver.auth.apikey.entity.ApiKey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link ApiKey} aggregate root operations.
 *
 * <p>All lookup paths leverage the underlying PostgreSQL indexes defined in the {@code
 * V20260303210000__init_base_schema.sql} migration:
 *
 * <ul>
 *   <li>{@code uk_api_keys_key_hash} — unique index on {@code key_hash}; O(log N) auth lookup.
 *   <li>{@code idx_api_keys_user_id} — b-tree on {@code user_id}; backs list / BOLA lookups.
 *   <li>{@code idx_api_keys_active} — partial index on {@code (user_id, created_at) WHERE
 *       revoked_at IS NULL}; backs the active-key count query used to enforce the per-user limit.
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-09
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /**
     * Looks up an API key by its SHA-256 hash during authentication.
     *
     * <p>Uses unique index {@code uk_api_keys_key_hash} for O(log N) lookup. A missing result
     * should be translated into {@code AUTH_010} (invalid credentials) by the calling service — it
     * does not leak whether the hash has ever existed.
     *
     * @param keyHash the SHA-256 hex digest of the full API key
     * @return {@code Optional} containing the entity when found
     */
    Optional<ApiKey> findByKeyHash(String keyHash);

    /**
     * Lists every API key owned by a user. Used by the management endpoint to render the {@code
     * /settings/api-keys} page.
     *
     * <p>Uses index {@code idx_api_keys_user_id}.
     *
     * @param userId the owning user id
     * @return all keys (active, expired, and revoked) owned by the user; never {@code null}
     */
    List<ApiKey> findAllByUserId(UUID userId);

    /**
     * BOLA-protected lookup: returns the key only when both the id and the owner match.
     *
     * <p>Prevents an authenticated user from reading or revoking another user's keys by guessing
     * UUIDs. The {@code idx_api_keys_user_id} index keeps the lookup efficient.
     *
     * @param id the API key id
     * @param userId the expected owner id
     * @return {@code Optional} containing the entity when both id and user match
     */
    Optional<ApiKey> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Counts the non-revoked keys owned by a user. Used to enforce the per-user issuance limit
     * (default 20) without loading the full entity set.
     *
     * <p>Hits the partial index {@code idx_api_keys_active} because of the {@code WHERE revoked_at
     * IS NULL} predicate.
     *
     * @param userId the owning user id
     * @return number of currently non-revoked keys for the user
     */
    long countByUserIdAndRevokedAtIsNull(UUID userId);
}
