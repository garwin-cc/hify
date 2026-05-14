package com.hify.common.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void wrapsCallableAndRestoresPreviousContext() throws Exception {
        MDC.put(TraceContext.TRACE_ID_KEY, "outer");
        TraceSnapshot snapshot = TraceContext.snapshot();

        Callable<String> callable = TraceContext.wrap(snapshot, () -> {
            assertThat(TraceContext.currentTraceId()).isEqualTo("outer");
            MDC.put("workflowRunId", "10");
            return MDC.get("workflowRunId");
        });

        MDC.put(TraceContext.TRACE_ID_KEY, "current");
        assertThat(callable.call()).isEqualTo("10");
        assertThat(TraceContext.currentTraceId()).isEqualTo("current");
        assertThat(MDC.get("workflowRunId")).isNull();
    }
}
