package com.hify.model.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.model.api.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    @GetMapping
    public PageResult<ProviderListItemResp> list(ProviderQuery query) {
        return providerService.list(query);
    }

    @GetMapping("/{id}")
    public Result<ProviderDetailResp> get(@PathVariable Long id) {
        return Result.ok(providerService.getById(id));
    }

    @PostMapping
    public Result<ProviderResp> create(@Valid @RequestBody CreateProviderReq req) {
        return Result.ok(providerService.create(req));
    }

    @PutMapping("/{id}")
    public Result<ProviderResp> update(@PathVariable Long id,
                                       @Valid @RequestBody UpdateProviderReq req) {
        return Result.ok(providerService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        providerService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/toggle")
    public Result<ProviderResp> toggle(@PathVariable Long id) {
        return Result.ok(providerService.toggle(id));
    }

    @PostMapping("/{id}/test-connection")
    public Result<ConnectivityTestResult> testConnection(@PathVariable Long id) {
        return Result.ok(providerService.test(id));
    }
}
