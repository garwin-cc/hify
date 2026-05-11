package com.hify.auth.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPasswordReq {

    @NotBlank
    private String password;
}
