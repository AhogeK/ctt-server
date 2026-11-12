package com.ahogek.cttserver.common.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.io.IOException;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.test.simple.SimpleTracer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestContextInitializerFilterTest {

    @Mock private HttpServletRequest request;

    @Mock private HttpServletResponse response;

    @Mock private FilterChain filterChain;

    private RequestContextInitializerFilter filter;

    private SimpleTracer tracer;

    @BeforeEach
    void setUp() {
        tracer = new SimpleTracer();
        filter = new RequestContextInitializerFilter("", tracerProvider(tracer));
        MDC.clear();
    }

    private static ObjectProvider<Tracer> tracerProvider(Tracer tracer) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        if (tracer != null) {
            beans.registerSingleton("tracer", tracer);
        }
        return beans.getBeanProvider(Tracer.class);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldUseTheSpanTraceId_whenTracingIsActive() throws Exception {
        stubRequest("/api/test");
        String spanTraceId = runWithSpan(filter);

        verify(response).setHeader(RequestContextInitializerFilter.TRACE_HEADER, spanTraceId);
    }

    @Test
    void shouldIgnoreTheClientTraceId_whenASpanExists() throws Exception {
        stubRequest("/api/test");
        when(request.getHeader(RequestContextInitializerFilter.TRACE_HEADER))
                .thenReturn("ffffffffffffffffffffffffffffffff");

        String spanTraceId = runWithSpan(filter);

        verify(response).setHeader(RequestContextInitializerFilter.TRACE_HEADER, spanTraceId);
    }

    @Test
    void shouldFallBackToTheClientTraceId_whenNoTracerIsPresent() throws Exception {
        RequestContextInitializerFilter withoutTracing =
                new RequestContextInitializerFilter("", tracerProvider(null));
        stubRequest("/api/test");
        when(request.getHeader(RequestContextInitializerFilter.TRACE_HEADER))
                .thenReturn("0123456789ABCDEF0123456789ABCDEF");

        withoutTracing.doFilterInternal(request, response, filterChain);

        verify(response)
                .setHeader(
                        RequestContextInitializerFilter.TRACE_HEADER,
                        "0123456789abcdef0123456789abcdef");
    }

    private void stubRequest(String uri) {
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
    }

    private String runWithSpan(RequestContextInitializerFilter target) throws Exception {
        Span span = tracer.nextSpan().name("test").start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            target.doFilterInternal(request, response, filterChain);
        } finally {
            span.end();
        }
        return span.context().traceId();
    }

    @Test
    void shouldGenerateATraceId_whenNoHeaderArrives() throws Exception {
        when(request.getHeader(RequestContextInitializerFilter.TRACE_HEADER)).thenReturn(null);
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq(RequestContextInitializerFilter.TRACE_HEADER), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldReuseTheClientTraceId_whenNoTracerIsPresent() {
        String existingTraceId = "0123456789abcdef0123456789abcdef";
        when(request.getHeader(RequestContextInitializerFilter.TRACE_HEADER))
                .thenReturn(existingTraceId);
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(RequestContextInitializerFilter.TRACE_HEADER, existingTraceId);
    }

    @Test
    void shouldGenerateATraceId_whenTheHeaderIsBlank() {
        when(request.getHeader(RequestContextInitializerFilter.TRACE_HEADER)).thenReturn("   ");
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response)
                .setHeader(
                        eq(RequestContextInitializerFilter.TRACE_HEADER),
                        argThat(s -> s != null && !s.isBlank()));
    }

    @Test
    void shouldSetHsts_whenATrustedProxyTerminatesTls() throws Exception {
        RequestContextInitializerFilter proxyAware =
                new RequestContextInitializerFilter("10.0.0.10", tracerProvider(tracer));
        when(request.getHeader(RequestContextInitializerFilter.TRACE_HEADER)).thenReturn(null);
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("10.0.0.10");
        when(request.getHeader("X-Forwarded-Proto")).thenReturn("https");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);

        proxyAware.doFilterInternal(request, response, filterChain);

        verify(response)
                .setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    }

    @Test
    void shouldPropagate_whenTheChainThrowsServletException() throws Exception {
        when(request.getHeader(RequestContextInitializerFilter.TRACE_HEADER)).thenReturn(null);
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        doThrow(new ServletException("test")).when(filterChain).doFilter(request, response);

        assertThrows(
                ServletException.class,
                () -> filter.doFilterInternal(request, response, filterChain));
    }

    @Test
    void shouldPropagate_whenTheChainThrowsIOException() throws Exception {
        when(request.getHeader(RequestContextInitializerFilter.TRACE_HEADER)).thenReturn(null);
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        doThrow(new IOException("test")).when(filterChain).doFilter(request, response);

        assertThrows(
                IOException.class, () -> filter.doFilterInternal(request, response, filterChain));
    }

    @Test
    void shouldPopulateTheMdc_whenTheRequestArrives() throws Exception {
        when(request.getHeader(RequestContextInitializerFilter.TRACE_HEADER))
                .thenReturn("0123456789abcdef0123456789abcdef");
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);

        final String[] capturedTraceId = new String[1];
        final String[] capturedClientIp = new String[1];
        final String[] capturedMethod = new String[1];
        final String[] capturedUri = new String[1];

        doAnswer(
                        _ -> {
                            capturedTraceId[0] = MDC.get(MdcKey.TRACE_ID);
                            capturedClientIp[0] = MDC.get(MdcKey.CLIENT_IP);
                            capturedMethod[0] = MDC.get(MdcKey.HTTP_METHOD);
                            capturedUri[0] = MDC.get(MdcKey.REQUEST_URI);
                            return null;
                        })
                .when(filterChain)
                .doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals("0123456789abcdef0123456789abcdef", capturedTraceId[0]);
        assertEquals("192.168.1.***", capturedClientIp[0]);
        assertEquals("POST", capturedMethod[0]);
        assertEquals("/api/test", capturedUri[0]);
    }

    @Test
    void shouldRemoveTheMdcKeys_whenTheChainReturns() {
        when(request.getHeader(RequestContextInitializerFilter.TRACE_HEADER)).thenReturn(null);
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(MDC.get(MdcKey.TRACE_ID));
        assertNull(MDC.get(MdcKey.CLIENT_IP));
        assertNull(MDC.get(MdcKey.HTTP_METHOD));
        assertNull(MDC.get(MdcKey.REQUEST_URI));
    }
}
