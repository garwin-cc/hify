package com.hify.auth.domain;

import com.hify.auth.api.CurrentUser;
import com.hify.auth.api.PermissionAction;
import com.hify.auth.api.ProjectRole;
import com.hify.auth.api.UserRole;
import com.hify.auth.infra.ProjectMemberMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionServiceImplTest {

    @Test
    void adminCanManageAnyProjectWithoutMembership() {
        PermissionServiceImpl service = new PermissionServiceImpl(mapper(null));

        assertThat(service.canAccessProject(user(UserRole.ADMIN), 99L, PermissionAction.MANAGE)).isTrue();
    }

    @Test
    void developerCanManageProjectButViewerCannot() {
        PermissionServiceImpl developerService = new PermissionServiceImpl(mapper(member(ProjectRole.DEVELOPER)));
        PermissionServiceImpl viewerService = new PermissionServiceImpl(mapper(member(ProjectRole.VIEWER)));

        assertThat(developerService.canAccessProject(user(UserRole.VIEWER), 10L, PermissionAction.MANAGE)).isTrue();
        assertThat(viewerService.canAccessProject(user(UserRole.VIEWER), 10L, PermissionAction.MANAGE)).isFalse();
        assertThat(viewerService.canAccessProject(user(UserRole.VIEWER), 10L, PermissionAction.READ)).isTrue();
    }

    @Test
    void missingMembershipCannotReadProject() {
        PermissionServiceImpl service = new PermissionServiceImpl(mapper(null));

        assertThat(service.canAccessProject(user(UserRole.VIEWER), 10L, PermissionAction.READ)).isFalse();
    }

    private static CurrentUser user(UserRole role) {
        CurrentUser user = new CurrentUser();
        user.setId(100L);
        user.setUsername("alice");
        user.setRole(role);
        return user;
    }

    private static ProjectMemberPo member(ProjectRole role) {
        ProjectMemberPo po = new ProjectMemberPo();
        po.setId(1L);
        po.setWorkspaceId(1L);
        po.setProjectId(10L);
        po.setUserId(100L);
        po.setRole(role.name());
        po.setStatus("ACTIVE");
        return po;
    }

    private static ProjectMemberMapper mapper(ProjectMemberPo member) {
        return proxy(ProjectMemberMapper.class, method -> {
            if ("selectOne".equals(method)) {
                return args -> member;
            }
            return null;
        });
    }

    private static <T> T proxy(Class<T> type, Function<String, Invocation> behavior) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    Invocation invocation = behavior.apply(method.getName());
                    if (invocation != null) {
                        return invocation.invoke(args == null ? new Object[0] : args);
                    }
                    if (method.getReturnType().equals(int.class) || method.getReturnType().equals(Integer.class)) {
                        return 0;
                    }
                    if (method.getReturnType().equals(boolean.class) || method.getReturnType().equals(Boolean.class)) {
                        return false;
                    }
                    return null;
                }));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Object[] args);
    }
}
