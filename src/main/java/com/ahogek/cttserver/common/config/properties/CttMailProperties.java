package com.ahogek.cttserver.common.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        @Valid @NotNull From from,
        @Valid @NotNull Outbox outbox,
        @Valid @NotNull Retry retry,
        @Valid @NotNull Frontend frontend) {

    /**
     * Sender configuration.
     *
     * @param address Sender email address (e.g., noreply@ahogek.com)
     * @param name Sender display name (e.g., "CTT Server")
     */
    public record From(@NotBlank String address, @NotBlank String name) {}

    /**
     * Frontend URL configuration for building email links.
     *
     * @param baseUrl Frontend application base URL (e.g., https://app.cttserver.com)
     * @param verifyEmailPath Path for email verification link (e.g., /auth/verify-email)
     * @param resetPasswordPath Path for password reset link (e.g., /auth/reset-password)
     */
    public record Frontend(
            @NotBlank String baseUrl,
            @NotBlank @Pattern(regexp = "^/.*") String verifyEmailPath,
            @NotBlank @Pattern(regexp = "^/.*") String resetPasswordPath) {}

    /**
     * Outbox scheduler configuration.
     *
     * @param pollIntervalMs Polling interval for outbox table in milliseconds
     * @param batchSize Maximum emails to fetch and send per batch
     * @param zombieTimeoutSeconds Timeout in seconds to reset stuck SENDING emails to PENDING
     * @param zombieIntervalMs Interval for zombie recovery task in milliseconds
     */
    public record Outbox(
            @Positive long pollIntervalMs,
            @Min(1) int batchSize,
            @Positive int zombieTimeoutSeconds,
            @Positive long zombieIntervalMs) {}

    /**
     * Retry policy with exponential backoff and jitter.
     *
     * <p>Delay calculation: {@code delay = min(base * multiplier^(attempt), maxDelay) ± jitter}
     *
     * <p>Jitter prevents retry storms in distributed deployments by randomizing retry times within
     * a configurable range.
     *
     * <p>Example with base=60s, multiplier=2.0, jitterFactor=0.1:
     *
     * <ul>
     *   <li>Attempt 1: 60 * 2.0⁰ = 60s ± 10% = [54s, 66s]
     *   <li>Attempt 2: 60 * 2.0¹ = 120s ± 10% = [108s, 132s]
     *   <li>Attempt 3: 60 * 2.0² = 240s ± 10% = [216s, 264s]
     *   <li>Attempt 5+: capped at maxDelay ± jitter
     * </ul>
     *
     * @param baseDelaySeconds Base delay in seconds for first retry
     * @param multiplier Exponential backoff multiplier (must be >= 1.0)
     * @param maxDelaySeconds Maximum delay cap in seconds
     * @param maxAttempts Maximum retry attempts before marking as CANCELLED
     * @param jitterFactor Jitter factor for randomization (0.0 to 1.0, default 0.1 = ±10%)
     */
    public record Retry(
            @Positive int baseDelaySeconds,
            @DecimalMin("1.0") double multiplier,
            @Positive int maxDelaySeconds,
            @Min(1) int maxAttempts,
            @DecimalMin("0.0") @DecimalMax("1.0") double jitterFactor) {}
}
