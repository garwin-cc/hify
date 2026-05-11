package com.hify.model.web;

import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.common.web.Result;
import com.hify.model.api.CreateModelConfigReq;
import com.hify.model.api.ModelConfigResp;
import com.hify.model.api.ModelConfigService;
import com.hify.model.api.UpdateModelConfigTypeReq;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/model-configs")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    /** 返回所有启用的模型配置，供 Agent / Knowledge 等模块的下拉选择使用。 */
    @GetMapping
    public Result<List<ModelConfigResp>> listEnabled(@RequestParam(required = false) String modelType) {
        if (modelType != null && !modelType.isBlank()) {
            return Result.ok(modelConfigService.listEnabledByType(modelType));
        }
        return Result.ok(modelConfigService.listEnabled());
    }

    @PostMapping
    @RequireRole(UserRole.ADMIN)
    public Result<ModelConfigResp> create(@Valid @RequestBody CreateModelConfigReq req) {
        return Result.ok(modelConfigService.create(req));
    }

    @PutMapping("/{id}/type")
    @RequireRole(UserRole.ADMIN)
    public Result<ModelConfigResp> updateModelType(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateModelConfigTypeReq req) {
        return Result.ok(modelConfigService.updateModelType(id, req.getModelType()));
    }
}
