package com.hify.auth.api;

import lombok.Data;

@Data
public class LoginResp {

    private String token;

    private UserResp user;
}
