package com.hify.mcp.api;

import java.util.List;

public interface McpToolCallAuditService {

    void record(McpToolCallAuditRecord record);

    List<McpToolCallAuditResp> listByTraceId(String traceId);
}
