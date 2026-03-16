package com.ahogek.cttserver.audit.entity;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Audit log entity for security and operational auditing.
 *
 * <p>Maps to the {@code audit_logs} table with native PostgreSQL JSONB support via Hibernate 6.
 *
 * <p>Key Features:
 *
 * <ul>
 *   <li>JSONB storage via {@code @JdbcTypeCode(SqlTypes.JSON)}
 *   <li>Strongly typed enums for action, resource type, and severity
 *   <li>Fluent API for object construction (chainable setters)
 *   <li>Automatic timestamp creation via {@code @CreationTimestamp}
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @see com.ahogek.cttserver.audit.listener.AuditEventListener
 * @since 2026-03-16
 */
@Entity
@Table(name = "audit_logs")
@SuppressWarnings("unused")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 50)
    private ResourceType resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SecuritySeverity severity;

    /**
     * Hibernate 6 native PostgreSQL JSONB support.
     *
     * <p>No external dependencies needed - {@code @JdbcTypeCode(SqlTypes.JSON)} handles
     * serialization/deserialization automatically.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Default constructor required by JPA. */
    public AuditLog() {}

    // Getters
    public Long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    // Fluent Setters (chainable)
    public AuditLog setUserId(UUID userId) {
        this.userId = userId;
        return this;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditLog setAction(AuditAction action) {
        this.action = action;
        return this;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public AuditLog setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
        return this;
    }

    public String getResourceId() {
        return resourceId;
    }

    public AuditLog setResourceId(String resourceId) {
        this.resourceId = resourceId;
        return this;
    }

    public SecuritySeverity getSeverity() {
        return severity;
    }

    public AuditLog setSeverity(SecuritySeverity severity) {
        this.severity = severity;
        return this;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public AuditLog setDetails(Map<String, Object> details) {
        this.details = details;
        return this;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public AuditLog setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public AuditLog setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
