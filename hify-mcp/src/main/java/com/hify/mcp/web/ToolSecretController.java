package com.hify.mcp.web;

import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.mcp.api.CreateToolSecretReq;
import com.hify.mcp.api.ToolSecretQuery;
import com.hify.mcp.api.ToolSecretResp;
import com.hify.mcp.domain.ToolSecretService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tool-secrets")
@RequireRole({UserRole.ADMIN, UserRole.EDITOR})
@RequiredArgsConstructor
public class ToolSecretController {

    private final ToolSecretService toolSecretService;

    @PostMapping
    public Result<ToolSecretResp> create(@Valid @RequestBody CreateToolSecretReq req) {
        return Result.ok(toolSecretService.create(req));
    }

    @GetMapping
    public PageResult<ToolSecretResp> list(ToolSecretQuery query) {
        return toolSecretService.list(query);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        toolSecretService.delete(id);
        return Result.ok();
    }
}
