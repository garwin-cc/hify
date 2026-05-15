package com.hify.common.metrics;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class HifyMetrics {

    private final MeterRegistry meterRegistry;
    private final Set<String> registeredCircuitBreakers = ConcurrentHashMap.newKeySet();
    private final AtomicInteger sseActiveConnections = new AtomicInteger(0);

    public HifyMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("hify_sse_active_connections", sseActiveConnections, AtomicInteger::get)
                .description("Active SSE connections")
                .register(meterRegistry);
    }

    public void recordChatRequest(Long agentId, String status, long elapsedMs) {
        Tags tags = Tags.of("agentId", tag(agentId), "status", tag(status));
        Counter.builder("hify_chat_requests")
                .description("Total chat requests")
                .tags(tags)
                .register(meterRegistry)
                .increment();
        Timer.builder("hify_chat_request_latency")
                .description("Chat request latency")
                .publishPercentileHistogram()
                .tags(tags)
                .register(meterRegistry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
    }

    public void recordLlmCall(String provider, String model, String result, long elapsedMs) {
        Tags tags = Tags.of("provider", tag(provider), "model", tag(model), "result", tag(result));
        Counter.builder("hify_llm_calls")
                .description("Total LLM calls")
                .tags(tags)
                .register(meterRegistry)
                .increment();
        Timer.builder("hify_llm_call_latency")
                .description("LLM call latency")
                .publishPercentileHistogram()
                .tags(tags)
                .register(meterRegistry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
    }

    public void recordLlmTokens(String provider, String model, int inputTokens, int outputTokens) {
        recordLlmToken(provider, model, "input", inputTokens);
        recordLlmToken(provider, model, "output", outputTokens);
    }

    public void recordRagRetrieval(String searchMode, String result, long elapsedMs) {
        Tags tags = Tags.of("searchMode", tag(searchMode), "result", tag(result));
        Counter.builder("hify_rag_retrievals")
                .description("Total RAG retrievals")
                .tags(tags)
                .register(meterRegistry)
                .increment();
        Timer.builder("hify_rag_retrieval_latency")
                .description("RAG retrieval latency")
                .publishPercentileHistogram()
                .tags(tags)
                .register(meterRegistry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
    }

    public void recordMcpToolCall(Long serverId, String toolName, String result) {
        Counter.builder("hify_mcp_tool_calls")
                .description("Total MCP tool calls")
                .tags("serverId", tag(serverId), "tool", tag(toolName), "result", tag(result))
                .register(meterRegistry)
                .increment();
    }

    public void recordWorkflowRun(String status, long elapsedMs) {
        Tags tags = Tags.of("status", tag(status));
        Counter.builder("hify_workflow_runs")
                .description("Total workflow runs")
                .tags(tags)
                .register(meterRegistry)
                .increment();
        Timer.builder("hify_workflow_run_latency")
                .description("Workflow run latency")
                .publishPercentileHistogram()
                .tags(tags)
                .register(meterRegistry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
    }

    public void incrementSseActiveConnections() {
        sseActiveConnections.incrementAndGet();
    }

    public void decrementSseActiveConnections() {
        sseActiveConnections.updateAndGet(value -> Math.max(0, value - 1));
    }

    public void registerCircuitBreakerState(String provider, CircuitBreaker circuitBreaker) {
        String key = tag(provider);
        if (!registeredCircuitBreakers.add(key)) {
            return;
        }
        registerCircuitBreakerStateGauge(key, circuitBreaker, CircuitBreaker.State.CLOSED);
        registerCircuitBreakerStateGauge(key, circuitBreaker, CircuitBreaker.State.OPEN);
        registerCircuitBreakerStateGauge(key, circuitBreaker, CircuitBreaker.State.HALF_OPEN);
    }

    private void registerCircuitBreakerStateGauge(String provider,
                                                  CircuitBreaker circuitBreaker,
                                                  CircuitBreaker.State state) {
        Gauge.builder("hify_provider_circuit_breaker_state", circuitBreaker,
                        cb -> cb.getState() == state ? 1D : 0D)
                .description("Provider circuit breaker state gauge. Current state is 1, other states are 0.")
                .tag("provider", provider)
                .tag("state", state.name())
                .register(meterRegistry);
    }

    private void recordLlmToken(String provider, String model, String direction, int tokens) {
        if (tokens <= 0) {
            return;
        }
        Counter.builder("hify_llm_tokens")
                .description("Total LLM tokens")
                .tags("provider", tag(provider), "model", tag(model), "direction", direction)
                .register(meterRegistry)
                .increment(tokens);
    }

    private static String tag(Long value) {
        return value == null ? "none" : String.valueOf(value);
    }

    private static String tag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
