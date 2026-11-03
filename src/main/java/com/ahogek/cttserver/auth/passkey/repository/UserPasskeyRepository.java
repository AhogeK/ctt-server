package com.ahogek.cttserver.auth.passkey.repository;

import com.ahogek.cttserver.auth.passkey.entity.UserPasskey;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for registered WebAuthn credentials.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Repository
public interface UserPasskeyRepository extends JpaRepository<UserPasskey, UUID> {

    /**
     * Lists every credential owned by a user in registration order.
     *
     * @param userId the owner
     * @return the user's credentials
     */
    List<UserPasskey> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    /**
     * BOLA-protected lookup: the credential must belong to the supplied user.
     *
     * @param id the credential row id
     * @param userId the expected owner
     * @return the credential when both match
     */
    @EntityGraph(attributePaths = "user")
    Optional<UserPasskey> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Looks up the credential used in an assertion by its WebAuthn credential ID.
     *
     * @param credentialId base64url credential ID
     * @return the credential when registered
     */
    @EntityGraph(attributePaths = "user")
    Optional<UserPasskey> findByCredentialId(String credentialId);

    /**
     * Counts credentials owned by a user.
     *
     * @param userId the owner
     * @return credential count
     */
    long countByUserId(UUID userId);

    /**
     * Checks whether a credential ID is already registered.
     *
     * @param credentialId base64url credential ID
     * @return true when already registered
     */
    boolean existsByCredentialId(String credentialId);

    /**
     * Locks all credentials of an account so concurrent deletions cannot remove the last one.
     *
     * @param userId the owner
     * @return the locked credentials
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserPasskey p where p.user.id = :userId")
    List<UserPasskey> lockAllByUserId(@Param("userId") UUID userId);
}
