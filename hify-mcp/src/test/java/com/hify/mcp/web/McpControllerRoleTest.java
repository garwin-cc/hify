package com.hify.mcp.web;

import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.mcp.api.CreateMcpServerReq;
import com.hify.mcp.api.McpServerQuery;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class McpControllerRoleTest {

    @Test
    void should_allow_editor_to_read_mcp_servers_when_endpoint_is_get() throws Exception {
        RequireRole listRole = roleOf("list", McpServerQuery.class);
        RequireRole detailRole = roleOf("get", Long.class);

        assertThat(listRole.value()).containsExactlyInAnyOrder(UserRole.ADMIN, UserRole.EDITOR);
        assertThat(detailRole.value()).containsExactlyInAnyOrder(UserRole.ADMIN, UserRole.EDITOR);
    }

    @Test
    void should_keep_admin_required_when_endpoint_mutates_or_tests_server() throws Exception {
        RequireRole classRole = McpController.class.getAnnotation(RequireRole.class);

        assertThat(classRole.value()).containsExactly(UserRole.ADMIN);
        assertThat(roleOf("create", CreateMcpServerReq.class)).isNull();
        assertThat(roleOf("test", Long.class)).isNull();
    }

    private RequireRole roleOf(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = McpController.class.getMethod(methodName, parameterTypes);
        return method.getAnnotation(RequireRole.class);
    }
}
