package com.hify.agent.api;

import com.hify.common.web.PageResult;

/** Agent 配置服务，跨模块调用的统一入口。 */
public interface AgentService {

    /** 创建 Agent，返回含模型配置和工具列表的完整详情。 */
    AgentDetailResp create(CreateAgentReq req);

    AgentDetailResp update(Long id, UpdateAgentReq req);

    AgentDetailResp bindTools(Long id, java.util.List<Long> toolIds);

    void delete(Long id);

    AgentDetailResp toggleEnabled(Long id, int enabled);

    AgentDetailResp getDetail(Long id);

    PageResult<AgentListItemResp> listPage(AgentQuery query);
}
