package com.hify.auth.web;

import com.hify.auth.api.AuthService;
import com.hify.auth.api.CurrentUser;
import com.hify.auth.api.PermissionAction;
import com.hify.auth.api.PermissionService;
import com.hify.auth.api.RequireProjectPermission;
import com.hify.auth.api.UserRole;
import com.hify.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthInterceptorProjectPermissionTest {

    @Test
    void deniesProjectPermissionWhenEnforcementEnabled() throws Exception {
        AuthInterceptor interceptor = interceptor(false);
        ReflectionTestUtils.setField(interceptor, "projectPermissionEnabled", true);

        assertThatThrownBy(() -> interceptor.preHandle(request(), new MockHttpServletResponse(), handlerMethod()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无操作权限");
    }

    @Test
    void allowsProjectPermissionWhenEnforcementDisabled() throws Exception {
        AuthInterceptor interceptor = interceptor(false);
        ReflectionTestUtils.setField(interceptor, "projectPermissionEnabled", false);

        assertThat(interceptor.preHandle(request(), new MockHttpServletResponse(), handlerMethod())).isTrue();
    }

    private static AuthInterceptor interceptor(boolean allowed) {
        AuthInterceptor interceptor = new AuthInterceptor(authService());
        interceptor.setPermissionService(permissionService(allowed));
        return interceptor;
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/projects/10");
        request.addHeader("Authorization", "Bearer token");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("projectId", "10"));
        return request;
    }

    private static HandlerMethod handlerMethod() throws NoSuchMethodException {
        Method method = ProjectController.class.getDeclaredMethod("update", Long.class);
        return new HandlerMethod(new ProjectController(), method);
    }

    private static AuthService authService() {
        return (AuthService) Proxy.newProxyInstance(
                AuthService.class.getClassLoader(),
                new Class<?>[]{AuthService.class},
                (proxy, method, args) -> {
                    if ("authenticate".equals(method.getName())) {
                        CurrentUser user = new CurrentUser();
                        user.setId(100L);
                        user.setUsername("viewer");
                        user.setRole(UserRole.VIEWER);
                        return user;
                    }
                    return null;
                });
    }

    private static PermissionService permissionService(boolean allowed) {
        return (PermissionService) Proxy.newProxyInstance(
                PermissionService.class.getClassLoader(),
                new Class<?>[]{PermissionService.class},
                (proxy, method, args) -> {
                    if ("canAccessProject".equals(method.getName())) {
                        return allowed;
                    }
                    return false;
                });
    }

    private static class ProjectController {
        @RequireProjectPermission(projectIdParam = "projectId", action = PermissionAction.MANAGE)
        void update(Long projectId) {
        }
    }
}
