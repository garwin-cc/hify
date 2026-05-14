package com.hify.mcp.api;

import lombok.Data;

@Data
public class ToolSecretQuery {

    private Long projectId;
    private String status;
    private int page = 1;
    private int size = 20;
}
