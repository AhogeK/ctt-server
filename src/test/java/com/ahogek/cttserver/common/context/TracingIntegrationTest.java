package com.ahogek.cttserver.common.context;

import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.common.LoopbackHttpProbe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the request-to-trace correlation the service publishes.
 *
 * <p>The gateway sends a W3C {@code traceparent}; the server span continues that trace, and the
 * {@code X-Trace-Id} response header reports the identifier the collector will store. A caller
 * cannot replace it with {@code X-Trace-Id}: doing so would let a client detach its logs from the
 * exported trace. The request goes through the real servlet container because filter ordering is
 * part of the contract - the observation filter must run before this application's filter.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-12
 */
@BaseIntegrationTest
@DisplayName("Tracing integration")
class TracingIntegrationTest {

    private static final String CONFLICTING_CLIENT_TRACE_ID = "ffffffffffffffffffffffffffffffff";

    @Value("${local.server.port}")
    private int applicationPort;

    @Test
    @DisplayName("Should continue the incoming trace and report its identifier")
    void shouldContinueTheIncomingTrace_whenTraceparentIsPresent() throws IOException {
        HttpResponse<String> response =
                LoopbackHttpProbe.get(
                        applicationPort,
                        TraceFixtures.PUBLIC_PATH,
                        Map.of(
                                "traceparent",
                                TraceFixtures.SAMPLED_TRACEPARENT,
                                RequestContextInitializerFilter.TRACE_HEADER,
                                CONFLICTING_CLIENT_TRACE_ID));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(RequestContextInitializerFilter.TRACE_HEADER))
                .hasValue(TraceFixtures.TRACE_ID);
    }

    @Test
    @DisplayName("Should generate a fresh identifier when the caller sends no trace context")
    void shouldGenerateAnIdentifier_whenNoTraceContextIsPresent() throws IOException {
        HttpResponse<String> response =
                LoopbackHttpProbe.get(applicationPort, TraceFixtures.PUBLIC_PATH);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(RequestContextInitializerFilter.TRACE_HEADER))
                .hasValueSatisfying(
                        traceId ->
                                assertThat(traceId)
                                        .matches("^[0-9a-f]{32}$")
                                        .isNotEqualTo(TraceFixtures.TRACE_ID));
    }
}
