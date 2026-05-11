package com.hify.auth.web;

import com.hify.auth.api.CreateUserReq;
import com.hify.auth.api.RequireRole;
import com.hify.auth.api.ResetPasswordReq;
import com.hify.auth.api.UpdateUserReq;
import com.hify.auth.api.UserResp;
import com.hify.auth.api.UserRole;
import com.hify.auth.api.UserService;
import com.hify.common.web.PageResult;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequireRole(UserRole.ADMIN)
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public PageResult<UserResp> list(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return userService.list(page, size);
    }

    @PostMapping
    public Result<UserResp> create(@Valid @RequestBody CreateUserReq req) {
        return Result.ok(userService.create(req));
    }

    @PutMapping("/{id}")
    public Result<UserResp> update(@PathVariable Long id, @Valid @RequestBody UpdateUserReq req) {
        return Result.ok(userService.update(id, req));
    }

    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordReq req) {
        userService.resetPassword(id, req);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }
}
