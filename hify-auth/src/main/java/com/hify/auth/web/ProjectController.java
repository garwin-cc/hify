package com.hify.auth.web;

import com.hify.auth.api.AddProjectMemberReq;
import com.hify.auth.api.CreateProjectReq;
import com.hify.auth.api.PermissionAction;
import com.hify.auth.api.ProjectMemberResp;
import com.hify.auth.api.ProjectResp;
import com.hify.auth.api.ProjectService;
import com.hify.auth.api.RequireProjectPermission;
import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UpdateProjectMemberRoleReq;
import com.hify.auth.api.UpdateProjectReq;
import com.hify.auth.api.UserRole;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public Result<List<ProjectResp>> listProjects() {
        return Result.ok(projectService.listProjects());
    }

    @PostMapping
    @RequireRole(UserRole.ADMIN)
    public Result<ProjectResp> createProject(@Valid @RequestBody CreateProjectReq req) {
        return Result.ok(projectService.createProject(req));
    }

    @PutMapping("/{projectId}")
    @RequireProjectPermission(action = PermissionAction.MANAGE)
    public Result<ProjectResp> updateProject(@PathVariable Long projectId,
                                             @Valid @RequestBody UpdateProjectReq req) {
        return Result.ok(projectService.updateProject(projectId, req));
    }

    @GetMapping("/{projectId}/members")
    @RequireProjectPermission(action = PermissionAction.READ)
    public Result<List<ProjectMemberResp>> listMembers(@PathVariable Long projectId) {
        return Result.ok(projectService.listMembers(projectId));
    }

    @PostMapping("/{projectId}/members")
    @RequireProjectPermission(action = PermissionAction.MANAGE)
    public Result<ProjectMemberResp> addMember(@PathVariable Long projectId,
                                               @Valid @RequestBody AddProjectMemberReq req) {
        return Result.ok(projectService.addMember(projectId, req));
    }

    @PutMapping("/{projectId}/members/{userId}/role")
    @RequireProjectPermission(action = PermissionAction.MANAGE)
    public Result<ProjectMemberResp> updateMemberRole(@PathVariable Long projectId,
                                                      @PathVariable Long userId,
                                                      @Valid @RequestBody UpdateProjectMemberRoleReq req) {
        return Result.ok(projectService.updateMemberRole(projectId, userId, req));
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @RequireProjectPermission(action = PermissionAction.MANAGE)
    public Result<Void> removeMember(@PathVariable Long projectId, @PathVariable Long userId) {
        projectService.removeMember(projectId, userId);
        return Result.ok();
    }
}
