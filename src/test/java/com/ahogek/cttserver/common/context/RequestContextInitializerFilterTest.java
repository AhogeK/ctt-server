package com.ahogek.cttserver.common.context;

import jakarta.servlet.FilterChain;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestContextInitializerFilterTest {

    @Mock private HttpServletRequest request;

    @Mock private HttpServletResponse response;

    @Mock private FilterChain filterChain;

    private RequestContextInitializerFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestContextInitializerFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void doFilter_withoutHeaders_generatesNewTraceId() throws Exception {
        when(request.getHeader(RequestContextInitializerFilter.TRACE_HEADER)).thenReturn(null);
        when(request.getHeader(RequestContextInitializerFilter.DEVICE_ID_HEADER)).thenReturn(null);
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
    void doFilter_withTraceIdHeader_reusesTraceId() {
        String existingTraceId = "existing-trace-id-123";
        when(request.getHeader(RequestContextInitializerFilter.TRACE_HEADER))
                .thenReturn(existingTraceId);
        when(request.getHeader(RequestContextInitializerFilter.DEVICE_ID_HEADER))
                .thenReturn("device-456");
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
    void doFilter_withBlankHeader_generatesNewTraceId() {
        when(request.getHeader(RequestContextInitializerFilter.TRACE_HEADER)).thenReturn("   ");
        when(request.getHeader(RequestContextInitializerFilter.DEVICE_ID_HEADER)).thenReturn(null);
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
    void doFilter_clearsMdcAfterChain() {
        when(request.getHeader(RequestContextInitializerFilter.TRACE_HEADER)).thenReturn(null);
        when(request.getHeader(RequestContextInitializerFilter.DEVICE_ID_HEADER)).thenReturn(null);
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(MDC.get(RequestContextInitializerFilter.MDC_TRACE_KEY));
    }

    @Test
    void doFilter_whenChainThrowsServletException_propagates() throws Exception {
        when(request.getHeader(RequestContextInitializerFilter.TRACE_HEADER)).thenReturn(null);
        when(request.getHeader(RequestContextInitializerFilter.DEVICE_ID_HEADER)).thenReturn(null);
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        doThrow(new jakarta.servlet.ServletException("test"))
                .when(filterChain)
                .doFilter(request, response);

        assertThrows(
                jakarta.servlet.ServletException.class,
                () -> filter.doFilterInternal(request, response, filterChain));
    }

    @Test
    void doFilter_whenChainThrowsIOException_propagates() throws Exception {
        when(request.getHeader(RequestContextInitializerFilter.TRACE_HEADER)).thenReturn(null);
        when(request.getHeader(RequestContextInitializerFilter.DEVICE_ID_HEADER)).thenReturn(null);
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        doThrow(new java.io.IOException("test")).when(filterChain).doFilter(request, response);

        assertThrows(
                java.io.IOException.class,
                () -> filter.doFilterInternal(request, response, filterChain));
    }
}
