package com.ahogek.cttserver.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapPropagator;

/**
 * Keeps W3C trace propagation working when span export is switched off.
 *
 * <p>Spring Boot only configures an OpenTelemetry propagator while {@code
 * management.tracing.export.enabled} is {@code true}: with the exporter disabled it registers a
 * no-op propagator, so an incoming {@code traceparent} is ignored and every request starts a new
 * trace. That would break correlation exactly where it is most useful - a developer running without
 * a collector - because the log line, the response header and the audit row would carry an
 * identifier no collector ever sees.
 *
 * <p>Export stays off locally and in tests (nothing is listening), so this configuration supplies
 * the W3C-only propagator in that case. When export is enabled Spring Boot's own composite
 * propagator is used instead, which also honours {@code management.tracing.propagation.*} and
 * baggage; this bean steps aside through the condition.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-12
 */
@Configuration(proxyBeanMethods = false)
class TracingPropagationConfiguration {

    @Bean
    @ConditionalOnProperty(name = "management.tracing.export.enabled", havingValue = "false")
    TextMapPropagator w3cTextMapPropagator() {
        return W3CTraceContextPropagator.getInstance();
    }
}
