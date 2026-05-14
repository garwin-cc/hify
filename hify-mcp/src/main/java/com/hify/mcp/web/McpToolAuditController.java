package com.hify.mcp.web;

import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.common.web.PageResult;
import com.hify.mcp.api.McpToolCallAuditQuery;
import com.hify.mcp.api.McpToolCallAuditResp;
import com.hify.mcp.api.McpToolCallAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mcp-tool-audits")
@RequireRole({UserRole.ADMIN, UserRole.EDITOR})
@RequiredArgsConstructor
public class McpToolAuditController {

    private final McpToolCallAuditService auditService;

    @GetMapping
    public PageResult<McpToolCallAuditResp> list(McpToolCallAuditQuery query) {
        return auditService.list(query);
    }
}
