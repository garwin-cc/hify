package com.hify.common.log;

import io.opentelemetry.sdk.trace.IdGenerator;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

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

    public static void put(String key, Object value) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (value == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, String.valueOf(value));
    }

    public static String get(String key) {
        return MDC.get(key);
    }

    public static TraceSnapshot snapshot() {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return new TraceSnapshot(context == null ? Map.of() : new HashMap<>(context));
    }

    public static void restore(TraceSnapshot snapshot) {
        if (snapshot == null || snapshot.mdc() == null || snapshot.mdc().isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(snapshot.mdc());
    }

    public static Runnable wrap(Runnable task) {
        return wrap(snapshot(), task);
    }

    public static Runnable wrap(TraceSnapshot snapshot, Runnable task) {
        return () -> {
            TraceSnapshot previous = snapshot();
            restore(snapshot);
            try {
                task.run();
            } finally {
                restore(previous);
            }
        };
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        return wrap(snapshot(), task);
    }

    public static <T> Callable<T> wrap(TraceSnapshot snapshot, Callable<T> task) {
        return () -> {
            TraceSnapshot previous = snapshot();
            restore(snapshot);
            try {
                return task.call();
            } finally {
                restore(previous);
            }
        };
    }

    public static <T> Supplier<T> wrap(Supplier<T> supplier) {
        TraceSnapshot captured = snapshot();
        return () -> {
            TraceSnapshot previous = snapshot();
            restore(captured);
            try {
                return supplier.get();
            } finally {
                restore(previous);
            }
        };
    }

    public static void runWith(String traceId, Runnable task) {
        TraceSnapshot previous = snapshot();
        try {
            MDC.put(TRACE_ID_KEY, traceId == null || traceId.isBlank() ? generateTraceId() : traceId);
            task.run();
        } finally {
            restore(previous);
        }
    }
}
