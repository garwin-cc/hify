package com.hify.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserReq {

    @NotBlank
    private String displayName;

    @NotNull
    private UserRole role;

    @NotNull
    private UserStatus status;
}
