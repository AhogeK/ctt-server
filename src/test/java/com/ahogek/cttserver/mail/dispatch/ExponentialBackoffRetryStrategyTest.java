package com.ahogek.cttserver.mail.dispatch;

import com.ahogek.cttserver.common.config.properties.CttMailProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ExponentialBackoffRetryStrategyTest {

    private static final int BASE_DELAY_SECONDS = 60;
    private static final double MULTIPLIER = 2.0;
    private static final int MAX_DELAY_SECONDS = 3600;
    private static final int MAX_ATTEMPTS = 5;
    private static final double JITTER_FACTOR = 0.1;

    private static final int MONTE_CARLO_ITERATIONS = 1000;

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
    @DisplayName("Monte Carlo Boundary Tests")
    class MonteCarloBoundaryTests {

        private void assertBoundsWithJitter(int retryCount, long expectedMinSeconds, long expectedMaxSeconds) {
            for (int i = 0; i < MONTE_CARLO_ITERATIONS; i++) {
                Instant start = Instant.now();
                Instant nextRetryTime = strategy.calculateNextRetryTime(retryCount);
                long actualDelaySeconds = Duration.between(start, nextRetryTime).getSeconds();

                assertThat(actualDelaySeconds)
                        .as(
                                "Retry count %d: delay %d should be between [%d, %d] (iteration %d)",
                                retryCount, actualDelaySeconds, expectedMinSeconds, expectedMaxSeconds, i)
                        .isGreaterThanOrEqualTo(expectedMinSeconds)
                        .isLessThanOrEqualTo(expectedMaxSeconds);
            }
        }

        @Test
        @DisplayName("0 retries (first failure): delay bounds [54s, 66s]")
        void boundsFor0Retries() {
            assertBoundsWithJitter(0, 54L, 66L);
        }

        @Test
        @DisplayName("1 retry: delay bounds [108s, 132s]")
        void boundsFor1Retry() {
            assertBoundsWithJitter(1, 108L, 132L);
        }

        @Test
        @DisplayName("3 retries: delay bounds [432s, 528s]")
        void boundsFor3Retries() {
            assertBoundsWithJitter(3, 432L, 528L);
        }

        @Test
        @DisplayName("5 retries: delay bounds [1728s, 2112s]")
        void boundsFor5Retries() {
            assertBoundsWithJitter(5, 1728L, 2112L);
        }

        @Test
        @DisplayName("Max delay capping (10 retries): delay bounds [3240s, 3960s]")
        void boundsForMaxDelayCapping() {
            assertBoundsWithJitter(10, 3240L, 3960L);
        }
    }

    @Nested
    @DisplayName("calculateDelaySeconds")
    class CalculateDelaySecondsTests {

        @Test
        @DisplayName("should include jitter variation across multiple calls")
        void shouldIncludeJitter() {
            long minDelay = Long.MAX_VALUE;
            long maxDelay = Long.MIN_VALUE;

            for (int i = 0; i < MONTE_CARLO_ITERATIONS; i++) {
                long delay = strategy.calculateDelaySeconds(0);
                minDelay = Math.min(minDelay, delay);
                maxDelay = Math.max(maxDelay, delay);
            }

            assertThat(minDelay)
                    .isLessThan(maxDelay)
                    .isGreaterThanOrEqualTo((long) (BASE_DELAY_SECONDS * (1 - JITTER_FACTOR)));
            assertThat(maxDelay).isLessThanOrEqualTo((long) (BASE_DELAY_SECONDS * (1 + JITTER_FACTOR)));
        }

        @Test
        @DisplayName("should cap delay at max delay seconds")
        void shouldCapAtMaxDelay() {
            for (int i = 0; i < MONTE_CARLO_ITERATIONS; i++) {
                long delay = strategy.calculateDelaySeconds(10);

                assertThat(delay)
                        .isGreaterThanOrEqualTo((long) (MAX_DELAY_SECONDS * (1 - JITTER_FACTOR)))
                        .isLessThanOrEqualTo((long) (MAX_DELAY_SECONDS * (1 + JITTER_FACTOR)));
            }
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
