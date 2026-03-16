package com.ahogek.cttserver.audit;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditEventTest {

    @Test
    void full_constructor_creates_event_with_all_fields() {
        Instant now = Instant.now();
        UUID userId = UUID.randomUUID();
        AuditDetails details = AuditDetails.reason("test reason");

        SecurityAuditEvent event =
                new SecurityAuditEvent(
                        userId,
                        AuditAction.LOGIN_SUCCESS,
                        ResourceType.USER,
                        "resource-123",
                        SecuritySeverity.INFO,
                        "192.168.1.1",
                        "Mozilla/5.0",
                        details,
                        now);

        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.action()).isEqualTo(AuditAction.LOGIN_SUCCESS);
        assertThat(event.resourceType()).isEqualTo(ResourceType.USER);
        assertThat(event.resourceId()).isEqualTo("resource-123");
        assertThat(event.severity()).isEqualTo(SecuritySeverity.INFO);
        assertThat(event.ipAddress()).isEqualTo("192.168.1.1");
        assertThat(event.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(event.details()).isEqualTo(details);
        assertThat(event.timestamp()).isEqualTo(now);
    }

    @Test
    void partial_constructor_without_timestamp_sets_current_time() {
        Instant before = Instant.now();
        AuditDetails details = AuditDetails.reason("test");

        SecurityAuditEvent event =
                new SecurityAuditEvent(
                        null,
                        AuditAction.LOGIN_FAILED,
                        ResourceType.API_KEY,
                        "api-key-456",
                        SecuritySeverity.WARNING,
                        "10.0.0.1",
                        "TestAgent",
                        details);

        Instant after = Instant.now();

        assertThat(event.userId()).isNull();
        assertThat(event.action()).isEqualTo(AuditAction.LOGIN_FAILED);
        assertThat(event.resourceType()).isEqualTo(ResourceType.API_KEY);
        assertThat(event.timestamp()).isBetween(before, after);
    }

    @Test
    void convenience_constructor_for_security_violations() {
        com.ahogek.cttserver.common.context.RequestInfo requestInfo =
                new com.ahogek.cttserver.common.context.RequestInfo(
                        "trace-789",
                        "10.0.0.1",
                        "PostmanRuntime/7.0",
                        "/api/data",
                        "GET",
                        "device-456");
        AuditDetails details = AuditDetails.attempt(3, "Invalid token");

        SecurityAuditEvent event =
                new SecurityAuditEvent(
                        AuditAction.UNAUTHORIZED_ACCESS,
                        ResourceType.REFRESH_TOKEN,
                        SecuritySeverity.CRITICAL,
                        requestInfo,
                        details);

        assertThat(event.action()).isEqualTo(AuditAction.UNAUTHORIZED_ACCESS);
        assertThat(event.resourceType()).isEqualTo(ResourceType.REFRESH_TOKEN);
        assertThat(event.severity()).isEqualTo(SecuritySeverity.CRITICAL);
        assertThat(event.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(event.userAgent()).isEqualTo("PostmanRuntime/7.0");
        assertThat(event.resourceId()).isEqualTo("trace-789");
        assertThat(event.details()).isEqualTo(details);
    }

    @Test
    void convenience_constructor_handles_null_request_info() {
        AuditDetails details = AuditDetails.error("RATE_LIMIT_EXCEEDED", "Too many requests");

        SecurityAuditEvent event =
                new SecurityAuditEvent(
                        AuditAction.RATE_LIMIT_EXCEEDED,
                        ResourceType.PASSWORD_RESET,
                        SecuritySeverity.WARNING,
                        null,
                        details);

        assertThat(event.action()).isEqualTo(AuditAction.RATE_LIMIT_EXCEEDED);
        assertThat(event.ipAddress()).isNull();
        assertThat(event.userAgent()).isNull();
        assertThat(event.resourceId()).isNull();
    }
}
