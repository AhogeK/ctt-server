package com.ahogek.cttserver.user.repository;

import com.ahogek.cttserver.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds user by email address.
     *
     * @param email the email address
     * @return Optional containing user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks if email is already registered.
     *
     * @param email the email address
     * @return true if email exists
     */
    boolean existsByEmail(String email);
}
