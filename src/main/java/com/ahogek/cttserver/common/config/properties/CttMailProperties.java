package com.ahogek.cttserver.common.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Mail configuration properties for CTT Server.
 *
 * <p>Provides type-safe access to mail-related configuration including sender information, outbox
 * scheduler settings, and retry policy with exponential backoff.
 *
 * @author AhogeK
 * @since 2026-03-18
 */
@ConfigurationProperties(prefix = "ctt.mail")
@Validated
public record CttMailProperties(
        @Valid @NotNull From from, @Valid @NotNull Outbox outbox, @Valid @NotNull Retry retry) {

    /**
     * Sender configuration.
     *
     * @param address Sender email address (e.g., noreply@ahogek.com)
     * @param name Sender display name (e.g., "CTT Server")
     */
    public record From(@NotBlank String address, @NotBlank String name) {}

    /**
     * Outbox scheduler configuration.
     *
     * @param pollIntervalMs Polling interval for outbox table in milliseconds
     * @param batchSize Maximum emails to fetch and send per batch
     * @param zombieTimeoutSeconds Timeout in seconds to reset stuck PROCESSING emails to PENDING
     */
    public record Outbox(
            @Positive long pollIntervalMs,
            @Min(1) int batchSize,
            @Positive int zombieTimeoutSeconds) {}

    /**
     * Retry policy with exponential backoff.
     *
     * <p>Delay calculation: {@code delay = min(base * multiplier^(attempt-1), maxDelay)}
     *
     * <p>Example with base=10s, multiplier=2.0:
     *
     * <ul>
     *   <li>Attempt 1: 10s
     *   <li>Attempt 2: 10 * 2.0¹ = 20s
     *   <li>Attempt 3: 10 * 2.0² = 40s
     *   <li>Attempt 4: 10 * 2.0³ = 80s
     *   <li>Attempt 5+: capped at maxDelay
     * </ul>
     *
     * @param baseDelaySeconds Base delay in seconds for first retry
     * @param multiplier Exponential backoff multiplier (must be >= 1.0)
     * @param maxDelaySeconds Maximum delay cap in seconds
     * @param maxAttempts Maximum retry attempts before marking as DEAD
     */
    public record Retry(
            @Positive int baseDelaySeconds,
            @DecimalMin("1.0") double multiplier,
            @Positive int maxDelaySeconds,
            @Min(1) int maxAttempts) {}
}
