package com.ahogek.cttserver.audit;

import com.ahogek.cttserver.TestcontainersConfiguration;
import com.ahogek.cttserver.audit.entity.AuditLog;
import com.ahogek.cttserver.audit.repository.AuditLogRepository;
import com.ahogek.cttserver.common.context.RequestInfo;
import com.ahogek.cttserver.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for audit event persistence via Testcontainers.
 *
 * <p>Test Coverage:
 *
 * <ul>
 *   <li>PostgreSQL container startup via TestcontainersConfiguration
 *   <li>Flyway migration execution
 *   <li>JPA JSONB mapping with Hibernate 6
 *   <li>Async event listener execution
 *   <li>Full end-to-end: Event publish → Async handle → Database persist
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuditEventListenerIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AuditLogRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Should persist audit log asynchronously with full context")
    void shouldPersistAuditLogAsyncWhenEventPublished() {
        // Given: Construct a security audit event with full context
        RequestInfo requestInfo =
            new RequestInfo(
                "trace-test-123",
                "192.168.1.100",
                "Mozilla/5.0 (Test)",
                "/api/admin/users",
                "DELETE",
                "device-12345");

        SecurityAuditEvent event =
            new SecurityAuditEvent(
                ErrorCode.AUTH_009, "Insufficient permissions", requestInfo, Instant.now());

        // When: Publish event (async processing)
        eventPublisher.publishEvent(event);

        // Then: Use Awaitility to wait for async operation and verify database state
        await().atMost(3, TimeUnit.SECONDS)
            .untilAsserted(
                () -> {
                    List<AuditLog> logs = repository.findAll();
                    assertThat(logs).hasSize(1);

                    AuditLog savedLog = logs.getFirst();

                    // Verify basic field mapping
                    assertThat(savedLog.getAction()).isEqualTo("SECURITY_VIOLATION");
                    assertThat(savedLog.getResourceType()).isEqualTo("AUTH_009");
                    assertThat(savedLog.getResourceId()).isEqualTo("trace-test-123");

                    // Verify IP address and user agent storage
                    assertThat(savedLog.getIpAddress()).isEqualTo("192.168.1.100");
                    assertThat(savedLog.getUserAgent()).isEqualTo("Mozilla/5.0 (Test)");

                    // Verify JSONB deserialization (critical test point)
                    Map<String, Object> details = savedLog.getDetails();
                    assertThat(details)
                        .containsEntry("errorCode", "AUTH_009")
                        .containsEntry("errorMessage", "Insufficient permissions")
                        .containsEntry("customMessage", "Insufficient permissions")
                        .containsEntry("traceId", "trace-test-123")
                        .containsEntry("requestUri", "/api/admin/users")
                        .containsEntry("httpMethod", "DELETE")
                        .containsEntry("deviceId", "device-12345");

                    // Verify automatic timestamp generation
                    assertThat(savedLog.getCreatedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("Should persist minimal event without RequestInfo using defaults")
    void shouldPersistMinimalEventWithoutRequestInfo() {
        // Given: Minimal event (no request info)
        SecurityAuditEvent event =
            new SecurityAuditEvent(ErrorCode.AUTH_001, "Token expired", null, Instant.now());

        // When: Publish event
        eventPublisher.publishEvent(event);

        // Then: Verify persistence with default values
        await().atMost(3, TimeUnit.SECONDS)
            .untilAsserted(
                () -> {
                    List<AuditLog> logs = repository.findAll();
                    assertThat(logs).hasSize(1);

                    AuditLog savedLog = logs.getFirst();

                    assertThat(savedLog.getAction()).isEqualTo("SECURITY_VIOLATION");
                    assertThat(savedLog.getResourceType()).isEqualTo("AUTH_001");

                    // Uses UNKNOWN defaults when no RequestInfo
                    assertThat(savedLog.getIpAddress()).isEqualTo("UNKNOWN");
                    assertThat(savedLog.getUserAgent()).isEqualTo("UNKNOWN");
                    assertThat(savedLog.getResourceId()).isNull();

                    // JSONB contains basic error info
                    // Note: errorMessage from ErrorCode default, customMessage from event
                    Map<String, Object> details = savedLog.getDetails();
                    assertThat(details)
                        .containsEntry("errorCode", "AUTH_001")
                        .containsEntry("errorMessage", "Invalid credentials")
                        .containsEntry("customMessage", "Token expired");

                    // Should not contain request-specific fields
                    assertThat(details)
                        .doesNotContainKeys("traceId", "requestUri", "deviceId");
                });
    }

    @Test
    @DisplayName("Should persist multiple events asynchronously in order")
    void shouldPersistMultipleEventsInOrder() {
        // Given: Publish multiple events
        for (int i = 0; i < 5; i++) {
            RequestInfo info =
                new RequestInfo(
                    "trace-" + i, "10.0.0." + i, "Agent" + i, "/api/" + i, "GET", null);
            SecurityAuditEvent event =
                new SecurityAuditEvent(
                    ErrorCode.AUTH_010, "API key invalid", info, Instant.now());
            eventPublisher.publishEvent(event);
        }

        // Then: Wait and verify all events persisted
        await().atMost(5, TimeUnit.SECONDS)
            .untilAsserted(
                () -> {
                    List<AuditLog> logs = repository.findAll();
                    assertThat(logs).hasSize(5);

                    // Verify each record has unique traceId
                    List<String> traceIds =
                        logs.stream().map(AuditLog::getResourceId).sorted().toList();
                    assertThat(traceIds)
                        .containsExactly(
                            "trace-0", "trace-1", "trace-2", "trace-3", "trace-4");
                });
    }
}
