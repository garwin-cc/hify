package com.hify.mcp.api;

import com.hify.common.web.PageResult;

import java.util.List;

public interface McpToolCallAuditService {

    void record(McpToolCallAuditRecord record);

    List<McpToolCallAuditResp> listByTraceId(String traceId);

    PageResult<McpToolCallAuditResp> list(McpToolCallAuditQuery query);
}
