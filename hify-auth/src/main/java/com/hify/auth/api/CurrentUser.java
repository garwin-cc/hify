package com.hify.auth.api;

import lombok.Data;

@Data
public class CurrentUser {

    private Long id;

    private String username;

    private String displayName;

    private UserRole role;

    public boolean hasAnyRole(UserRole... roles) {
        if (role == null || roles == null) {
            return false;
        }
        for (UserRole item : roles) {
            if (role == item) {
                return true;
            }
        }
        return false;
    }
}
