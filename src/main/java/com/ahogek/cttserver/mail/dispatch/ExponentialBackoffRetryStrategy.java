package com.ahogek.cttserver.mail.dispatch;

import com.ahogek.cttserver.common.config.properties.CttMailProperties;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff retry strategy with jitter for distributed mail delivery.
 *
 * <p>Prevents retry storms (Thundering Herd) when external services (e.g., SMTP gateways)
 * experience transient failures. By introducing randomized jitter, multiple instances retry at
 * different times, allowing downstream services to recover.
 *
 * <p>Formula: {@code delay = min(base * multiplier^attempt, maxDelay) ± (delay * jitterFactor)}
 *
 * @author AhogeK
 * @since 2026-03-21
 * @see com.ahogek.cttserver.mail.service.MailOutboxProcessor
 */
@Component
public class ExponentialBackoffRetryStrategy {

    private final CttMailProperties.Retry retryConfig;

    public ExponentialBackoffRetryStrategy(CttMailProperties mailProperties) {
        this.retryConfig = mailProperties.retry();
    }

    /**
     * Calculates the next retry time with exponential backoff and jitter.
     *
     * @param currentRetryCount the current retry count (0 = first retry after initial failure)
     * @return the timestamp for the next retry attempt
     */
    public Instant calculateNextRetryTime(int currentRetryCount) {
        long delaySeconds = calculateDelaySeconds(currentRetryCount);
        return Instant.now().plusSeconds(delaySeconds);
    }

    /**
     * Calculates the delay in seconds using exponential backoff with jitter.
     *
     * <p>Visible for testing.
     *
     * @param currentRetryCount the current retry count
     * @return delay in seconds with jitter applied
     */
    long calculateDelaySeconds(int currentRetryCount) {
        double baseDelay = retryConfig.baseDelaySeconds();
        double multiplier = retryConfig.multiplier();
        double maxDelay = retryConfig.maxDelaySeconds();
        double jitterFactor = retryConfig.jitterFactor();

        double calculatedDelay = baseDelay * Math.pow(multiplier, currentRetryCount);
        double cappedDelay = Math.min(calculatedDelay, maxDelay);

        double jitter =
                cappedDelay * jitterFactor * (ThreadLocalRandom.current().nextDouble(2) - 1.0);

        return Math.max(0, (long) (cappedDelay + jitter));
    }

    /**
     * Checks if the maximum retry attempts have been exhausted.
     *
     * @param currentRetryCount the current retry count
     * @return true if retries are exhausted, false otherwise
     */
    public boolean isRetriesExhausted(int currentRetryCount) {
        return currentRetryCount >= retryConfig.maxAttempts();
    }

    /**
     * Returns the configured maximum retry attempts.
     *
     * @return the maximum retry attempts
     */
    public int getMaxAttempts() {
        return retryConfig.maxAttempts();
    }

    /**
     * Returns the delay range for a given retry count (for observability/logging).
     *
     * @param currentRetryCount the current retry count
     * @return array of [minDelay, maxDelay] in seconds
     */
    public long[] getDelayRange(int currentRetryCount) {
        double baseDelay = retryConfig.baseDelaySeconds();
        double multiplier = retryConfig.multiplier();
        double maxDelay = retryConfig.maxDelaySeconds();
        double jitterFactor = retryConfig.jitterFactor();

        double calculatedDelay = baseDelay * Math.pow(multiplier, currentRetryCount);
        double cappedDelay = Math.min(calculatedDelay, maxDelay);
        double jitterAmount = cappedDelay * jitterFactor;

        return new long[] {
            Math.max(0, (long) (cappedDelay - jitterAmount)), (long) (cappedDelay + jitterAmount)
        };
    }
}
