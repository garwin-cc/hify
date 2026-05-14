package com.hify.mcp.api;

import java.util.List;
import java.util.Map;

public interface McpClientService {

    String callTool(Long mcpServerId, String toolName, Map<String, Object> arguments);

    String callTool(McpToolCallRequest request);

    List<String> listTools(Long mcpServerId);
}
