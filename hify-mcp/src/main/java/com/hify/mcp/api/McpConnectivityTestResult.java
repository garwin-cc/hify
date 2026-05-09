package com.hify.mcp.api;

import lombok.Data;

import java.util.List;

@Data
public class McpConnectivityTestResult {

    private boolean success;

    private String message;

    private long latencyMs;

    private List<McpToolResp> tools;
}
