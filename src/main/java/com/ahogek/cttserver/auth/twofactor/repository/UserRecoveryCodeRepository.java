package com.ahogek.cttserver.auth.twofactor.repository;

import com.ahogek.cttserver.auth.twofactor.entity.UserRecoveryCode;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistence for single-use recovery codes.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Repository
public interface UserRecoveryCodeRepository extends JpaRepository<UserRecoveryCode, UUID> {

    /**
     * Atomically consumes a code, returning 1 only for the caller that marked it used.
     *
     * @param userId the account
     * @param codeHash the normalized code hash
     * @param now the consumption instant
     * @return 1 when this call consumed the code, otherwise 0
     */
    @Modifying
    @Query(
            "update UserRecoveryCode c set c.usedAt = :now"
                    + " where c.userId = :userId and c.codeHash = :codeHash and c.usedAt is null")
    int consume(
            @Param("userId") UUID userId,
            @Param("codeHash") String codeHash,
            @Param("now") Instant now);

    long countByUserIdAndUsedAtIsNull(UUID userId);

    void deleteByUserId(UUID userId);
}
