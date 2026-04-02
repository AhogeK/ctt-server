package com.ahogek.cttserver.audit.service;

import com.ahogek.cttserver.audit.SecurityAuditEvent;
import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.common.context.ClientIdentity;
import com.ahogek.cttserver.common.context.RequestContext;
import com.ahogek.cttserver.common.context.RequestInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock private ApplicationEventPublisher eventPublisher;

    @Captor private ArgumentCaptor<SecurityAuditEvent> eventCaptor;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(eventPublisher);
    }

    @Test
    void log_publishes_event_with_all_parameters() {
        UUID userId = UUID.randomUUID();
        AuditDetails details = AuditDetails.reason("Test reason");

        auditLogService.log(
                userId,
                AuditAction.LOGIN_FAILED,
                ResourceType.USER,
                "user-123",
                SecuritySeverity.WARNING,
                details);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        SecurityAuditEvent captured = eventCaptor.getValue();

        assertThat(captured.userId()).isEqualTo(userId);
        assertThat(captured.action()).isEqualTo(AuditAction.LOGIN_FAILED);
        assertThat(captured.resourceType()).isEqualTo(ResourceType.USER);
        assertThat(captured.resourceId()).isEqualTo("user-123");
        assertThat(captured.severity()).isEqualTo(SecuritySeverity.WARNING);
        assertThat(captured.details()).isEqualTo(details);
    }

    @Test
    void log_uses_system_fallback_when_no_request_context() {
        auditLogService.log(
                null,
                AuditAction.LOGIN_SUCCESS,
                ResourceType.USER,
                "user-456",
                SecuritySeverity.INFO,
                AuditDetails.empty());

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        SecurityAuditEvent captured = eventCaptor.getValue();

        assertThat(captured.ipAddress()).isEqualTo("SYSTEM");
        assertThat(captured.userAgent()).isEqualTo("INTERNAL");
    }

    @Test
    void logSuccess_uses_info_severity_and_empty_details() {
        UUID userId = UUID.randomUUID();

        auditLogService.logSuccess(
                userId, AuditAction.LOGIN_SUCCESS, ResourceType.USER, "user-789");

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        SecurityAuditEvent captured = eventCaptor.getValue();

        assertThat(captured.userId()).isEqualTo(userId);
        assertThat(captured.action()).isEqualTo(AuditAction.LOGIN_SUCCESS);
        assertThat(captured.severity()).isEqualTo(SecuritySeverity.INFO);
        assertThat(captured.details()).isEqualTo(AuditDetails.empty());
    }

    @Test
    void logFailure_uses_warning_severity_and_reason_details() {
        UUID userId = UUID.randomUUID();

        auditLogService.logFailure(
                userId,
                AuditAction.LOGIN_FAILED,
                ResourceType.USER,
                "user-abc",
                "Invalid credentials");

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        SecurityAuditEvent captured = eventCaptor.getValue();

        assertThat(captured.userId()).isEqualTo(userId);
        assertThat(captured.action()).isEqualTo(AuditAction.LOGIN_FAILED);
        assertThat(captured.severity()).isEqualTo(SecuritySeverity.WARNING);
        assertThat(captured.details().reason()).isEqualTo("Invalid credentials");
    }

    @Test
    void logCritical_uses_critical_severity() {
        UUID userId = UUID.randomUUID();
        AuditDetails details = AuditDetails.error("AUTH_001", "Token compromised");

        auditLogService.logCritical(
                userId, AuditAction.UNAUTHORIZED_ACCESS, ResourceType.API_KEY, "key-xyz", details);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        SecurityAuditEvent captured = eventCaptor.getValue();

        assertThat(captured.userId()).isEqualTo(userId);
        assertThat(captured.action()).isEqualTo(AuditAction.UNAUTHORIZED_ACCESS);
        assertThat(captured.severity()).isEqualTo(SecuritySeverity.CRITICAL);
        assertThat(captured.details().errorCode()).isEqualTo("AUTH_001");
    }

    @Test
    void logTransition_records_state_change() {
        UUID userId = UUID.randomUUID();

        auditLogService.logTransition(
                userId,
                AuditAction.PASSWORD_CHANGED,
                ResourceType.USER,
                userId.toString(),
                "old-hash-value",
                "new-hash-value");

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        SecurityAuditEvent captured = eventCaptor.getValue();

        assertThat(captured.userId()).isEqualTo(userId);
        assertThat(captured.action()).isEqualTo(AuditAction.PASSWORD_CHANGED);
        assertThat(captured.severity()).isEqualTo(SecuritySeverity.INFO);
        assertThat(captured.details().stateBefore()).isEqualTo("old-hash-value");
        assertThat(captured.details().stateAfter()).isEqualTo("new-hash-value");
    }

    @Test
    void log_extracts_traceId_from_RequestContext() {
        String expectedTraceId = "test-trace-id-12345678901234567890";
        RequestInfo requestInfo =
                new RequestInfo(
                        expectedTraceId,
                        "192.168.1.1",
                        "TestAgent",
                        "/api/test",
                        "POST",
                        ClientIdentity.empty());

        ScopedValue.where(RequestContext.CONTEXT, requestInfo)
                .run(
                        () ->
                                auditLogService.log(
                                        UUID.randomUUID(),
                                        AuditAction.LOGIN_SUCCESS,
                                        ResourceType.USER,
                                        "user-123",
                                        SecuritySeverity.INFO,
                                        AuditDetails.empty()));

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().traceId()).isEqualTo(expectedTraceId);
    }

    @Test
    void log_uses_null_when_context_missing() {
        auditLogService.log(
                null,
                AuditAction.LOGIN_SUCCESS,
                ResourceType.USER,
                "user-456",
                SecuritySeverity.INFO,
                AuditDetails.empty());

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().traceId()).isNull();
    }
}
