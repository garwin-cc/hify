package com.hify.common.log;

import io.opentelemetry.sdk.trace.IdGenerator;
import org.slf4j.MDC;

import java.util.Map;

public final class TraceContext {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private TraceContext() {
    }

    public static String generateTraceId() {
        return IdGenerator.random().generateTraceId();
    }

    public static String currentTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public static String ensureTraceId() {
        String traceId = currentTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = generateTraceId();
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }

    public static Runnable wrap(Runnable task) {
        Map<String, String> capturedContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            if (capturedContext == null || capturedContext.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(capturedContext);
            }
            try {
                task.run();
            } finally {
                if (previousContext == null || previousContext.isEmpty()) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previousContext);
                }
            }
        };
    }
}
