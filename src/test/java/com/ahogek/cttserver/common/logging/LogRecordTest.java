package com.ahogek.cttserver.common.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests for {@link LogRecord} structured logging utility.
 *
 * @author AhogeK
 * @since 2026-03-16
 */
@DisplayName("LogRecord - Structured Business Logging")
class LogRecordTest {

    @Test
    @DisplayName("Should create info level log record")
    void shouldCreateInfoLevelLogRecord() {
        assertDoesNotThrow(
            () ->
                LogRecord.info(LogRecordTest.class)
                    .with("sync_count", 10)
                    .with("ide_name", "IntelliJ IDEA")
                    .log("Started pushing coding sessions"));
    }

    @Test
    @DisplayName("Should create warn level log record")
    void shouldCreateWarnLevelLogRecord() {
        assertDoesNotThrow(
            () ->
                LogRecord.warn(LogRecordTest.class)
                    .with("conflict_version", 123L)
                    .with("strategy", "LWW")
                    .log("Sync conflict detected, falling back to LWW strategy"));
    }

    @Test
    @DisplayName("Should create error level log record with cause")
    void shouldCreateErrorLevelLogRecordWithCause() {
        Exception cause = new RuntimeException("Database connection failed");

        assertDoesNotThrow(
            () ->
                LogRecord.error(LogRecordTest.class)
                    .with("operation", "SELECT")
                    .with("table", "coding_sessions")
                    .cause(cause)
                    .log("Failed to execute query"));
    }

    @Test
    @DisplayName("Should support method chaining")
    void shouldSupportMethodChaining() {
        assertDoesNotThrow(
            () ->
                LogRecord.info(LogRecordTest.class)
                    .with("order_id", "ORD-12345")
                    .with("amount", 99.99)
                    .with("currency", "USD")
                    .with("user_id", "user-789")
                    .log("Order created successfully"));
    }

    @Test
    @DisplayName("Should support apply with consumer")
    void shouldSupportApplyWithConsumer() {
        assertDoesNotThrow(
            () ->
                LogRecord.info(LogRecordTest.class)
                    .apply(
                        fields -> {
                            fields.put("event_type", "page_view");
                            fields.put("page", "/dashboard");
                            fields.put("duration_ms", 150L);
                        })
                    .log("Page viewed"));
    }

    @Test
    @DisplayName("Should handle null values gracefully")
    void shouldHandleNullValuesGracefully() {
        assertDoesNotThrow(
            () ->
                LogRecord.info(LogRecordTest.class)
                    .with("null_field", null)
                    .with("valid_field", "value")
                    .log("Test with null value"));
    }

    @Test
    @DisplayName("Should support formatted messages")
    void shouldSupportFormattedMessages() {
        assertDoesNotThrow(
            () ->
                LogRecord.info(LogRecordTest.class)
                    .with("payment_id", "PAY-001")
                    .log("Payment processed: {} for user {}", "PAY-001", "user-123"));
    }

    @Test
    @DisplayName("Should create different event type loggers")
    void shouldCreateDifferentEventTypeLoggers() {
        assertDoesNotThrow(
            () -> {
                LogRecord.info(String.class).with("user_id", "u1").log("User logged in");
                LogRecord.info(Integer.class).with("count", 5).log("Data synced");
                LogRecord.info(Double.class).with("amount", 100.0).log("Payment received");
            });
    }

    @Test
    @DisplayName("Should support adding fields from map")
    void shouldSupportAddingFieldsFromMap() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("user_id", "user-123");
        fields.put("action", "update_profile");
        fields.put("timestamp", System.currentTimeMillis());

        assertDoesNotThrow(
            () ->
                LogRecord.info(LogRecordTest.class)
                    .with(fields)
                    .with("ip_address", "192.168.1.1")
                    .log("User profile updated"));
    }
}
