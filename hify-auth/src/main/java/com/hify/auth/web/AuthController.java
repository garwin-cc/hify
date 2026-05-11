package com.hify.auth.web;

import com.hify.auth.api.AuthService;
import com.hify.auth.api.ChangePasswordReq;
import com.hify.auth.api.LoginReq;
import com.hify.auth.api.LoginResp;
import com.hify.auth.api.UserResp;
import com.hify.common.web.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(authService.login(req));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        authService.logout(header == null ? null : header.replaceFirst("^Bearer\\s+", ""));
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<UserResp> me() {
        return Result.ok(authService.me());
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        authService.changePassword(req);
        return Result.ok();
    }
}
