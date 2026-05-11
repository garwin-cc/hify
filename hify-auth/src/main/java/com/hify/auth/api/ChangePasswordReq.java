package com.hify.auth.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordReq {

    @NotBlank
    private String oldPassword;

    @NotBlank
    private String newPassword;
}
