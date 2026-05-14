package com.hify.auth.web;

import com.hify.auth.api.AuthService;
import com.hify.auth.api.CurrentUser;
import com.hify.auth.api.PermissionService;
import com.hify.auth.api.RequireProjectPermission;
import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.common.audit.AuditContext;
import com.hify.auth.domain.CurrentUserContext;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private PermissionService permissionService;

    @Value("${hify.auth.project-permission-enabled:false}")
    private boolean projectPermissionEnabled;

    @Autowired(required = false)
    public void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

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
        AuditContext.setActor(user.getId(), user.getUsername());
        try {
            if (handler instanceof HandlerMethod handlerMethod) {
                RequireRole requireRole = findRequireRole(handlerMethod);
                if (requireRole != null && Arrays.stream(requireRole.value()).noneMatch(role -> role == user.getRole())) {
                    throw new BizException(ErrorCode.FORBIDDEN);
                }
                RequireProjectPermission requireProjectPermission = findRequireProjectPermission(handlerMethod);
                if (projectPermissionEnabled && requireProjectPermission != null && permissionService != null) {
                    Long projectId = resolveProjectId(request, requireProjectPermission.projectIdParam());
                    if (!permissionService.canAccessProject(user, projectId, requireProjectPermission.action())) {
                        throw new BizException(ErrorCode.FORBIDDEN);
                    }
                }
            }
        } catch (RuntimeException e) {
            CurrentUserContext.clear();
            AuditContext.clear();
            throw e;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserContext.clear();
        AuditContext.clear();
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

    private RequireProjectPermission findRequireProjectPermission(HandlerMethod handlerMethod) {
        RequireProjectPermission method = handlerMethod.getMethodAnnotation(RequireProjectPermission.class);
        if (method != null) {
            return method;
        }
        return handlerMethod.getBeanType().getAnnotation(RequireProjectPermission.class);
    }

    private Long resolveProjectId(HttpServletRequest request, String projectIdParam) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables instanceof Map<?, ?> map) {
            Object value = map.get(projectIdParam);
            if (value != null) {
                return parseProjectId(value);
            }
        }
        String value = request.getParameter(projectIdParam);
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return parseProjectId(value);
    }

    private Long parseProjectId(Object value) {
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
