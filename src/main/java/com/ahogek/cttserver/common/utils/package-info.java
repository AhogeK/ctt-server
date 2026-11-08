/**
 * Utility classes for cross-cutting concerns.
 *
 * <p>This package contains general-purpose utilities that are used throughout the application:
 *
 * <ul>
 *   <li>IP address extraction and validation
 *   <li>Sensitive data desensitization (GDPR/privacy compliance)
 * </ul>
 *
 * <h2>Data Desensitization</h2>
 *
 * <p>{@link com.ahogek.cttserver.common.utils.DesensitizeUtils} provides the first line of defense
 * in the two-layer data protection architecture:
 *
 * <ol>
 *   <li>Filter Layer: Use {@code DesensitizeUtils} to mask HTTP headers before logging
 *   <li>Global Layer: Logback {@code MaskingMessageConverter} provides regex-based fallback
 * </ol>
 *
 * <p>Protected patterns include:
 *
 * <ul>
 *   <li>HTTP Headers: Authorization, Cookie, X-API-Key, Token, X-Auth-Token
 *   <li>Personal Data: Email addresses (partial masking)
 *   <li>Credentials: Passwords, tokens, secrets (full masking)
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
package com.ahogek.cttserver.common.utils;
