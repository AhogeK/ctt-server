package com.ahogek.cttserver.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Login attempt record for brute-force protection.
 *
 * <p>This is an append-only, read-heavy entity. Email and IP addresses are stored as SHA-256
 * hashes to comply with privacy requirements. No setters are provided — records are created
 * via the no-arg constructor and field assignment by JPA.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-09
 */
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email_hash", nullable = false, length = 64)
    private String emailHash;

    @Column(name = "ip_hash", nullable = false, length = 64)
    private String ipHash;

    @CreationTimestamp
    @Column(name = "attempt_at", nullable = false, updatable = false)
    private Instant attemptAt;

    public LoginAttempt() {}

    public LoginAttempt(String emailHash, String ipHash) {
        this.emailHash = emailHash;
        this.ipHash = ipHash;
    }

    // ==========================================
    // Getters
    // ==========================================

    public Long getId() {
        return id;
    }

    public String getEmailHash() {
        return emailHash;
    }

    public String getIpHash() {
        return ipHash;
    }

    public Instant getAttemptAt() {
        return attemptAt;
    }
}
