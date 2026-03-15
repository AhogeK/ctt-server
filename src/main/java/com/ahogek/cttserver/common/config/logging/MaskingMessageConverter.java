package com.ahogek.cttserver.common.config.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Global log message desensitization converter for Logback.
 *
 * <p>This converter provides the third line of defense in the desensitization architecture by
 * applying regex-based pattern matching to log messages at the framework level. It catches
 * accidental hardcoded sensitive data that bypassed the Filter and DTO layers.
 *
 * <p><strong>Configuration in logback-spring.xml:</strong>
 *
 * <pre>{@code
 * <configuration>
 *     <conversionRule conversionWord="maskedMsg"
 *                    converterClass="com.ahogek.cttserver.common.config.logging.MaskingMessageConverter"/>
 *
 *     <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
 *         <encoder>
 *             <pattern>%maskedMsg%n</pattern>
 *         </encoder>
 *     </appender>
 * </configuration>
 * }</pre>
 *
 * <p><strong>Protected Patterns:</strong>
 *
 * <ul>
 *   <li>password, passwd
 *   <li>token
 *   <li>authorization (including Bearer/Basic prefixes)
 *   <li>cookie
 *   <li>secret
 * </ul>
 *
 * <p><strong>Performance Note:</strong> The regex pattern is pre-compiled and the matcher is
 * reused. The complexity is O(L) where L is the log message length. This overhead is acceptable for
 * the security benefits in most high-concurrency scenarios.
 *
 * <p>Three-Layer Defense:
 *
 * <ol>
 *   <li>Filter: {@code DesensitizeUtils.maskHeader()} for HTTP headers
 *   <li>DTO: {@code MaskSerializer} for object serialization
 *   <li>Global: This converter for regex-based兜底 protection
 * </ol>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
public class MaskingMessageConverter extends ClassicConverter {

    /**
     * Pattern to match sensitive key-value pairs in log messages.
     *
     * <p>For authorization headers, also matches "Bearer " or "Basic " prefixes. For other
     * sensitive keys, matches the value after various delimiters.
     *
     * <p>Matches patterns like:
     *
     * <ul>
     *   <li>password=123456
     *   <li>token: "abc"
     *   <li>authorization: Bearer eyJhbGciOiJIUzI1NiIs...
     *   <li>authorization bearer token123
     * </ul>
     */
    private static final Pattern SENSITIVE_PATTERN =
        Pattern.compile(
            "(?i)(password|passwd|token|cookie|secret)(['\"\\s:=]+)([^\\s,'\"\\]}]+)|"
                + "(?i)(authorization)(['\"\\s:=]+)((?:Bearer\\s+|Basic\\s+)?[^\\s,'\"\\]}]+)");

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null || message.isEmpty()) {
            return message;
        }

        Matcher matcher = SENSITIVE_PATTERN.matcher(message);
        if (!matcher.find()) {
            return message;
        }

        matcher.reset();
        StringBuilder sb = new StringBuilder(message.length());

        while (matcher.find()) {
            // Group 1-3 for standard sensitive keys, Group 4-6 for authorization
            String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(4);
            String delimiter = matcher.group(2) != null ? matcher.group(2) : matcher.group(5);
            matcher.appendReplacement(sb, key + delimiter + "******");
        }
        matcher.appendTail(sb);

        return sb.toString();
    }
}
