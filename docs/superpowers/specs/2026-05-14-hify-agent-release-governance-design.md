# Hify Agent Release Governance Design

## Scope

本期做 `hify-agent` 后端闭环，不改前端页面。目标是让 Agent 从“可编辑配置”升级为“可发布、可回滚、可作为内部应用/API 调用、工具调用受控、资源绑定受项目权限保护”的后端能力。

## Design

### Agent 版本

新增 `t_agent_version` 保存 Agent 配置快照，版本状态为 `DRAFT / TEST / PUBLISHED / ROLLED_BACK`。Agent 创建时自动生成 v1 草稿；普通编辑更新 Agent 主表并生成新的草稿版本；测试发布生成 `TEST` 版本；正式发布生成 `PUBLISHED` 版本并更新 Agent 当前发布版本号。

回滚不会覆盖历史版本，而是从目标版本快照恢复 Agent 主表，并生成一个新的草稿版本。这样能保留完整历史，也避免运行中的应用引用被静默修改。

### Agent 应用发布

新增 `t_agent_app` 表表示内部 Web App / API Endpoint 发布入口。应用绑定 `agent_id` 和 `published_version_id`，只允许绑定 `PUBLISHED` 版本，线上调用不读取草稿。

新增 `t_agent_api_key` 表，保存 API Key 哈希、前缀、状态和最后使用时间。创建时只返回一次明文 key，后续只展示 prefix。API Endpoint 的实际调用链路可以后续接入，本期先完成管理闭环和数据模型。

### Agent 调试

复用现有 conversation trace 体系，不重新造日志中心。本期扩展 Agent 和 LLM trace 返回字段：Agent trace 增加 system prompt、版本信息和最大工具轮次；LLM trace 增加 request summary。RAG 命中、MCP 调用、LLM token/耗时已存在，前端调试面板可直接读取 message trace。

### 工具治理

MCP 工具增加 `dangerous`、`permission_level`、`schema_validation_enabled`。Agent 增加 `max_tool_rounds`，默认 2，最大 5。对话执行工具调用时使用 Agent 配置限制轮次，不再只用常量。

工具参数校验以 JSON schema 的轻量子集落地：支持根对象 `required`、`properties` 下的 `type`，覆盖 string/number/integer/boolean/object/array。校验失败时不直接中断 SSE，而是作为 tool message 交回 LLM，让模型生成可读说明。

### 资源权限

Agent 绑定知识库、MCP 工具、工作流时校验资源项目边界。Agent 与资源同项目时允许；跨项目时当前用户必须具备目标项目 `READ` 权限，且当前 Agent 项目必须有 `MANAGE` 权限。

知识库通过 `KnowledgeService.getKnowledgeBase` 返回 `projectId`。MCP 通过 `McpToolResp` 返回工具所属 Server 的 `projectId`。Workflow 因为模块依赖方向是 `workflow -> agent`，在 `hify-agent/api` 定义 `AgentWorkflowResourceService`，由 `hify-workflow` 实现，避免循环依赖。

## API

- `POST /api/v1/agents/{id}/versions/test`：生成测试版。
- `POST /api/v1/agents/{id}/versions/publish`：生成发布版。
- `POST /api/v1/agents/{id}/versions/{versionNo}/rollback`：从历史版本回滚为新草稿。
- `GET /api/v1/agents/{id}/versions`：查看版本列表。
- `POST /api/v1/agents/{id}/apps`：发布 Agent 应用入口。
- `GET /api/v1/agents/{id}/apps`：查看应用入口。
- `POST /api/v1/agent-apps/{appId}/api-keys`：创建 API Key。
- `GET /api/v1/agent-apps/{appId}/api-keys`：查看 API Key 列表。
- `DELETE /api/v1/agent-apps/{appId}/api-keys/{keyId}`：禁用 API Key。

## Data Migration

新增 `V34__agent_release_governance.sql`：

- 扩展 `t_agent`：当前草稿版本、发布版本、发布状态、最大工具轮次。
- 扩展 `t_mcp_tool`：危险工具、权限等级、schema 校验开关。
- 新增 `t_agent_version`、`t_agent_app`、`t_agent_api_key`。
- 扩展 conversation trace：system prompt、Agent version、LLM request summary。

## Testing

后端测试覆盖：

- Agent 创建生成草稿版本。
- 发布版本绑定不可变快照。
- 回滚生成新草稿而不改历史。
- 跨项目绑定知识库/工具/工作流时无权限会失败。
- 危险工具绑定需要调用方具备项目 MANAGE。
- 工具参数 schema 校验失败时返回受控错误。
- conversation 使用 Agent 的 `maxToolRounds`。

## Risks

- 当前 API Endpoint 只完成管理闭环，不直接新增外部调用执行链路，避免绕过现有 conversation 权限和限流。
- Workflow 权限校验通过 Agent API 中的资源接口反向实现，必须保持接口很窄，只暴露 `id/workspaceId/projectId/enabled`。
- 工具 schema 校验只实现常用子集，复杂 schema 仍交给 MCP 服务端二次校验。
