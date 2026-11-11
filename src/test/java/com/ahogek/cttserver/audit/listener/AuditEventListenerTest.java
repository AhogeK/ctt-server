package com.ahogek.cttserver.audit.listener;

import com.ahogek.cttserver.audit.SecurityAuditEvent;
import com.ahogek.cttserver.audit.entity.AuditLog;
import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.service.AuditLogWriter;
import com.ahogek.cttserver.common.metrics.BusinessMetrics;
import com.ahogek.cttserver.common.metrics.BusinessMetrics.AuditWriteOutcome;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

    @Mock private AuditLogWriter auditLogWriter;

    @Captor private ArgumentCaptor<AuditLog> entityCaptor;

    private SimpleMeterRegistry registry;
    private AuditEventListener listener;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        listener = new AuditEventListener(auditLogWriter, new BusinessMetrics(registry));
    }

    @Test
    void shouldMapTraceId_whenTheEventCarriesOne() {
        String traceId = "listener-test-trace-id-123456789012";
        SecurityAuditEvent event =
                new SecurityAuditEvent(
                        UUID.randomUUID(),
                        AuditAction.LOGIN_SUCCESS,
                        ResourceType.USER,
                        "user-123",
                        SecuritySeverity.INFO,
                        "192.168.1.1",
                        "TestAgent",
                        traceId,
                        AuditDetails.empty(),
                        Instant.now());

        listener.handleSecurityAudit(event);

        verify(auditLogWriter).persist(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getTraceId()).isEqualTo(traceId);
    }

    @Test
    void shouldMapEveryField_whenTheEventIsComplete() {
        UUID userId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        AuditDetails details = AuditDetails.reason("Test reason");

        SecurityAuditEvent event =
                new SecurityAuditEvent(
                        userId,
                        AuditAction.LOGIN_FAILED,
                        ResourceType.API_KEY,
                        "api-key-456",
                        SecuritySeverity.WARNING,
                        "10.0.0.1",
                        "PostmanRuntime/7.0",
                        "trace-id-abc123def456ghi789jkl012mno345",
                        details,
                        timestamp);

        listener.handleSecurityAudit(event);

        verify(auditLogWriter).persist(entityCaptor.capture());
        AuditLog captured = entityCaptor.getValue();

        assertThat(captured.getUserId()).isEqualTo(userId);
        assertThat(captured.getAction()).isEqualTo(AuditAction.LOGIN_FAILED);
        assertThat(captured.getResourceType()).isEqualTo(ResourceType.API_KEY);
        assertThat(captured.getResourceId()).isEqualTo("api-key-456");
        assertThat(captured.getSeverity()).isEqualTo(SecuritySeverity.WARNING);
        assertThat(captured.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(captured.getUserAgent()).isEqualTo("PostmanRuntime/7.0");
        assertThat(captured.getTraceId()).isEqualTo("trace-id-abc123def456ghi789jkl012mno345");
        assertThat(captured.getDetails()).isEqualTo(details);
    }

    @Test
    void shouldLeaveTraceIdNull_whenTheEventHasNone() {
        SecurityAuditEvent event =
                new SecurityAuditEvent(
                        null,
                        AuditAction.RATE_LIMIT_EXCEEDED,
                        ResourceType.PASSWORD_RESET,
                        null,
                        SecuritySeverity.CRITICAL,
                        null,
                        null,
                        null,
                        AuditDetails.error("RATE_LIMIT", "Quota exceeded"),
                        Instant.now());

        listener.handleSecurityAudit(event);

        verify(auditLogWriter).persist(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getTraceId()).isNull();
    }

    @Test
    void shouldFallBackToUnknown_whenIpAndUserAgentAreMissing() {
        SecurityAuditEvent event =
                new SecurityAuditEvent(
                        null,
                        AuditAction.UNAUTHORIZED_ACCESS,
                        ResourceType.REFRESH_TOKEN,
                        null,
                        SecuritySeverity.WARNING,
                        null,
                        null,
                        "trace-id-null-fields-test",
                        AuditDetails.empty(),
                        Instant.now());

        listener.handleSecurityAudit(event);

        verify(auditLogWriter).persist(entityCaptor.capture());
        AuditLog captured = entityCaptor.getValue();

        assertThat(captured.getIpAddress()).isEqualTo("UNKNOWN");
        assertThat(captured.getUserAgent()).isEqualTo("UNKNOWN");
        assertThat(captured.getTraceId()).isEqualTo("trace-id-null-fields-test");
    }

    @Test
    void shouldCountTheRow_whenItIsPersisted() {
        listener.handleSecurityAudit(eventWith(SecuritySeverity.INFO));

        assertThat(auditWrites(SecuritySeverity.INFO, AuditWriteOutcome.WRITTEN)).isEqualTo(1.0);
    }

    @Test
    void shouldCountTheDroppedRow_whenPersistenceFails() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(auditLogWriter)
                .persist(any());

        assertThatCode(() -> listener.handleSecurityAudit(eventWith(SecuritySeverity.WARNING)))
                .doesNotThrowAnyException();

        assertThat(auditWrites(SecuritySeverity.WARNING, AuditWriteOutcome.DROPPED)).isEqualTo(1.0);
        assertThat(auditWrites(SecuritySeverity.WARNING, AuditWriteOutcome.WRITTEN)).isZero();
    }

    private double auditWrites(SecuritySeverity severity, AuditWriteOutcome outcome) {
        Counter counter =
                registry.find(BusinessMetrics.AUDIT_EVENTS)
                        .tag(BusinessMetrics.TAG_SEVERITY, severity.name())
                        .tag(BusinessMetrics.TAG_OUTCOME, outcome.name())
                        .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private SecurityAuditEvent eventWith(SecuritySeverity severity) {
        return new SecurityAuditEvent(
                UUID.randomUUID(),
                AuditAction.LOGOUT_SUCCESS,
                ResourceType.USER,
                "user-789",
                severity,
                "127.0.0.1",
                "TestAgent",
                "trace-id-metrics-test",
                AuditDetails.empty(),
                Instant.now());
    }
}
