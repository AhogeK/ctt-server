package com.ahogek.cttserver.common.context;

import java.util.HexFormat;

/**
 * One request shape shared by the trace-correlation tests.
 *
 * <p>The identifier, the header and the path have to agree across three tests: one checks the
 * response header, one inspects the exported payload, one drives the sampler to zero. Drifting the
 * values apart would not fail a build, it would quietly stop testing the same request.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-12
 */
final class TraceFixtures {

    static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    static final String PARENT_SPAN_ID = "00f067aa0ba902b7";
    static final String SAMPLED_TRACEPARENT = "00-" + TRACE_ID + "-" + PARENT_SPAN_ID + "-01";
    static final String UNSAMPLED_TRACEPARENT = "00-" + TRACE_ID + "-" + PARENT_SPAN_ID + "-00";
    static final String PUBLIC_PATH = "/ctt-server/api/v1/config/public";
    static final byte[] TRACE_ID_BYTES = HexFormat.of().parseHex(TRACE_ID);

    private TraceFixtures() {}
}
