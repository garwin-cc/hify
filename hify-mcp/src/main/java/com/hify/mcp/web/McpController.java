package com.hify.mcp.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.mcp.api.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mcp-servers")
@RequiredArgsConstructor
public class McpController {

    private final McpService mcpService;

    @GetMapping
    public PageResult<McpServerListItemResp> list(McpServerQuery query) {
        return mcpService.list(query);
    }

    @GetMapping("/{id}")
    public Result<McpServerDetailResp> get(@PathVariable Long id) {
        return Result.ok(mcpService.getById(id));
    }

    @PostMapping
    public Result<McpServerResp> create(@Valid @RequestBody CreateMcpServerReq req) {
        return Result.ok(mcpService.create(req));
    }

    @PutMapping("/{id}")
    public Result<McpServerResp> update(@PathVariable Long id,
                                        @Valid @RequestBody UpdateMcpServerReq req) {
        return Result.ok(mcpService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mcpService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/test")
    public Result<McpConnectivityTestResult> test(@PathVariable Long id) {
        return Result.ok(mcpService.test(id));
    }
}
