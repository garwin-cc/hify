package com.hify.auth.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.auth.api.CurrentUser;
import com.hify.auth.api.PermissionAction;
import com.hify.auth.api.PermissionService;
import com.hify.auth.api.ProjectRole;
import com.hify.auth.api.UserRole;
import com.hify.auth.infra.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final ProjectMemberMapper projectMemberMapper;

    @Override
    public boolean hasProjectRole(CurrentUser user, Long projectId, ProjectRole... roles) {
        if (isAdmin(user)) {
            return true;
        }
        if (user == null || user.getId() == null || projectId == null || roles == null || roles.length == 0) {
            return false;
        }
        ProjectMemberPo member = loadActiveMember(user.getId(), projectId);
        if (member == null) {
            return false;
        }
        return Arrays.stream(roles).anyMatch(role -> role.name().equals(member.getRole()));
    }

    @Override
    public boolean canAccessProject(CurrentUser user, Long projectId, PermissionAction action) {
        if (isAdmin(user)) {
            return true;
        }
        if (user == null || user.getId() == null || projectId == null || action == null) {
            return false;
        }
        ProjectMemberPo member = loadActiveMember(user.getId(), projectId);
        if (member == null) {
            return false;
        }
        ProjectRole role;
        try {
            role = ProjectRole.valueOf(member.getRole());
        } catch (Exception ignored) {
            return false;
        }
        return allowedActions(role).contains(action);
    }

    private ProjectMemberPo loadActiveMember(Long userId, Long projectId) {
        return projectMemberMapper.selectOne(Wrappers.lambdaQuery(ProjectMemberPo.class)
                .eq(ProjectMemberPo::getUserId, userId)
                .eq(ProjectMemberPo::getProjectId, projectId)
                .eq(ProjectMemberPo::getStatus, STATUS_ACTIVE));
    }

    private static boolean isAdmin(CurrentUser user) {
        return user != null && user.getRole() == UserRole.ADMIN;
    }

    private static Set<PermissionAction> allowedActions(ProjectRole role) {
        return switch (role) {
            case OWNER -> Set.of(PermissionAction.READ, PermissionAction.MANAGE,
                    PermissionAction.RUN, PermissionAction.REVIEW);
            case DEVELOPER -> Set.of(PermissionAction.READ, PermissionAction.MANAGE, PermissionAction.RUN);
            case OPERATOR -> Set.of(PermissionAction.READ, PermissionAction.RUN);
            case REVIEWER -> Set.of(PermissionAction.READ, PermissionAction.REVIEW);
            case VIEWER -> Set.of(PermissionAction.READ);
        };
    }
}
