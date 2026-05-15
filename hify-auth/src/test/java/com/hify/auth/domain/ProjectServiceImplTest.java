package com.hify.auth.domain;

import com.hify.auth.api.AddProjectMemberReq;
import com.hify.auth.api.ProjectMemberResp;
import com.hify.auth.api.ProjectResp;
import com.hify.auth.api.ProjectRole;
import com.hify.auth.api.ProjectService;
import com.hify.auth.api.UpdateProjectMemberRoleReq;
import com.hify.auth.api.CurrentUser;
import com.hify.auth.api.UserRole;
import com.hify.auth.infra.ProjectMapper;
import com.hify.auth.infra.ProjectMemberMapper;
import com.hify.auth.infra.UserMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectServiceImplTest {

    @Test
    void should_list_only_joined_projects_for_non_admin_user() {
        ProjectMemberPo member = new ProjectMemberPo();
        member.setProjectId(10L);
        member.setUserId(100L);
        member.setRole(ProjectRole.VIEWER.name());
        member.setStatus("ACTIVE");
        ProjectPo project = new ProjectPo();
        project.setId(10L);
        project.setWorkspaceId(1L);
        project.setName("研发项目");
        project.setCode("rd");
        project.setStatus("ACTIVE");
        ProjectService service = new ProjectServiceImpl(
                mapper(ProjectMapper.class, method -> {
                    if ("selectList".equals(method)) {
                        return args -> List.of(project);
                    }
                    return null;
                }),
                memberMapper(member, new ArrayList<>(), new ArrayList<>()),
                mapper(UserMapper.class, method -> null));
        CurrentUser user = new CurrentUser();
        user.setId(100L);
        user.setRole(UserRole.VIEWER);
        CurrentUserContext.set(user);
        try {
            List<ProjectResp> projects = service.listProjects();

            assertThat(projects).singleElement()
                    .satisfies(item -> assertThat(item.getId()).isEqualTo(10L));
        } finally {
            CurrentUserContext.clear();
        }
    }

    @Test
    void should_add_project_member_when_user_is_not_member() {
        List<ProjectMemberPo> inserted = new ArrayList<>();
        ProjectService service = new ProjectServiceImpl(
                projectMapper(),
                memberMapper(null, inserted, new ArrayList<>()),
                mapper(UserMapper.class, method -> null));
        AddProjectMemberReq req = new AddProjectMemberReq();
        req.setUserId(100L);
        req.setRole(ProjectRole.DEVELOPER);

        ProjectMemberResp resp = service.addMember(10L, req);

        assertThat(resp.getProjectId()).isEqualTo(10L);
        assertThat(resp.getUserId()).isEqualTo(100L);
        assertThat(resp.getRole()).isEqualTo(ProjectRole.DEVELOPER);
        assertThat(inserted).singleElement()
                .satisfies(member -> assertThat(member.getStatus()).isEqualTo("ACTIVE"));
    }

    @Test
    void should_update_project_member_role_when_member_exists() {
        ProjectMemberPo member = new ProjectMemberPo();
        member.setId(1L);
        member.setWorkspaceId(1L);
        member.setProjectId(10L);
        member.setUserId(100L);
        member.setRole(ProjectRole.VIEWER.name());
        member.setStatus("ACTIVE");
        List<ProjectMemberPo> updated = new ArrayList<>();
        ProjectService service = new ProjectServiceImpl(
                mapper(ProjectMapper.class, method -> null),
                memberMapper(member, new ArrayList<>(), updated),
                mapper(UserMapper.class, method -> null));
        UpdateProjectMemberRoleReq req = new UpdateProjectMemberRoleReq();
        req.setRole(ProjectRole.OPERATOR);

        ProjectMemberResp resp = service.updateMemberRole(10L, 100L, req);

        assertThat(resp.getRole()).isEqualTo(ProjectRole.OPERATOR);
        assertThat(updated).singleElement()
                .satisfies(item -> assertThat(item.getRole()).isEqualTo(ProjectRole.OPERATOR.name()));
    }

    private static ProjectMemberMapper memberMapper(ProjectMemberPo selected,
                                                    List<ProjectMemberPo> inserted,
                                                    List<ProjectMemberPo> updated) {
        return mapper(ProjectMemberMapper.class, method -> {
            if ("selectOne".equals(method)) {
                return args -> selected;
            }
            if ("selectList".equals(method)) {
                return args -> selected == null ? List.of() : List.of(selected);
            }
            if ("insert".equals(method)) {
                return args -> {
                    ProjectMemberPo po = (ProjectMemberPo) args[0];
                    po.setId(99L);
                    inserted.add(po);
                    return 1;
                };
            }
            if ("updateById".equals(method)) {
                return args -> {
                    updated.add((ProjectMemberPo) args[0]);
                    return 1;
                };
            }
            return null;
        });
    }

    private static ProjectMapper projectMapper() {
        return mapper(ProjectMapper.class, method -> {
            if ("selectById".equals(method)) {
                return args -> {
                    ProjectPo po = new ProjectPo();
                    po.setId((Long) args[0]);
                    po.setWorkspaceId(1L);
                    po.setCode("default");
                    po.setName("默认项目");
                    po.setStatus("ACTIVE");
                    return po;
                };
            }
            return null;
        });
    }

    private static <T> T mapper(Class<T> type, Function<String, Invocation> behavior) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    Invocation invocation = behavior.apply(method.getName());
                    if (invocation != null) {
                        return invocation.invoke(args == null ? new Object[0] : args);
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                }));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Object[] args);
    }
}
