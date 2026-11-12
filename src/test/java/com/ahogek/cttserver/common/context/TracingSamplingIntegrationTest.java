package com.ahogek.cttserver.common.context;

import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.common.LoopbackHttpProbe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that sampling decides what is exported, not what a caller sees.
 *
 * <p>Production records a tenth of the traffic. If an unsampled request lost the trace id, the log
 * line and the response header would stop describing the request for nine out of ten calls, and a
 * trace view would be the only place the identifier survived. The identifier therefore comes from
 * the span context even when the sampler drops the span's data.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-12
 */
@BaseIntegrationTest
@TestPropertySource(properties = "management.tracing.sampling.probability=0.0")
@DisplayName("Trace correlation under sampling")
class TracingSamplingIntegrationTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String UNSAMPLED_TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-00";
    private static final String PUBLIC_PATH = "/ctt-server/api/v1/config/public";

    @Value("${local.server.port}")
    private int applicationPort;

    @Test
    @DisplayName("Should keep the caller's trace id when the sampler drops the trace")
    void shouldKeepTheCallerTraceId_whenTheTraceIsNotSampled() throws Exception {
        HttpResponse<String> response =
                LoopbackHttpProbe.get(
                        applicationPort, PUBLIC_PATH, Map.of("traceparent", UNSAMPLED_TRACEPARENT));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(RequestContextInitializerFilter.TRACE_HEADER))
                .contains(TRACE_ID);
    }

    @Test
    @DisplayName("Should still generate an identifier when nothing is sampled")
    void shouldGenerateAnIdentifier_whenNothingIsSampled() throws Exception {
        HttpResponse<String> response = LoopbackHttpProbe.get(applicationPort, PUBLIC_PATH);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(RequestContextInitializerFilter.TRACE_HEADER))
                .hasValueSatisfying(traceId -> assertThat(traceId).matches("^[0-9a-f]{32}$"));
    }
}
