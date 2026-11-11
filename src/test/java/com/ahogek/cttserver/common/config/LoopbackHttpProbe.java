package com.ahogek.cttserver.common.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Calls the loopback HTTP servers of a split-port deployment.
 *
 * <p>Probes and metrics answer on the management port while the API stays on the application port,
 * so integration tests must speak HTTP to a chosen port instead of going through MockMvc on the
 * application context.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-11
 */
final class LoopbackHttpProbe {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private LoopbackHttpProbe() {}

    static int status(int port, String path) throws IOException {
        return get(port, path).statusCode();
    }

    static String body(int port, String path) throws IOException {
        return get(port, path).body();
    }

    static HttpResponse<String> get(int port, String path) throws IOException {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("Accept", "*/*")
                        .GET()
                        .build();
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while requesting " + path, ex);
        }
    }
}
