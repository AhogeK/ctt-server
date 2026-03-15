/**
 * Structured logging infrastructure.
 *
 * <p>This package provides tools for recording business events and operations with structured
 * key-value pairs, enabling machine-parseable logs for analytics and monitoring systems.
 *
 * <h2>Three-Layer Logging Architecture</h2>
 *
 * <h3>1. Request Log (ACCESS_LOG)</h3>
 *
 * <ul>
 *   <li>Location: {@link com.ahogek.cttserver.common.context.RequestLoggingFilter}
 *   <li>Records: HTTP request lifecycle (method, URI, status, duration)
 *   <li>Logger: "ACCESS_LOG" (single entry point for all HTTP requests)
 *   <li>Slow requests: Logged via {@code ACCESS_LOG.atWarn()} (≥500ms threshold)
 * </ul>
 *
 * <h3>2. Business Log (via LogRecord)</h3>
 *
 * <ul>
 *   <li>Location: Use {@link com.ahogek.cttserver.common.logging.LogRecord} utility with class identity
 *   <li>Records: Domain events, state transitions, third-party RPC calls
 *   <li>Format: Structured key-value pairs via SLF4J Fluent API
 *   <li>Design: Uses actual class name for traceability (e.g., UserService.class)
 *   <li>Example:
 *       <pre>{@code
 * LogRecord.info(SyncPushService.class)
 *     .with("sync_count", sessions.size())
 *     .with("ide_name", requestInfo.userAgent())
 *     .log("Started pushing coding sessions");
 * }</pre>
 * </ul>
 *
 * <h3>3. Error Log (via GlobalExceptionHandler)</h3>
 *
 * <ul>
 *   <li>Location: {@link com.ahogek.cttserver.common.exception.GlobalExceptionHandler}
 *   <li>Business exceptions (4xx): WARN level, no stack trace
 *   <li>System errors (5xx): ERROR level, full stack trace
 *   <li>Single exit point for all error logging
 * </ul>
 *
 * <h2>Structured Logging Benefits</h2>
 *
 * <ul>
 *   <li>Machine-parseable: JSON output for ELK/Loki/ClickHouse
 *   <li>Queryable: Filter by any key-value field
 *   <li>Aggregatable: Sum/average metrics like sync_count, duration_ms
 *   <li>Correlated: All logs include trace_id for distributed tracing
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
package com.ahogek.cttserver.common.logging;
