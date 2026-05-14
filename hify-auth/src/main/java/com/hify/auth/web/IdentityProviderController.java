package com.hify.auth.web;

import com.hify.auth.api.CreateIdentityProviderReq;
import com.hify.auth.api.IdentityProviderResp;
import com.hify.auth.api.IdentityProviderService;
import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UpdateIdentityProviderReq;
import com.hify.auth.api.UserRole;
import com.hify.common.web.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/identity-providers")
@RequireRole(UserRole.ADMIN)
@RequiredArgsConstructor
public class IdentityProviderController {

    private final IdentityProviderService identityProviderService;

    @GetMapping
    public Result<List<IdentityProviderResp>> list() {
        return Result.ok(identityProviderService.list());
    }

    @PostMapping
    public Result<IdentityProviderResp> create(@Valid @RequestBody CreateIdentityProviderReq req) {
        return Result.ok(identityProviderService.create(req));
    }

    @PutMapping("/{id}")
    public Result<IdentityProviderResp> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateIdentityProviderReq req) {
        return Result.ok(identityProviderService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        identityProviderService.delete(id);
        return Result.ok();
    }
}
