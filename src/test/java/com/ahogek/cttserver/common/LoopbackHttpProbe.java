package com.ahogek.cttserver.common;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Calls the loopback HTTP servers of a split-port deployment.
 *
 * <p>Probes and metrics answer on the management port while the API stays on the application port,
 * so integration tests must speak HTTP to a chosen port instead of going through MockMvc on the
 * application context. The helper is public because both the configuration tests and the metrics
 * tests depend on that contract.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-11
 */
public final class LoopbackHttpProbe {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private LoopbackHttpProbe() {}

    public static int status(int port, String path) throws IOException {
        return get(port, path).statusCode();
    }

    public static String body(int port, String path) throws IOException {
        return get(port, path).body();
    }

    public static HttpResponse<String> get(int port, String path) throws IOException {
        return get(port, path, Map.of());
    }

    /** The headers overload lets a test send cross-cutting context, such as a traceparent. */
    public static HttpResponse<String> get(int port, String path, Map<String, String> headers)
            throws IOException {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("Accept", "*/*")
                        .GET();
        headers.forEach(builder::header);
        try {
            return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while requesting " + path, ex);
        }
    }
}
