package com.hify.model.web;

import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.common.web.Result;
import com.hify.model.api.LlmCallContext;
import com.hify.model.api.ModelDefaultPolicyReq;
import com.hify.model.api.ModelDefaultPolicyResp;
import com.hify.model.api.ModelDefaultPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/model-default-policies")
@RequireRole(UserRole.ADMIN)
@RequiredArgsConstructor
public class ModelDefaultPolicyController {

    private final ModelDefaultPolicyService modelDefaultPolicyService;

    @GetMapping
    public Result<List<ModelDefaultPolicyResp>> list() {
        return Result.ok(modelDefaultPolicyService.list());
    }

    @PostMapping
    public Result<ModelDefaultPolicyResp> upsert(@Valid @RequestBody ModelDefaultPolicyReq req) {
        return Result.ok(modelDefaultPolicyService.upsert(req));
    }

    @GetMapping("/resolve")
    public Result<ModelDefaultPolicyResp> resolve(@RequestParam(defaultValue = "CHAT") String modelType,
                                                  @RequestParam(required = false) Long projectId,
                                                  @RequestParam(required = false) Long appId,
                                                  @RequestParam(required = false) String fallbackProviderType) {
        return Result.ok(modelDefaultPolicyService.resolve(modelType,
                LlmCallContext.builder().projectId(projectId).appId(appId).build(),
                fallbackProviderType));
    }
}
