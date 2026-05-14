# Hify AI 路由文档

> 本文件由原 `CLAUDE.md` 拆分而来，保留对应章节原文。

## 1000 人规模演进路线图

当 Hify 从 20-50 人内部使用扩展到约 1000 人本地部署时，建设重点从“功能可用”转向“生产可管、权限可控、运行可观测、成本可治理”。后续仍不追求 Dify 全量复制，而是优先补齐内部生产级平台必需能力。

### 总体取舍

#### 必须增强

- 应用交付闭环：Agent / Workflow 需要可发布为内部 Web App、API Endpoint，并支持应用级 API Key。
- 权限和资源隔离：需要项目/空间维度管理用户、应用、知识库、工作流、MCP 工具和凭证。
- 审计和日志：关键配置修改、工具调用、工作流审批、应用调用都必须可追踪。
- 稳定性和成本控制：需要限流、配额、Provider 健康检查、熔断、fallback、token 和调用统计。
- 生产化 RAG / Workflow：知识库处理、检索、工作流运行、人审和失败恢复必须可解释、可重试、可恢复。

#### 暂不追求

- 不做公开插件市场。内部场景优先通过 MCP、OpenAPI 工具和 OpenAI-compatible Provider 扩展。
- 不做 SaaS 式完整多租户、计费和套餐系统。可以做项目/空间隔离，但不引入外部租户商业化模型。
- 不做实时协作编辑。优先用草稿、发布版本、编辑锁、版本快照和回滚控制风险。
- 不做完整 n8n 式通用自动化平台。可以支持定时/Webhook 触发，但工作流主线仍围绕 AI 应用编排。
- 不把多模态平台作为主线。图片、音频、OCR 等按明确内部需求增量接入。

### 按模块划分的建设清单

#### hify-auth：账号、组织和权限

- 新增项目/空间概念，用于隔离应用、知识库、工作流、MCP Server、工具凭证和成员。
- 将 `ADMIN / EDITOR / VIEWER` 扩展为项目级角色，如 Owner、Developer、Operator、Reviewer、Viewer。
- 接入 LDAP / OIDC / 企业 SSO，避免 1000 人规模下手工维护账号。
- 增加登录、权限变更、资源授权、关键管理操作的审计记录。
- 新增管理接口时默认只允许 Admin 或资源 Owner 操作，普通成员只获得显式授权资源。

#### hify-common：公共治理能力

- 建立统一审计日志组件，供 auth、model、agent、knowledge、workflow、mcp 调用。
- 建立统一限流和配额组件，支持用户、应用、API Key、Provider、模型维度。
- 完善 trace 上下文传播，覆盖 HTTP、SSE、异步任务、RAG、LLM、MCP、Workflow。
- 完善错误码和异常分类，便于前端展示、日志检索和告警聚合。
- 提供后台任务/队列抽象，服务文档处理、Workflow 异步运行、健康检查和日志清理。
- 强化缓存策略，避免大规模访问下 Provider、模型、Agent、权限等配置反复查库。

#### hify-model：模型和 Provider 管理

- 完善 Provider 健康检查、定时探测、失败告警和可视化健康状态。
- 落实 LLM 重试、熔断和 fallback 路由，避免单个供应商故障拖垮平台。
- 增加调用统计：调用次数、token、耗时、失败率、首 token 延迟，按应用/用户/Provider/模型聚合。
- 增强 OpenAI-compatible 适配，优先兼容企业模型网关、阿里百炼、DeepSeek、本地 Ollama 等。
- 增加默认模型策略：全局默认、项目默认、应用默认和 fallback 默认。
- 前端选择模型时继续展示 Provider 名称、模型名称、modelId、模型类型和健康状态。

#### hify-agent：Agent 配置和发布

- 增加 Agent 草稿版、测试版、已发布版和回滚能力，避免线上 Agent 被直接改坏。
- 将 Agent 发布为内部 Web App 和 API Endpoint，并支持应用级 API Key。
- 增强 Agent 调试视图，展示 system prompt、RAG 检索、工具选择、LLM 请求和二次调用链路。
- 增加工具治理：危险工具标记、工具调用权限、参数 schema 校验和最大工具轮次配置。
- Agent 绑定知识库、工具、工作流时必须校验项目/空间权限，避免跨部门误用敏感资源。
- 保持工具绑定数量上限，避免 tools 参数过长影响模型效果和调用成本。

#### hify-conversation：对话运行和日志

- 优化 SSE 稳定性：断开检测、请求取消、超时控制、线程池隔离和流式错误恢复。
- 会话和消息列表逐步改为游标分页，避免 1000 人规模下历史消息深分页拖慢数据库。
- 完善会话记忆：摘要触发阈值、摘要模型、摘要失败降级和 memory trace。
- 增加应用级会话日志中心，支持按用户、Agent、状态、模型、traceId、时间范围查询。
- 增加用户反馈：点赞/点踩、问题标记、人工修正答案，用于后续 prompt、RAG 和模型优化。
- 增加调用限流：按用户、应用、API Key、Agent 并发数和调用频率控制。
- Agent 绑定 workflow 后，仍保持“优先执行 workflow、不再走普通 LLM/RAG/MCP 对话链路”的语义。

