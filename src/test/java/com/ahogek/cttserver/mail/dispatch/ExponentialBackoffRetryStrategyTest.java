package com.ahogek.cttserver.mail.dispatch;

import com.ahogek.cttserver.common.config.properties.CttMailProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ExponentialBackoffRetryStrategyTest {

    private static final int BASE_DELAY_SECONDS = 60;
    private static final double MULTIPLIER = 2.0;
    private static final int MAX_DELAY_SECONDS = 3600;
    private static final int MAX_ATTEMPTS = 5;
    private static final double JITTER_FACTOR = 0.1;

    private ExponentialBackoffRetryStrategy strategy;

    @BeforeEach
    void setUp() {
        CttMailProperties properties =
                new CttMailProperties(
                        new CttMailProperties.From("test@localhost", "CTT Test"),
                        new CttMailProperties.Outbox(5000, 50, 300, 120000),
                        new CttMailProperties.Retry(
                                BASE_DELAY_SECONDS,
                                MULTIPLIER,
                                MAX_DELAY_SECONDS,
                                MAX_ATTEMPTS,
                                JITTER_FACTOR),
                        new CttMailProperties.Frontend("http://localhost:5173"));

        strategy = new ExponentialBackoffRetryStrategy(properties);
    }

    @Nested
    @DisplayName("calculateNextRetryTime")
    class CalculateNextRetryTimeTests {

        @Test
        @DisplayName("should return future instant")
        void shouldReturnFutureInstant() {
            Instant before = Instant.now();
            Instant result = strategy.calculateNextRetryTime(0);
            Instant after = Instant.now();

            assertThat(result).isAfterOrEqualTo(before);
            assertThat(result).isBeforeOrEqualTo(after.plusSeconds(MAX_DELAY_SECONDS + 1));
        }
    }

    @Nested
    @DisplayName("calculateDelaySeconds")
    class CalculateDelaySecondsTests {

        @Test
        @DisplayName("should calculate exponential delay for first retry")
        void shouldCalculateFirstRetry() {
            long delay = strategy.calculateDelaySeconds(0);

            assertThat(delay)
                    .isGreaterThanOrEqualTo((long) (BASE_DELAY_SECONDS * (1 - JITTER_FACTOR)))
                    .isLessThanOrEqualTo((long) (BASE_DELAY_SECONDS * (1 + JITTER_FACTOR)));
        }

        @Test
        @DisplayName("should calculate exponential delay for subsequent retries")
        void shouldCalculateSubsequentRetry() {
            long delay = strategy.calculateDelaySeconds(1);

            long expectedBase = BASE_DELAY_SECONDS * 2;
            assertThat(delay)
                    .isGreaterThanOrEqualTo((long) (expectedBase * (1 - JITTER_FACTOR)))
                    .isLessThanOrEqualTo((long) (expectedBase * (1 + JITTER_FACTOR)));
        }

        @Test
        @DisplayName("should cap delay at max delay seconds")
        void shouldCapAtMaxDelay() {
            long delay = strategy.calculateDelaySeconds(10);

            assertThat(delay)
                    .isGreaterThanOrEqualTo((long) (MAX_DELAY_SECONDS * (1 - JITTER_FACTOR)))
                    .isLessThanOrEqualTo((long) (MAX_DELAY_SECONDS * (1 + JITTER_FACTOR)));
        }

        @Test
        @DisplayName("should include jitter in delay")
        void shouldIncludeJitter() {
            long minDelay = Long.MAX_VALUE;
            long maxDelay = Long.MIN_VALUE;

            for (int i = 0; i < 100; i++) {
                long delay = strategy.calculateDelaySeconds(0);
                minDelay = Math.min(minDelay, delay);
                maxDelay = Math.max(maxDelay, delay);
            }

            assertThat(minDelay).isLessThan(maxDelay);
        }
    }

    @Nested
    @DisplayName("isRetriesExhausted")
    class IsRetriesExhaustedTests {

        @Test
        @DisplayName("should return false when retries not exhausted")
        void shouldReturnFalseWhenNotExhausted() {
            assertThat(strategy.isRetriesExhausted(0)).isFalse();
            assertThat(strategy.isRetriesExhausted(4)).isFalse();
        }

        @Test
        @DisplayName("should return true when retries exhausted")
        void shouldReturnTrueWhenExhausted() {
            assertThat(strategy.isRetriesExhausted(5)).isTrue();
            assertThat(strategy.isRetriesExhausted(10)).isTrue();
        }
    }

    @Nested
    @DisplayName("getDelayRange")
    class GetDelayRangeTests {

        @Test
        @DisplayName("should return correct range for first retry")
        void shouldReturnCorrectRangeForFirstRetry() {
            long[] range = strategy.getDelayRange(0);

            long expectedMin = (long) (BASE_DELAY_SECONDS * (1 - JITTER_FACTOR));
            long expectedMax = (long) (BASE_DELAY_SECONDS * (1 + JITTER_FACTOR));

            assertThat(range[0]).isEqualTo(expectedMin);
            assertThat(range[1]).isEqualTo(expectedMax);
        }

        @Test
        @DisplayName("should return capped range when delay exceeds max")
        void shouldReturnCappedRange() {
            long[] range = strategy.getDelayRange(10);

            long expectedMin = (long) (MAX_DELAY_SECONDS * (1 - JITTER_FACTOR));
            long expectedMax = (long) (MAX_DELAY_SECONDS * (1 + JITTER_FACTOR));

            assertThat(range[0]).isEqualTo(expectedMin);
            assertThat(range[1]).isEqualTo(expectedMax);
        }
    }

    @Nested
    @DisplayName("getMaxAttempts")
    class GetMaxAttemptsTests {

        @Test
        @DisplayName("should return configured max attempts")
        void shouldReturnMaxAttempts() {
            assertThat(strategy.getMaxAttempts()).isEqualTo(MAX_ATTEMPTS);
        }
    }
}
