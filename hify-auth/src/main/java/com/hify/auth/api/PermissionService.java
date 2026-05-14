package com.hify.auth.api;

public interface PermissionService {

    boolean hasProjectRole(CurrentUser user, Long projectId, ProjectRole... roles);

    boolean canAccessProject(CurrentUser user, Long projectId, PermissionAction action);
}
