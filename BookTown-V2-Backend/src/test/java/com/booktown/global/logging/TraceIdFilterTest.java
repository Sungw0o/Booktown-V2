package com.booktown.global.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void keeps_safe_incoming_trace_id() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-12345678");

        filter.doFilter(request, response, assertTraceId("trace-12345678"));

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("trace-12345678");
        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void creates_trace_id_when_header_is_missing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNotBlank());

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isNotBlank();
        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    private FilterChain assertTraceId(String traceId) {
        return (request, response) -> assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isEqualTo(traceId);
    }
}
