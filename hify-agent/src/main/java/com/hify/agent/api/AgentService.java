package com.hify.agent.api;

import com.hify.common.web.PageResult;

import java.util.List;

/** Agent 配置服务，跨模块调用的统一入口。 */
public interface AgentService {

    /** 创建 Agent，返回含模型配置和工具列表的完整详情。 */
    AgentDetailResp create(CreateAgentReq req);

    AgentDetailResp update(Long id, UpdateAgentReq req);

    AgentDetailResp bindTools(Long id, List<Long> toolIds);

    void delete(Long id);

    AgentDetailResp toggleEnabled(Long id, int enabled);

    AgentDetailResp getDetail(Long id);

    PageResult<AgentListItemResp> listPage(AgentQuery query);

    List<AgentVersionResp> listVersions(Long id);

    AgentVersionResp publishTestVersion(Long id, AgentPublishReq req);

    AgentVersionResp publishVersion(Long id, AgentPublishReq req);

    AgentDetailResp rollbackVersion(Long id, Integer versionNo, AgentRollbackReq req);

    AgentAppResp createApp(Long agentId, AgentAppReq req);

    List<AgentAppResp> listApps(Long agentId);

    AgentApiKeyCreateResp createApiKey(Long appId, AgentApiKeyReq req);

    List<AgentApiKeyResp> listApiKeys(Long appId);

    void revokeApiKey(Long appId, Long keyId);
}
