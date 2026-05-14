package com.hify.agent.web;

import com.hify.agent.api.AgentApiKeyCreateResp;
import com.hify.agent.api.AgentApiKeyReq;
import com.hify.agent.api.AgentApiKeyResp;
import com.hify.agent.api.AgentService;
import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.common.web.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent-apps")
@RequiredArgsConstructor
public class AgentAppController {

    private final AgentService agentService;

    @PostMapping("/{appId}/api-keys")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<AgentApiKeyCreateResp> createApiKey(@PathVariable Long appId,
                                                      @Valid @RequestBody AgentApiKeyReq req) {
        return Result.ok(agentService.createApiKey(appId, req));
    }

    @GetMapping("/{appId}/api-keys")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<List<AgentApiKeyResp>> listApiKeys(@PathVariable Long appId) {
        return Result.ok(agentService.listApiKeys(appId));
    }

    @DeleteMapping("/{appId}/api-keys/{keyId}")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<Void> revokeApiKey(@PathVariable Long appId, @PathVariable Long keyId) {
        agentService.revokeApiKey(appId, keyId);
        return Result.ok();
    }
}