#### hify-knowledge：知识库 RAG 生产化

- 文档处理队列化，支持排队、并发控制、取消、失败重试、断点续跑和任务恢复。
- 增加重建索引和重向量化能力，覆盖 embedding 模型变更、chunk 策略变更和文档更新场景。
- 增加元数据过滤，如部门、文档类型、标签、时间范围、权限范围。
- 增加混合检索能力：向量检索 + 关键词检索；后续可选接入 rerank 模型。
- 强化 pgvector 性能治理：ivfflat/hnsw 索引、probes 参数、慢查询监控、按知识库和向量维度过滤。
- 增加知识库权限和共享策略，避免敏感知识库被其他项目 Agent 检索。
- 增强文档处理可观测性，展示解析、切片、embedding、写入 pgvector 的进度和错误。

#### hify-workflow：工作流生产化

- Workflow 可发布为内部 Web App、API Endpoint，或作为受控工具被其他应用调用。
- 完善版本管理：草稿、发布版本、灰度、回滚和版本差异查看。
- 增加错误分支、节点重试、节点 timeout、失败后人工处理。
- 完善运行中心：运行历史、事件回放、节点输入输出、失败重跑、按状态筛选。
- 完善人工审核：待办列表、审批人、审批记录、超时处理和通知。
- 强化 `CODE_TASK` 安全：必须走 MCP Code Worker，增加沙箱要求、审批要求、diff 审计和调用权限。
- 增加变量管理面板，帮助用户引用 `{{nodeKey.varName}}`，减少手写变量错误。
- 谨慎增加 Trigger 能力，先支持 Webhook 触发和定时触发，不扩展成完整通用自动化平台。

#### hify-mcp：工具接入和安全

- 增加 MCP Server 的项目/空间归属和权限控制，工具不能默认全局可用。
- 增加 SSRF 防护：endpoint 白名单、协议限制、端口限制、内网访问策略和调用超时。
- 增强调用审计，支持按应用、Agent、Workflow、用户、工具、状态、耗时查询。
- 增加工具参数 schema 校验，减少模型生成非法参数导致的调用失败。
- 增加工具超时、重试和失败降级策略，避免慢工具阻塞整条 SSE 或 workflow 链路。
- 增加 OpenAPI 工具接入，作为 MCP 之外接入企业 REST API 的轻量方案。
- 增加 Secret/凭证管理，工具调用使用凭证引用，不把密钥散落在节点 JSON 中。

#### hify-app：启动、迁移和集成装配

- 增加 Flyway migration，支持项目空间、应用发布、API Key、审计、运行日志、限流配额等表。
- 增加系统初始化配置：默认空间、默认管理员、默认模型、默认限流策略。
- 完善健康检查，区分 liveness、readiness、deep health；deep health 检查 MySQL、Redis、pgvector 和关键模型 Provider。
- 增加后台 Job：Provider 健康检查、日志清理、过期 session 清理、知识库任务恢复、失败任务重试。
- 增加集成测试，覆盖登录、发布应用、API Key 调用、对话、RAG、Workflow、人审、MCP 核心链路。

#### hify-web：管理后台和用户体验

- 重整导航信息架构，按应用、Agent、工作流、知识库、工具、日志、审计、系统设置分区。
- 增加应用发布页，展示发布状态、访问地址、API Key、调用示例、版本回滚。
- 增加日志中心，统一查看对话日志、Workflow 运行日志、RAG trace、MCP 调用、LLM 调用。
- 增加项目成员、角色、资源授权和审计日志界面。
- 优化工作流编辑器，补齐变量面板、错误分支、节点重试、版本差异和运行事件回放。
- 优化知识库页面，展示处理队列、进度、重试、重建索引和元数据管理。
- SSE 和轮询页面必须支持刷新恢复，不能依赖页面内临时状态。

#### 部署和运维

- Docker Compose 保留单机交付能力，同时补充 1000 人规模生产推荐配置。
- K8s 模板完善多副本、readiness/liveness、资源限制、HPA、Ingress SSE 配置和滚动发布策略。
- MySQL、Redis、PostgreSQL 的连接池、备份、恢复、容量规划和慢查询治理需要文档化。
- 增加 Prometheus/Grafana 面板：请求量、错误率、SSE 连接数、LLM token、RAG 延迟、MCP 调用、Workflow run。
- 运行日志和审计日志分开管理，支持保留周期配置、归档和敏感字段脱敏。

### 推荐实施顺序

1. 先做 `hify-auth` + `hify-common`：项目空间、权限、审计、限流、trace 和配额基础。
2. 再做 `hify-agent` + `hify-workflow` + `hify-conversation`：应用发布、API Key、版本和日志中心。
3. 再做 `hify-knowledge`：队列化、重建索引、权限、元数据过滤、检索性能治理。
4. 再做 `hify-model` + `hify-mcp`：Provider 健康、fallback、工具安全、OpenAPI 工具和凭证管理。
5. 最后统一打磨 `hify-web`、`hify-app` 和部署运维，形成 1000 人规模的可交付版本。

---
