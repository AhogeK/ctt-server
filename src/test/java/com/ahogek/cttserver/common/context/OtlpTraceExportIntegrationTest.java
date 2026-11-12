package com.ahogek.cttserver.common.context;

import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.common.LoopbackHttpProbe;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that spans leave the process over OTLP with the identifier the caller supplied.
 *
 * <p>A stub collector stands in for the OTLP receiver so the assertion can look at the bytes the
 * exporter actually sends: the request is a real HTTP POST to {@code /v1/traces}, and the span's
 * trace id appears in its protobuf payload. That is the contract the collector relies on; whether
 * Tempo stores it is the deployment's business, not this test's.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-12
 */
@BaseIntegrationTest
@TestPropertySource(
        properties = {
            "management.tracing.export.enabled=true",
            "management.tracing.sampling.probability=1.0"
        })
@DisplayName("OTLP trace export")
class OtlpTraceExportIntegrationTest {

    private static final BlockingQueue<byte[]> EXPORTED = new LinkedBlockingQueue<>();
    private static final HttpServer COLLECTOR = startCollector();

    @LocalServerPort private int applicationPort;

    @DynamicPropertySource
    static void collectorEndpoint(DynamicPropertyRegistry registry) {
        registry.add(
                "management.opentelemetry.tracing.export.otlp.endpoint",
                () -> "http://127.0.0.1:" + COLLECTOR.getAddress().getPort() + "/v1/traces");
    }

    @AfterAll
    static void stopCollector() {
        COLLECTOR.stop(0);
    }

    @Test
    @DisplayName("Should export the span of a request under the caller's trace id")
    void shouldExportTheSpan_whenTheRequestCarriesATraceparent() throws Exception {
        HttpResponse<String> response =
                LoopbackHttpProbe.get(
                        applicationPort,
                        TraceFixtures.PUBLIC_PATH,
                        Map.of("traceparent", TraceFixtures.SAMPLED_TRACEPARENT));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(RequestContextInitializerFilter.TRACE_HEADER))
                .hasValue(TraceFixtures.TRACE_ID);
        assertThat(awaitExportCarryingTheTraceId())
                .as("an OTLP payload carrying the caller's trace id within 30 s")
                .isNotEmpty();
    }

    /**
     * Drains exports until one carries the trace id. Earlier payloads may belong to scheduled work
     * that the application exports on its own, so the first POST is not necessarily this request's.
     */
    private static byte[] awaitExportCarryingTheTraceId() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        int inspected = 0;
        while (System.nanoTime() < deadline) {
            byte[] payload = EXPORTED.poll(Duration.ofSeconds(1).toMillis(), TimeUnit.MILLISECONDS);
            if (payload == null) {
                continue;
            }
            inspected++;
            if (carriesTraceId(payload)) {
                return payload;
            }
        }
        throw new AssertionError(
                "none of the "
                        + inspected
                        + " exported payloads carried "
                        + TraceFixtures.TRACE_ID);
    }

    private static boolean carriesTraceId(byte[] payload) {
        return new String(payload, StandardCharsets.ISO_8859_1)
                .contains(new String(TraceFixtures.TRACE_ID_BYTES, StandardCharsets.ISO_8859_1));
    }

    private static HttpServer startCollector() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/v1/traces",
                    exchange -> {
                        EXPORTED.add(exchange.getRequestBody().readAllBytes());
                        exchange.sendResponseHeaders(200, -1);
                        exchange.close();
                    });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException("cannot start the stub collector", ex);
        }
    }
}
