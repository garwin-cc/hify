package com.hify.auth.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserResp {

    private Long id;

    private String username;

    private String displayName;

    private UserRole role;

    private UserStatus status;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;
}
