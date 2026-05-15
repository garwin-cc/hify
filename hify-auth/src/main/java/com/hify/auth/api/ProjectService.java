package com.hify.auth.api;

import java.util.List;

public interface ProjectService {

    List<ProjectResp> listProjects();

    ProjectResp createProject(CreateProjectReq req);

    ProjectResp updateProject(Long projectId, UpdateProjectReq req);

    List<ProjectMemberResp> listMembers(Long projectId);

    ProjectMemberResp addMember(Long projectId, AddProjectMemberReq req);

    ProjectMemberResp updateMemberRole(Long projectId, Long userId, UpdateProjectMemberRoleReq req);

    void removeMember(Long projectId, Long userId);
}
