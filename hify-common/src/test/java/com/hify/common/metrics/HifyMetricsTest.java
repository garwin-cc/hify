package com.hify.common.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HifyMetricsTest {

    @Test
    void recordsStage7ProductionMetricsWithHifyPrefix() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HifyMetrics metrics = new HifyMetrics(registry);

        metrics.incrementSseActiveConnections();
        metrics.recordLlmTokens("OPENAI", "gpt-4o", 11, 7);
        metrics.recordRagRetrieval("HYBRID", "success", 123);
        metrics.recordWorkflowRun("SUCCESS", 456);

        assertThat(registry.get("hify_sse_active_connections").gauge().value()).isEqualTo(1D);
        assertThat(registry.get("hify_llm_tokens").tag("direction", "input").counter().count()).isEqualTo(11D);
        assertThat(registry.get("hify_llm_tokens").tag("direction", "output").counter().count()).isEqualTo(7D);
        assertThat(registry.get("hify_rag_retrieval_latency").timer().count()).isEqualTo(1);
        assertThat(registry.get("hify_workflow_runs").counter().count()).isEqualTo(1D);
    }
}
