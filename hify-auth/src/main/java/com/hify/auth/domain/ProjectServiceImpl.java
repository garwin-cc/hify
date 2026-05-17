package com.hify.auth.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.auth.api.AddProjectMemberReq;
import com.hify.auth.api.CreateProjectReq;
import com.hify.auth.api.ProjectMemberResp;
import com.hify.auth.api.ProjectResp;
import com.hify.auth.api.ProjectRole;
import com.hify.auth.api.ProjectService;
import com.hify.auth.api.UpdateProjectMemberRoleReq;
import com.hify.auth.api.UpdateProjectReq;
import com.hify.auth.api.CurrentUser;
import com.hify.auth.api.UserRole;
import com.hify.auth.infra.ProjectMapper;
import com.hify.auth.infra.ProjectMemberMapper;
import com.hify.auth.infra.UserMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private static final long DEFAULT_WORKSPACE_ID = 1L;
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserMapper userMapper;

    @Override
    public List<ProjectResp> listProjects() {
        CurrentUser user = CurrentUserContext.get();
        if (user != null && user.getRole() != UserRole.ADMIN) {
            List<Long> projectIds = projectMemberMapper.selectList(Wrappers.lambdaQuery(ProjectMemberPo.class)
                            .eq(ProjectMemberPo::getUserId, user.getId())
                            .eq(ProjectMemberPo::getStatus, STATUS_ACTIVE))
                    .stream()
                    .map(ProjectMemberPo::getProjectId)
                    .distinct()
                    .toList();
            if (projectIds.isEmpty()) {
                return List.of();
            }
            return projectMapper.selectList(Wrappers.lambdaQuery(ProjectPo.class)
                            .in(ProjectPo::getId, projectIds)
                            .eq(ProjectPo::getStatus, STATUS_ACTIVE)
                            .orderByDesc(ProjectPo::getCreatedAt))
                    .stream()
                    .map(this::toProjectResp)
                    .toList();
        }
        return projectMapper.selectList(Wrappers.lambdaQuery(ProjectPo.class)
                        .orderByDesc(ProjectPo::getCreatedAt))
                .stream()
                .map(this::toProjectResp)
                .toList();
    }

    @Override
    @Transactional
    public ProjectResp createProject(CreateProjectReq req) {
        checkProjectCodeUnique(req.getWorkspaceId() == null ? DEFAULT_WORKSPACE_ID : req.getWorkspaceId(),
                req.getCode(), null);
        ProjectPo po = new ProjectPo();
        po.setWorkspaceId(req.getWorkspaceId() == null ? DEFAULT_WORKSPACE_ID : req.getWorkspaceId());
        po.setName(req.getName());
        po.setCode(req.getCode());
        po.setStatus(STATUS_ACTIVE);
        projectMapper.insert(po);
        addCreatorAsOwner(po);
        return toProjectResp(po);
    }

    @Override
    @Transactional
    public ProjectResp updateProject(Long projectId, UpdateProjectReq req) {
        ProjectPo po = requireProject(projectId);
        if (StringUtils.hasText(req.getName())) {
            po.setName(req.getName());
        }
        if (StringUtils.hasText(req.getCode()) && !req.getCode().equals(po.getCode())) {
            checkProjectCodeUnique(po.getWorkspaceId(), req.getCode(), projectId);
            po.setCode(req.getCode());
        }
        if (StringUtils.hasText(req.getStatus())) {
            po.setStatus(normalizeStatus(req.getStatus()));
        }
        projectMapper.updateById(po);
        return toProjectResp(po);
    }

    @Override
    public List<ProjectMemberResp> listMembers(Long projectId) {
        requireProject(projectId);
        List<ProjectMemberPo> members = projectMemberMapper.selectList(Wrappers.lambdaQuery(ProjectMemberPo.class)
                .eq(ProjectMemberPo::getProjectId, projectId)
                .orderByDesc(ProjectMemberPo::getCreatedAt));
        if (members.isEmpty()) {
            return List.of();
        }
        Map<Long, UserPo> users = userMapper.selectList(Wrappers.lambdaQuery(UserPo.class)
                        .in(UserPo::getId, members.stream().map(ProjectMemberPo::getUserId).toList()))
                .stream()
                .collect(Collectors.toMap(UserPo::getId, Function.identity(), (left, right) -> left));
        return members.stream()
                .map(member -> toMemberResp(member, users.get(member.getUserId())))
                .toList();
    }

    @Override
    @Transactional
    public ProjectMemberResp addMember(Long projectId, AddProjectMemberReq req) {
        ProjectPo project = requireProject(projectId);
        ProjectMemberPo member = projectMemberMapper.selectOne(Wrappers.lambdaQuery(ProjectMemberPo.class)
                .eq(ProjectMemberPo::getProjectId, projectId)
                .eq(ProjectMemberPo::getUserId, req.getUserId()));
        if (member == null) {
            member = new ProjectMemberPo();
            member.setWorkspaceId(project.getWorkspaceId());
            member.setProjectId(projectId);
            member.setUserId(req.getUserId());
            member.setRole(req.getRole().name());
            member.setStatus(STATUS_ACTIVE);
            projectMemberMapper.insert(member);
        } else {
            member.setRole(req.getRole().name());
            member.setStatus(STATUS_ACTIVE);
            projectMemberMapper.updateById(member);
        }
        return toMemberResp(member, userMapper.selectById(req.getUserId()));
    }

    @Override
    @Transactional
    public ProjectMemberResp updateMemberRole(Long projectId, Long userId, UpdateProjectMemberRoleReq req) {
        ProjectMemberPo member = requireMember(projectId, userId);
        member.setRole(req.getRole().name());
        member.setStatus(STATUS_ACTIVE);
        projectMemberMapper.updateById(member);
        return toMemberResp(member, userMapper.selectById(userId));
    }

    @Override
    @Transactional
    public void removeMember(Long projectId, Long userId) {
        ProjectMemberPo member = requireMember(projectId, userId);
        member.setStatus(STATUS_DISABLED);
        projectMemberMapper.updateById(member);
    }

    private ProjectPo requireProject(Long projectId) {
        ProjectPo po = projectMapper.selectById(projectId);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目不存在: " + projectId);
        }
        return po;
    }

    private ProjectMemberPo requireMember(Long projectId, Long userId) {
        ProjectMemberPo member = projectMemberMapper.selectOne(Wrappers.lambdaQuery(ProjectMemberPo.class)
                .eq(ProjectMemberPo::getProjectId, projectId)
                .eq(ProjectMemberPo::getUserId, userId));
        if (member == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目成员不存在: " + userId);
        }
        return member;
    }

    private void checkProjectCodeUnique(Long workspaceId, String code, Long excludeId) {
        Long count = projectMapper.selectCount(Wrappers.lambdaQuery(ProjectPo.class)
                .eq(ProjectPo::getWorkspaceId, workspaceId)
                .eq(ProjectPo::getCode, code)
                .ne(excludeId != null, ProjectPo::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "项目编码已存在: " + code);
        }
    }

    private void addCreatorAsOwner(ProjectPo project) {
        CurrentUser user = CurrentUserContext.get();
        if (user == null || user.getId() == null || project.getId() == null) {
            return;
        }
        ProjectMemberPo member = new ProjectMemberPo();
        member.setWorkspaceId(project.getWorkspaceId());
        member.setProjectId(project.getId());
        member.setUserId(user.getId());
        member.setRole(ProjectRole.OWNER.name());
        member.setStatus(STATUS_ACTIVE);
        projectMemberMapper.insert(member);
    }

    private String normalizeStatus(String status) {
        if (STATUS_ACTIVE.equalsIgnoreCase(status)) {
            return STATUS_ACTIVE;
        }
        if (STATUS_DISABLED.equalsIgnoreCase(status)) {
            return STATUS_DISABLED;
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "项目状态不合法: " + status);
    }

    private ProjectResp toProjectResp(ProjectPo po) {
        ProjectResp resp = new ProjectResp();
        resp.setId(po.getId());
        resp.setWorkspaceId(po.getWorkspaceId());
        resp.setName(po.getName());
        resp.setCode(po.getCode());
        resp.setStatus(po.getStatus());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    private ProjectMemberResp toMemberResp(ProjectMemberPo po, UserPo user) {
        ProjectMemberResp resp = new ProjectMemberResp();
        resp.setId(po.getId());
        resp.setWorkspaceId(po.getWorkspaceId());
        resp.setProjectId(po.getProjectId());
        resp.setUserId(po.getUserId());
        resp.setUsername(user == null ? "" : user.getUsername());
        resp.setDisplayName(user == null ? "" : user.getDisplayName());
        resp.setRole(ProjectRole.valueOf(po.getRole()));
        resp.setStatus(po.getStatus());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }
}
