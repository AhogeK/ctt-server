package com.ahogek.cttserver.common.ratelimit.core;

import com.ahogek.cttserver.common.config.properties.SecurityProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Installation-wide request ceiling, applied in front of the per-dimension buckets.
 *
 * <p>The per-dimension buckets isolate callers from each other: a user (or an IP) that polls too
 * often can only exhaust its own budget. This guard is the counterpart: it caps the total number of
 * rate-limited requests a single application instance accepts per second, so a large fleet of
 * individually well-behaved clients cannot drive the database past the instance's capacity.
 *
 * <p>It is an in-process fixed-window counter, not a shared Redis counter: the guard protects one
 * instance, and the effective ceiling of an N-instance deployment is N times the configured value.
 * A fleet-wide ceiling belongs at the edge (reverse proxy or load balancer), which is also the only
 * layer that can see traffic this application never receives.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-05
 */
@Component
public class GlobalRequestGuard {

    private final int limitPerSecond;
    private final Clock clock;
    private final AtomicLong windowStartSecond = new AtomicLong(0);
    private final AtomicLong counter = new AtomicLong(0);

    /**
     * Production constructor: system clock, ceiling from configuration.
     *
     * <p>Deliberately the only constructor Spring can see. The second one takes a {@link Clock} for
     * tests; two constructors would leave the container with no unique choice and it would fall
     * back to searching for a no-arg constructor that does not exist (integration tests failed on
     * exactly that on 2026-11-05). Tests construct the class directly from their own package.
     *
     * @param securityProperties the rate-limit configuration
     */
    @Autowired
    public GlobalRequestGuard(SecurityProperties securityProperties) {
        this(securityProperties, Clock.systemUTC());
    }

    GlobalRequestGuard(SecurityProperties securityProperties, Clock clock) {
        this.limitPerSecond = securityProperties.rateLimit().globalMaxRequestsPerSecond();
        this.clock = clock;
    }

    /**
     * Registers one request against the current one-second window.
     *
     * <p>The window is a fixed wall-clock second: the counter resets when the second changes, so
     * the ceiling reads as "requests per second", not "requests per rolling second".
     *
     * @return {@code true} when the request is within the ceiling
     */
    public boolean tryAcquire() {
        long nowSecond = clock.instant().getEpochSecond();
        long start = windowStartSecond.get();
        if (start != nowSecond && windowStartSecond.compareAndSet(start, nowSecond)) {
            counter.set(0);
        }
        return counter.incrementAndGet() <= limitPerSecond;
    }

    /**
     * Returns the configured ceiling.
     *
     * @return maximum accepted rate-limited requests per second, per instance
     */
    public int limitPerSecond() {
        return limitPerSecond;
    }
}
