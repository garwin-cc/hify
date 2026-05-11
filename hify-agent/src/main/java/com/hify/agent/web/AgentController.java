package com.hify.agent.web;

import com.hify.agent.api.*;
import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<AgentDetailResp> create(@Valid @RequestBody CreateAgentReq req) {
        return Result.ok(agentService.create(req));
    }

    @PutMapping("/{id}")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<AgentDetailResp> update(@PathVariable Long id,
                                          @Valid @RequestBody UpdateAgentReq req) {
        return Result.ok(agentService.update(id, req));
    }

    @PutMapping("/{id}/tools")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<AgentDetailResp> bindTools(@PathVariable Long id,
                                             @RequestBody List<Long> toolIds) {
        return Result.ok(agentService.bindTools(id, toolIds));
    }

    @DeleteMapping("/{id}")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/enabled/{enabled}")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<AgentDetailResp> toggleEnabled(@PathVariable Long id,
                                                  @PathVariable int enabled) {
        return Result.ok(agentService.toggleEnabled(id, enabled));
    }

    @GetMapping("/{id}")
    public Result<AgentDetailResp> getDetail(@PathVariable Long id) {
        return Result.ok(agentService.getDetail(id));
    }

    @GetMapping
    public PageResult<AgentListItemResp> listPage(AgentQuery query) {
        return agentService.listPage(query);
    }
}
