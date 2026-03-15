package com.ahogek.cttserver.common.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Structured business logging utility.
 *
 * <p>Provides a fluent API for recording domain events and business operations with structured
 * key-value pairs. This enables machine-parseable logs for analytics and monitoring systems
 * (ELK/Loki/ClickHouse).
 *
 * <p><strong>Design Principle:</strong> Business logs should use the actual class name for
 * traceability. This utility wraps the standard logger to provide structured key-value logging
 * while preserving the class identity in log output.
 *
 * <p><strong>Usage Examples:</strong>
 *
 * <pre>{@code
 * // In UserService.java
 * LogRecord.info(UserService.class)
 *     .with("user_id", userId)
 *     .with("action", "created")
 *     .log("User created successfully");
 *
 * // In SyncPushService.java
 * LogRecord.info(SyncPushService.class)
 *     .with("sync_count", sessions.size())
 *     .with("ide_name", requestInfo.userAgent())
 *     .log("Started pushing coding sessions");
 *
 * // Warning with context
 * LogRecord.warn(SyncPushService.class)
 *     .with("conflict_version", e.getServerVersion())
 *     .with("strategy", "LWW")
 *     .log("Sync conflict detected, falling back to LWW strategy");
 * }</pre>
 *
 * <p><strong>Best Practices:</strong>
 *
 * <ul>
 *   <li>Always pass {@code Xxx.class} to identify the source class
 *   <li>Include business-relevant IDs (userId, sessionId, etc.)
 *   <li>Use numeric values for metrics that might be aggregated
 *   <li>Avoid logging PII (personally identifiable information)
 *   <li>Keep message templates static (don't concatenate variables)
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
public final class LogRecord {

    private static final String LEVEL_KEY = "_level";
    private static final String INFO_LEVEL = "INFO";
    private static final String WARN_LEVEL = "WARN";
    private static final String ERROR_LEVEL = "ERROR";

    private final Logger logger;
    private final Map<String, Object> fields = new HashMap<>();
    private Throwable cause;

    private LogRecord(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    /**
     * Creates an INFO level log record for the given class.
     *
     * @param clazz the source class (e.g., {@code UserService.class})
     * @return a new LogRecord instance
     */
    public static LogRecord info(Class<?> clazz) {
        return new LogRecord(clazz);
    }

    /**
     * Creates a WARN level log record for the given class.
     *
     * @param clazz the source class (e.g., {@code UserService.class})
     * @return a new LogRecord instance
     */
    public static LogRecord warn(Class<?> clazz) {
        LogRecord logRecord = new LogRecord(clazz);
        logRecord.fields.put(LEVEL_KEY, WARN_LEVEL);
        return logRecord;
    }

    /**
     * Creates an ERROR level log record for the given class.
     *
     * @param clazz the source class (e.g., {@code UserService.class})
     * @return a new LogRecord instance
     */
    public static LogRecord error(Class<?> clazz) {
        LogRecord logRecord = new LogRecord(clazz);
        logRecord.fields.put(LEVEL_KEY, ERROR_LEVEL);
        return logRecord;
    }

    /**
     * Adds a structured key-value field to this log record.
     *
     * @param key   the field name
     * @param value the field value
     * @return this LogRecord for method chaining
     */
    public LogRecord with(String key, Object value) {
        this.fields.put(key, value);
        return this;
    }

    /**
     * Adds multiple fields from a map.
     *
     * @param fields map of field names to values
     * @return this LogRecord for method chaining
     */
    public LogRecord with(Map<String, Object> fields) {
        this.fields.putAll(fields);
        return this;
    }

    /**
     * Sets the exception cause for this log record.
     *
     * @param cause the exception
     * @return this LogRecord for method chaining
     */
    public LogRecord cause(Throwable cause) {
        this.cause = cause;
        return this;
    }

    /**
     * Executes a consumer to add multiple fields in a block.
     *
     * <pre>{@code
     * LogRecord.info(OrderService.class)
     *     .apply(fields -> {
     *         fields.put("order_id", orderId);
     *         fields.put("amount", amount);
     *         fields.put("currency", "USD");
     *     })
     *     .log("Order created");
     * }</pre>
     *
     * @param consumer function to populate fields
     * @return this LogRecord for method chaining
     */
    public LogRecord apply(Consumer<Map<String, Object>> consumer) {
        consumer.accept(this.fields);
        return this;
    }

    /**
     * Outputs the log record at the appropriate level.
     *
     * @param message the log message (should be static, not concatenated)
     * @param args    optional message format arguments
     */
    public void log(String message, Object... args) {
        String level = (String) fields.getOrDefault(LEVEL_KEY, INFO_LEVEL);
        fields.remove(LEVEL_KEY);

        var builder =
            switch (level) {
                case WARN_LEVEL -> logger.atWarn();
                case ERROR_LEVEL -> logger.atError();
                default -> logger.atInfo();
            };

        if (cause != null) {
            builder = builder.setCause(cause);
        }

        // Add all structured fields using explicit reassignment to maintain fluent chain
        for (var entry : fields.entrySet()) {
            builder = builder.addKeyValue(entry.getKey(), entry.getValue());
        }

        // Log with formatted message
        builder.log(message, args);
    }
}
