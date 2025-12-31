package com.ai.rag.observability;

import jakarta.servlet.*;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

public class TraceIdFilter implements Filter {

    public static final String TRACE_ID = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }
}
