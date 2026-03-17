package com.ahogek.cttserver.user.repository;

import com.ahogek.cttserver.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity.
 *
 * <p>All email lookups use case-insensitive queries to leverage the uk_users_email_lower function
 * index.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds user by email address (case-insensitive).
     *
     * <p>Generates SQL: WHERE LOWER(email) = LOWER(?) Leverages uk_users_email_lower index for
     * O(log N) lookup.
     *
     * <p>Since all emails are normalized to lowercase on persist, this is equivalent to exact match
     * while maintaining index usage.
     *
     * @param email the email address
     * @return Optional containing user if found
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Checks if email is already registered (case-insensitive).
     *
     * <p>Generates SQL: WHERE LOWER(email) = LOWER(?) Leverages uk_users_email_lower index for
     * O(log N) lookup.
     *
     * @param email the email address
     * @return true if email exists
     */
    boolean existsByEmailIgnoreCase(String email);
}
