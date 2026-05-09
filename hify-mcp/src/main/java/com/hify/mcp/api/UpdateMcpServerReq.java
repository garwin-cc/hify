package com.hify.mcp.api;

import lombok.Data;

@Data
public class UpdateMcpServerReq {

    private String name;

    private String description;

    private String endpoint;

    private Integer enabled;
}
