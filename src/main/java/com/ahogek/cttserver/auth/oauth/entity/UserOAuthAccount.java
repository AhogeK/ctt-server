package com.ahogek.cttserver.auth.oauth.entity;

import com.ahogek.cttserver.auth.oauth.crypto.OAuthTokenConverter;
import com.ahogek.cttserver.auth.oauth.enums.OAuthProvider;
import com.ahogek.cttserver.user.entity.User;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity representing a user's OAuth account linkage.
 *
 * <p>Stores OAuth provider account information and encrypted tokens for third-party authentication
 * integration. Each user can have multiple OAuth accounts linked to different providers.
 *
 * @author AhogeK
 * @since 0.16.0
 */
@Entity
@Table(
        name = "user_oauth_accounts",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_user_oauth_provider_uid",
                    columnNames = {"provider", "provider_user_id"}),
            @UniqueConstraint(
                    name = "uk_user_oauth_user_provider",
                    columnNames = {"user_id", "provider"})
        })
public class UserOAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(name = "provider_login")
    private String providerLogin;

    @Column(name = "provider_email")
    private String providerEmail;

    @Convert(converter = OAuthTokenConverter.class)
    @Column(name = "access_token_encrypted", columnDefinition = "TEXT")
    private String accessToken;

    @Convert(converter = OAuthTokenConverter.class)
    @Column(name = "refresh_token_encrypted", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_expires_at")
    private OffsetDateTime tokenExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UserOAuthAccount() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public OAuthProvider getProvider() {
        return provider;
    }

    public void setProvider(OAuthProvider provider) {
        this.provider = provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public void setProviderUserId(String providerUserId) {
        this.providerUserId = providerUserId;
    }

    public String getProviderLogin() {
        return providerLogin;
    }

    public void setProviderLogin(String providerLogin) {
        this.providerLogin = providerLogin;
    }

    public String getProviderEmail() {
        return providerEmail;
    }

    public void setProviderEmail(String providerEmail) {
        this.providerEmail = providerEmail;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public OffsetDateTime getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public void setTokenExpiresAt(OffsetDateTime tokenExpiresAt) {
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
