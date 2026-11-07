package com.ahogek.cttserver.common.config.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.OutputStreamAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MaskingJsonEncoder}.
 *
 * <p>Exercises the real Logback pipeline: a {@link LoggerContext} with the encoder under test and
 * an in-memory appender. The JSON assertions therefore describe what production would emit.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-08
 */
@DisplayName("MaskingJsonEncoder - production JSON masking")
class MaskingJsonEncoderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LoggerContext context;
    private ByteArrayOutputStream output;
    private Logger logger;

    @BeforeEach
    void setUp() {
        context = new LoggerContext();
        // A hand-built context has no MDC adapter; the JSON encoder reads it for every event.
        context.setMDCAdapter(new LogbackMDCAdapter());
        context.start();
        output = new ByteArrayOutputStream();

        MaskingJsonEncoder encoder = new MaskingJsonEncoder();
        encoder.setContext(context);
        encoder.start();

        OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.setOutputStream(output);
        appender.start();

        logger = context.getLogger("test.masking");
        logger.setAdditive(false);
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        context.getMDCAdapter().clear();
        context.stop();
    }

    @Test
    @DisplayName("Should mask credentials carried by the message itself")
    void shouldMaskCredentials_whenMessageCarriesThem() throws Exception {
        logger.info("login rejected password=hunter2 token=abc123xyz");

        String json = lastEvent();
        assertThat(json).doesNotContain("hunter2").doesNotContain("abc123xyz").contains("******");

        JsonNode event = MAPPER.readTree(json);
        assertThat(event.path("message").asText())
                .contains("password=******")
                .contains("token=******");
    }

    @Test
    @DisplayName("Should mask key/value values that a pattern never sees")
    void shouldMaskKeyValueValues_whenQueryStringCarriesAToken() throws Exception {
        logger.atInfo()
                .addKeyValue("query_string", "token=hunter2&email=a@b.c")
                .log("GET /api/v1/auth/verify-email");

        String json = lastEvent();
        assertThat(json).doesNotContain("hunter2");

        JsonNode event = MAPPER.readTree(json);
        assertThat(event.path("message").asText()).isEqualTo("GET /api/v1/auth/verify-email");
        assertThat(event.path("kvpList")).hasSize(1);
        assertThat(event.path("kvpList").get(0).path("query_string").asText())
                .isEqualTo("token=******");
    }

    @Test
    @DisplayName("Should keep the schema, MDC and key/value pairs for clean events")
    void shouldKeepSchemaAndMdc_whenEventIsClean() throws Exception {
        context.getMDCAdapter().put("traceId", "trace-42");
        logger.atInfo().addKeyValue("uri", "/api/v1/users").log("session synced for {}", "ahogek");

        String json = lastEvent();
        assertThat(json).doesNotContain("******");

        JsonNode event = MAPPER.readTree(json);
        assertThat(event.path("message").asText()).isEqualTo("session synced for {}");
        assertThat(event.path("arguments").get(0).asText()).isEqualTo("ahogek");
        assertThat(event.path("mdc").path("traceId").asText()).isEqualTo("trace-42");
        assertThat(event.path("kvpList").get(0).path("uri").asText()).isEqualTo("/api/v1/users");
        assertThat(event.path("level").asText()).isEqualTo("INFO");
    }

    @Test
    @DisplayName("Should publish masked text and drop arguments when a secret arrives in one")
    void shouldKeepTemplate_whenSecretArrivesOnlyInArguments() throws Exception {
        logger.info("password={} rejected", "hunter2");

        String json = lastEvent();
        assertThat(json).doesNotContain("hunter2");

        JsonNode event = MAPPER.readTree(json);
        assertThat(event.path("message").asText()).isEqualTo("password=****** rejected");
        assertThat(event.has("arguments")).isFalse();
    }

    private String lastEvent() {
        String text = output.toString(StandardCharsets.UTF_8);
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        String last = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    last = text.substring(start, i + 1);
                    start = -1;
                }
            }
        }
        assertThat(last).as("no complete JSON event captured, raw=%s", text).isNotNull();
        return last;
    }
}
