package com.hify.mcp.api;

import lombok.Data;

import java.util.List;

@Data
public class McpServerDetailResp {

    private McpServerResp server;

    private List<McpToolResp> tools;
}
