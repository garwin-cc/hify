package com.hify.model.api;

import lombok.Data;

@Data
public class LlmUsageStatsResp {

    private String groupKey;
    private String groupName;
    private long callCount;
    private long successCount;
    private long failureCount;
    private long inputTokens;
    private long outputTokens;
    private long totalTokens;
    private double failureRate;
    private double avgLatencyMs;
}
