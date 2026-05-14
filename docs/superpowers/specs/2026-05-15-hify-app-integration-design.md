# hify-app 启动迁移和集成装配设计

## 背景和查重结论

本轮需求聚焦 `hify-app`，不是重复建设各业务模块能力。现有迁移已经覆盖：

- `V32__project_permission_audit.sql`：workspace、project、project member、identity provider、audit log，并给 Agent、Knowledge、Workflow、MCP Server 增加项目空间字段。
- `V33__model_governance.sql`：provider health、模型默认策略、LLM 调用统计。
- `V34__agent_release_governance.sql`：Agent 版本、应用发布、API Key。
- `V35__conversation_runtime_logging.sql`：会话日志、反馈、memory、trace 扩展。
- `V36__knowledge_rag_production.sql`：知识库任务、重建、混合检索和共享策略。
- `V37__workflow_productionization.sql`：Workflow 发布、运行中心、人审、触发器、变量、diff。
- `V38__mcp_security_openapi_secret.sql`：MCP 安全、OpenAPI 工具、Secret、MCP 调用审计扩展。

因此本轮只补 app 层缺口：系统配置表、限流配额表、Job 运行日志表、启动初始化、健康检查分层、维护类后台 Job 和 app 级集成测试支撑。

## 目标

让 `hify-app` 成为生产部署入口：启动时能完成默认空间/项目、默认管理员、默认模型和默认限流策略的初始化；对外提供 liveness/readiness/deep health；后台维护任务有可观测的运行日志；集成测试覆盖核心链路的应用装配。

## 非目标

- 不重复创建已存在的项目空间、Agent App、API Key、审计日志、对话日志、Workflow 运行日志、MCP 审计表。
- 不在本轮实现新的前端页面。
- 不把业务 Job 迁到 `hify-app`；Provider 健康检查、知识库任务恢复、Workflow stale run 处理仍保留在原模块，本轮只补 app 维护 Job 和运行日志基础。

## 数据设计

新增 `V39__app_bootstrap_quota_job_log.sql`：

- `t_system_setting`：存系统初始化和运行配置，如默认模型、默认限流策略版本。使用 `setting_key` 唯一约束，避免重复初始化。
- `t_rate_limit_quota`：存默认和可扩展的限流策略，维度支持 `USER / APP / API_KEY / PROVIDER / AGENT`，范围支持 `GLOBAL / PROJECT / APP`。
- `t_app_job_run_log`：记录后台 Job 每次执行状态、耗时、影响行数和错误摘要，便于排查定时任务。

已有表不改名、不迁移历史数据；默认数据用 `INSERT ... SELECT ... WHERE NOT EXISTS` 保持幂等。

## 启动初始化

新增 `SystemInitializationRunner` 放在 `hify-app`：

- 确保默认 workspace/project/project member 基线存在，兼容空库、测试库和跳过 Flyway 的 mock profile。
- 默认管理员继续由 `hify-auth` 的 `InitAdminRunner` 负责，本轮只补配置说明，不重复创建用户。
- 当 `hify.init.default-provider.enabled=true` 且数据库没有同名 Provider 时，按配置创建默认 Provider、默认聊天模型和可选 embedding 模型。
- 当默认模型存在时，写入 `t_model_default_policy` 的 GLOBAL CHAT/EMBEDDING 策略，已存在则不覆盖人工配置。
- 初始化默认限流策略到 `t_rate_limit_quota`，作为后续统一限流组件读取的数据库来源。

## 健康检查

保留 `/api/v1/health` 作为兼容入口，语义等同 deep health。

新增：

- `/api/v1/health/liveness`：只返回进程存活，不访问外部依赖。
- `/api/v1/health/readiness`：检查 MySQL、Redis、pgvector，适合 K8s readinessProbe。
- `/api/v1/health/deep`：在 readiness 基础上增加关键 Provider 健康汇总；没有启用 Provider 时返回 UP，但标记 `providerSummary.enabledCount=0`。

Provider deep health 只读取 `t_provider` 和 `t_provider_health`，不主动发起模型调用，避免健康检查放大外部供应商压力。

## 后台 Job

保留已有业务 Job：

- `ProviderHealthCheckJob`：模型 Provider 定时探测。
- `KnowledgeDocumentRecoveryRunner`：知识库处理任务恢复。
- `WorkflowRunCleanupJob`：服务重启后标记 stale workflow run。

新增 `AppMaintenanceJob`：

- 清理过期 session：将 `t_user_session.expires_at < now` 且未 revoke 的 session 标记 revoked。
- 清理日志类数据：按配置保留天数清理 `t_audit_log`、`t_conversation_trace`、`t_conversation_llm_trace`、`t_conversation_rag_trace`、`t_mcp_tool_call_audit`、`t_llm_call_stat`、`t_app_job_run_log`。
- 失败任务重试：触发知识库 retryable failed task 的恢复入口；如入口不可用则只记录跳过，不影响启动。
- 每个 Job 通过 `AppJobRunLogger` 写入 `t_app_job_run_log`，失败也记录。

## 测试策略

- 增加 app 层单测/集成测试，先覆盖初始化 Runner、健康检查分层和维护 Job。
- 保持已有 `HifyMockIntegrationTest` 模式，mock profile 不启动定时 Job，避免测试抖动。
- 对登录、发布应用、API Key、对话、RAG、Workflow、人审、MCP 等长链路，优先补“装配可用”的 MockMvc 测试，不依赖真实 LLM、Redis 或 pgvector。

## 风险和稳定性影响

- 迁移风险：V39 只新增表和幂等默认数据，不修改已有表结构，风险低。
- 初始化风险：默认 Provider/Model 仅在配置显式启用时创建，默认关闭，避免污染生产环境。
- 健康检查风险：deep health 读取 Provider 状态但不主动探测，避免高频检查产生外部成本。
- Job 风险：维护 Job 默认开启，但所有清理 SQL 按时间条件执行，并记录 job log；mock profile 关闭。
