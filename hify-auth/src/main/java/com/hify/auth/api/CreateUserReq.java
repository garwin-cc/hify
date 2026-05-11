package com.hify.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateUserReq {

    @NotBlank
    private String username;

    @NotBlank
    private String displayName;

    @NotBlank
    private String password;

    @NotNull
    private UserRole role;
}
