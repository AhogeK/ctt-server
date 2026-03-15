package com.ahogek.cttserver.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TraceIdFilterTest {

    @Mock private HttpServletRequest request;

    @Mock private HttpServletResponse response;

    @Mock private FilterChain filterChain;

    private TraceIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TraceIdFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void doFilter_withoutHeader_generatesNewTraceId() throws Exception {
        when(request.getHeader(TraceIdFilter.TRACE_HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq(TraceIdFilter.TRACE_HEADER), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_withHeader_reusesTraceId() throws Exception {
        String existingId = "existing-trace-id";
        when(request.getHeader(TraceIdFilter.TRACE_HEADER)).thenReturn(existingId);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(TraceIdFilter.TRACE_HEADER, existingId);
    }

    @Test
    void doFilter_withBlankHeader_generatesNewTraceId() throws Exception {
        when(request.getHeader(TraceIdFilter.TRACE_HEADER)).thenReturn("   ");

        filter.doFilterInternal(request, response, filterChain);

        verify(response)
                .setHeader(eq(TraceIdFilter.TRACE_HEADER), argThat(s -> s != null && !s.isBlank()));
    }

    @Test
    void doFilter_clearsMdcAfterChain() throws Exception {
        when(request.getHeader(TraceIdFilter.TRACE_HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(MDC.get(TraceIdFilter.MDC_KEY));
    }
}
