package com.hify.auth.web;

import com.hify.auth.api.AuthService;
import com.hify.auth.api.CurrentUser;
import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.auth.domain.CurrentUserContext;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            return true;
        }
        CurrentUser user = authService.authenticate(extractBearerToken(request));
        CurrentUserContext.set(user);
        if (handler instanceof HandlerMethod handlerMethod) {
            RequireRole requireRole = findRequireRole(handlerMethod);
            if (requireRole != null && Arrays.stream(requireRole.value()).noneMatch(role -> role == user.getRole())) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserContext.clear();
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/health")
                || path.startsWith("/actuator/");
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return header.substring("Bearer ".length()).trim();
    }

    private RequireRole findRequireRole(HandlerMethod handlerMethod) {
        RequireRole method = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (method != null) {
            return method;
        }
        return handlerMethod.getBeanType().getAnnotation(RequireRole.class);
    }
}
