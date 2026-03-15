/**
 * Configuration classes for application-wide settings.
 *
 * <p>This package contains infrastructure configuration including:
 *
 * <ul>
 *   <li>Jackson: JSON serialization configuration and custom serializers (e.g., {@code
 *       MaskSerializer})
 *   <li>Logging: Logback converters and filters for structured logging
 *   <li>Security: Spring Security configuration
 *   <li>Web: WebMvc configuration and interceptors
 * </ul>
 *
 * <h2>Three-Layer Data Desensitization Architecture</h2>
 *
 * <p>To comply with GDPR and personal information protection laws, sensitive data must never appear
 * in logs in plain text. We implement defense in depth:
 *
 * <h3>Layer 1: Filter Layer (Protocol)</h3>
 *
 * <ul>
 *   <li>Location: {@link com.ahogek.cttserver.common.utils.DesensitizeUtils}
 *   <li>Purpose: Mask HTTP headers (Authorization, Cookie, X-API-Key) before logging
 *   <li>Usage: Call {@code maskHeader()} in filters or controllers
 * </ul>
 *
 * <h3>Layer 2: DTO Layer (Domain)</h3>
 *
 * <ul>
 *   <li>Location: {@link com.ahogek.cttserver.common.config.jackson.MaskSerializer}
 *   <li>Purpose: Mask sensitive fields during JSON serialization
 *   <li>Usage: Annotate fields with {@code @JsonSerialize(using = MaskSerializer.class)}
 * </ul>
 *
 * <h3>Layer 3: Global Layer (Framework)</h3>
 *
 * <ul>
 *   <li>Location: {@link com.ahogek.cttserver.common.config.logging.MaskingMessageConverter}
 *   <li>Purpose: Regex-based fallback protection for accidental hardcoded sensitive data
 *   <li>Usage: Configured in {@code logback-spring.xml} via {@code %maskedMsg}
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
package com.ahogek.cttserver.common.config;
