package com.hify.auth.api;

import lombok.Data;

@Data
public class UpdateProjectReq {

    private String name;
    private String code;
    private String status;
}
