package com.hify.mcp.api;

import com.hify.common.web.PageResult;

import java.util.List;

/** MCP 工具接入服务，跨模块调用的统一入口。 */
public interface McpService {

    PageResult<McpServerListItemResp> list(McpServerQuery query);

    McpServerDetailResp getById(Long id);

    McpServerResp create(CreateMcpServerReq req);

    McpServerResp update(Long id, UpdateMcpServerReq req);

    void delete(Long id);

    McpConnectivityTestResult test(Long id);

    void validateEnabledToolIds(List<Long> toolIds);

    List<McpToolResp> listEnabledToolsByIds(List<Long> toolIds);

    List<McpServerResp> listEnabled();
}
