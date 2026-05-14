package com.hify.mcp.web;

import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.common.web.Result;
import com.hify.mcp.api.CreateOpenApiSourceReq;
import com.hify.mcp.api.OpenApiSourceResp;
import com.hify.mcp.api.OpenApiToolResp;
import com.hify.mcp.domain.OpenApiToolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/openapi-sources")
@RequireRole({UserRole.ADMIN, UserRole.EDITOR})
@RequiredArgsConstructor
public class OpenApiToolController {

    private final OpenApiToolService openApiToolService;

    @PostMapping
    public Result<OpenApiSourceResp> create(@Valid @RequestBody CreateOpenApiSourceReq req) {
        return Result.ok(openApiToolService.create(req));
    }

    @PostMapping("/{id}/sync")
    public Result<List<OpenApiToolResp>> sync(@PathVariable Long id) {
        return Result.ok(openApiToolService.sync(id));
    }

    @GetMapping("/{id}/tools")
    public Result<List<OpenApiToolResp>> listTools(@PathVariable Long id) {
        return Result.ok(openApiToolService.listTools(id));
    }
}
