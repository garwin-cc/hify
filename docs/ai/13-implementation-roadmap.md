# Hify 实现路线图

> 本文件记录经过完整讨论后确定的分阶段实现路径。AI 接到实现任务时，先查本文件确认该功能属于哪个 Stage、是否在当前阶段、是否在"不做"列表中，再进入具体实现。

---

## 总体原则

路径按「稳定性 → 可观测性 → 节点能力 → 权限治理 → RAG 质量 → 运营规模」递进。

每个 Stage 内部各项可并行推进；Stage 之间存在依赖：Stage 2 完成后 Stage 3 才有意义（引擎稳定才扩节点）；Stage 4 依赖 Stage 1 的权限修复；Stage 5–7 依赖 Stage 4 的项目隔离基础。

**Hify 的产品差异化定位**：工具调用可控可审计、工作流人审安全、部署可解释。后续建设围绕加深这三点，而不是追赶 Dify 功能数量。

---

## 明确不做的功能

以下功能 Dify 有，Hify 不做，AI 实现时遇到相关需求应拒绝或提示讨论：

| 功能 | 原因 |
|-----|------|
| 公开插件市场 | 内部场景靠 MCP + OpenAPI 工具扩展已够 |
| SaaS 多租户计费套餐 | 内部部署无商业化需求 |
| 实时协作编辑 | 版本快照 + 编辑锁已够，协作编辑复杂度极高 |
| n8n 式通用自动化 | 工作流围绕 AI 编排，不做全能自动化平台 |
| Text Generator 独立应用类型 | Agent 即可完成一次性生成任务 |
| 多模态主线（图片/音频） | 有明确内部需求时增量接入，不列主线 |
| Agent 子节点 | 内部场景不需要嵌套 Agent 编排 |
| Question Classifier 独立节点 | LLM + CONDITION 可替代 |
| Parameter Extractor 独立节点 | LLM + JSON 格式 prompt 可替代 |
| 并行分支 / Variable Aggregator | 不计划做并行执行，此节点无使用场景 |

---

## Stage 1：消除现存风险（立即执行）

处理已发现的缺陷，不引入新功能。

### 1-1 权限漏洞修复

**背景**：权限审查发现两个会影响正常使用的问题。

- **VIEWER 无法提交 HUMAN_REVIEW**：UserRole=VIEWER 的用户无法审批，工作流永久卡在 WAITING。需明确审核者角色要求：在产品层面规定审核者须为 EDITOR 及以上，`submitReview` 保持 `@RequireRole(ADMIN, EDITOR)`，前端和文档说明此约束。
- **EDITOR 无法查看 MCP 工具列表**：`McpController` 类级别要求 ADMIN，EDITOR 可绑定工具但无法浏览。将 GET 接口（列表、详情）降为 `@RequireRole(ADMIN, EDITOR)`，写操作保留 ADMIN。

### 1-2 CONDITION 节点表达式增强

**背景**：当前仅支持 `==` 和 `!=` 字符串比较，几乎所有真实工作流都会遇到限制。

需支持：

| 类型 | 运算符 |
|-----|-------|
| 数值比较 | `>` `<` `>=` `<=` |
| 字符串 | `contains` `startsWith` `endsWith` |
| 空值 | `isEmpty` `isNotEmpty` |
| 逻辑组合 | AND / OR 多条件 |

改动范围：仅 `ConditionNodeExecutor.evaluate()` 的表达式解析逻辑，不涉及引擎和配置结构变更。同步更新 `ConditionNodeConfig` 以支持条件列表格式。

### 1-3 TOOL 节点执行器实现

**背景**：`TOOL` 的枚举和配置类（`toolName + inputMapping`）均已就位，执行器缺失，调用直接抛异常。工作流内直接调用 MCP 工具是内部自动化核心能力。

改动范围：新增 `ToolNodeExecutor`，复用已有的 `McpClientService.callTool`，将工具返回结果写入 ExecutionContext。更新 `NodeConfigParser.parseExecutionConfig` 移除 TOOL 的不支持异常，新增 TOOL 的 `NodeConfigDef` 解析分支。

---

## Stage 2：P0 稳定性（核心优先级）

按顺序执行，详细任务清单见 [`docs/ai/12-p0-backlog.md`](12-p0-backlog.md)。Stage 2 以补齐已有能力闭环为主，优先复用现有表、trace、任务状态和前端页面，不重复造新模块。

### 2-1 RAG 任务可靠性

目标：文档处理链路必须可控、可恢复、可解释。

- 已有 `error_code`、`error_message`、`failed_stage`、`retryable`、`process_stage`、`cancel_requested` 等字段时，先检查状态流转和前端展示是否闭环。
- 失败重试时先清理旧 chunk 再重新处理，防止脏向量被检索。
- 取消以数据库 `cancel_requested` 为事实来源，处理循环在解析、切片、embedding batch、保存前后检查，内存标志只能做加速。
- 单文件大小、最大分块数、embedding 批次、并发任务数均加上限保护。
- 前端按 `process_stage` 字段驱动进度展示，不依赖固定等待时间。

### 2-2 对话运行可观测

目标：任意一次 Agent 对话失败，能通过 traceId 定位到 RAG / MCP / LLM 哪个环节出了问题。

- traceId 贯穿：用户消息入库 → RAG 检索 → MCP 工具调用 → LLM 请求 → SSE 输出 → assistant 消息落库，跨线程通过 `TraceContext.wrap` 传播
- `t_conversation_trace` 只保存索引字段和概要状态；RAG 命中、MCP 调用、LLM 请求明细继续放子表或审计表。
- SSE 断开区分用户主动断开 / 模型调用失败 / 后端异常 / 超时，assistant 消息进入确定状态（DONE / ERROR / PARTIAL）
- 前端对话详情页展示 Trace 信息，不暴露 API Key 和敏感工具返回

### 2-3 Workflow 运行排障

目标：任意失败的工作流运行都能定位失败节点，节点输入输出可查。

- 确保异常路径下 `t_workflow_node_run` 的 `input_snapshot` 和 `outputs` 均已写入。
- 前端运行详情页展示节点执行顺序、状态、耗时、输入输出、失败错误
- 前端补充失败重跑入口（接口已有）
- HUMAN_REVIEW / CODE_TASK / API_CALL 节点的外部调用耗时和失败原因写入 `WorkflowCallTrace`

### 2-4 Agent 会话摘要记忆

目标：长会话不因简单裁剪丢失早期目标，摘要失败时不阻断对话。

- 消息数或估算 Token 数超阈值时，调用指定模型生成摘要，写入 `t_chat_session_summary`
- LLM messages 构造顺序固定：Agent system prompt → RAG 参考资料 → 会话摘要 → 最近历史消息 → 当前用户消息
- 摘要生成失败时回退为纯裁剪，记录 warn 日志，不抛异常
- `t_agent` 新增 `memory_enabled` 字段，关闭后跳过摘要生成和注入

---

## Stage 3：工作流节点能力补全

在 Stage 2 完成、引擎稳定可排障后推进。

### 3-1 API_CALL 节点增强

当前问题：仅 GET/POST，POST body 硬编码 `{}`，无响应字段提取。

- `ApiCallNodeConfig` 新增 `body`（模板字符串）和 `responseJsonPath` 字段
- 支持 PUT / DELETE / PATCH 方法
- `body` 字段通过 `ctx.resolve()` 插值，支持 `{{variable}}` 引用
- 执行器通过 JsonPath 从响应 JSON 中提取指定字段写入输出变量
- 响应非 2xx 时记录错误到 `nodeKey.error`，由 `NodeRuntimePolicy.onFailure` 决定后续走向

### 3-2 REPLY 节点实现

允许工作流在执行中途向用户推送中间内容，不必等到 END 才输出。配置类 `ReplyNodeConfig(content)` 已就位。

- 执行器通过 `ctx.resolve(config.content())` 渲染内容，写入 `nodeKey.reply` 变量
- 异步运行时，SSE 事件流发布 `NODE_REPLY` 事件，前端实时渲染
- REPLY 执行后继续流转，不终止工作流（区别于 END）
- 更新 `NodeConfigParser.parseExecutionConfig` 支持 REPLY 类型

### 3-3 ITERATION 迭代节点

对数组逐项执行是批量场景必备能力（批量调用 API、逐条 LLM 加工、对列表每项做知识检索）。

- `IterationNodeConfig` 包含：`inputArrayVariable`（来源数组）、`itemVariable`（元素变量名）、`subflowStartNodeKey`（子流程入口）、`outputVariable`（收集结果数组）、`maxConcurrency`（一期固定为 1，顺序执行）
- `WorkflowEngine` 遇到 ITERATION 节点时，从 ctx 中取出数组，对每个元素新建子 ExecutionContext（注入 `item` 变量），执行子图，将结果收集为输出数组写回父 ctx
- 新增 `ITERATION_END` 节点类型标记子流程终止，子流程内禁止嵌套 ITERATION（一期限制）
- 每次迭代的节点 run 记录附带 `iteration_index` 字段，便于排障时区分第几次迭代失败
- ITERATION 节点本身算 1 步，子流程步数单独计数，防止大数组触发 MAX_STEPS 限制

### 3-4 Variable Assigner 节点

允许在工作流中途直接给变量赋值，不需要调用 LLM，适合格式转换、条件赋值等轻量场景。

- `VariableAssignerNodeConfig` 包含 `assignments`（`Map<String, String>`，key 为输出变量名，value 为模板字符串）
- 执行器仅调用 `ctx.resolve(template)` 并 `ctx.set(nodeKey, varName, value)`，无外部调用

---

## Stage 4：生产化基础

### 4-1 项目级权限激活

**前提**：Stage 1 权限修复已完成，ProjectRole 模型已就位。

- `hify.auth.project-permission-enabled` 改为 `true`
- `t_agent`、`t_workflow`、`t_knowledge_base`、`t_mcp_server` 各增加 `project_id` 字段（Flyway migration），存量数据归到默认项目
- 核心写接口（Agent、Workflow、Knowledge、MCP）添加 `@RequireProjectPermission` 注解
- 补全 `ProjectController` 正式接口：项目成员增删改查、角色变更
- Agent 绑定知识库/工具/工作流时，校验被绑定资源与 Agent 同属一个项目

### 4-2 Agent 应用发布与 API Key

- `t_agent` 新增 `published_at`、`published_config_snapshot` 字段，发布时做配置快照，线上运行走快照
- 对话接口支持 `X-Api-Key` 鉴权路径（不需要 Bearer Token），绑定到对应 Agent 上下文
- 每个 Agent 发布后生成访问路径（如 `/app/{agentId}`），前端提供极简对话页面，供非研发人员直接使用

### 4-3 对话日志中心

- `ConversationController` 增加管理员视角查询接口，支持按用户、Agent、状态、时间范围、traceId 过滤
- 每条对话记录可展开查看 Stage 2-2 的完整 Trace 数据
- `t_chat_message` 新增 `feedback` 字段，提供点赞/点踩接口

---

## Stage 5：RAG 质量提升

### 5-1 混合检索

- pgvector 中建立 `tsvector` + GIN 全文索引
- 检索时并行执行向量查询和全文查询，按 `alpha` 参数加权合并排名（RRF 融合）
- `UpdateKnowledgeRetrievalConfigReq` 新增 `searchMode`（VECTOR / FULLTEXT / HYBRID）和 `hybridAlpha`

### 5-2 Rerank 模型支持

- `hify-model` 新增 `RERANK` 模型类型，接入 Cohere / BGE-Reranker 等 rerank 接口
- `KnowledgeService.searchSimilar` 检索后可选调用 Rerank 服务精排
- `UpdateKnowledgeRetrievalConfigReq` 新增 `rerankEnabled`、`rerankModelConfigId`

### 5-3 元数据过滤

- `t_knowledge_chunk` 新增 `metadata` JSON 字段，文档上传时允许携带元数据（部门、标签、日期等）
- pgvector 查询增加 `metadata @> '{...}'::jsonb` 过滤条件
- 知识库检索配置支持元数据过滤规则

### 5-4 重建索引

- `POST /{id}/rebuild-index` 接口（已有）补全 Service 实现
- 重建时知识库置为 `REBUILDING` 状态，异步逐文档重新解析→分块→向量化→写入，旧向量软切换，不影响线上检索

---

## Stage 6：工具生态安全加固

### 6-1 MCP SSRF 防护

- 创建/更新 MCP Server 时校验 endpoint URL 协议（仅 http/https）、端口（黑名单 22/3306/5432 等）
- 可配置内网 IP 段白名单，拒绝直接访问私有地址
- 基于现有 `McpEndpointGuard` 加固：协议、端口白名单、端口黑名单、host allowlist、私有 CIDR allowlist、可选 DNS 解析后私网地址检查统一在创建/更新和调用前执行。

### 6-2 工具参数 Schema 校验

- 工具调用前，用 MCP Server 同步到本地的 `input_schema` 对 LLM 生成的 arguments 做 JSON Schema 校验
- 校验失败时不调用工具，将错误作为 tool message 返回 LLM
- 一期支持 JSON Schema 常用子集：`required`、`properties`、`additionalProperties=false`、`enum`、嵌套 `object`、`array.items`、字符串长度/正则、数值范围；不追完整 Draft 规范。

### 6-3 工具调用超时与降级

- `ToolNodeConfig` 和工具绑定增加 `timeoutSeconds` 配置
- MCP 调用使用 `CompletableFuture.get(timeout)` 包裹，超时后返回受控错误，不阻塞 SSE 链路
- Workflow `TOOL` 节点的单次 `timeoutSeconds` 转为 `McpToolCallRequest.timeoutMs`，优先级高于工具级和 Server 级默认超时。

---

## Stage 7：平台运营规模化（面向 1000 人生产部署）

### 7-1 企业 SSO

- 完善 `IdentityProviderService` 中 LDAP / OIDC 协议的实际鉴权逻辑（配置表已有）
- 登录页增加 SSO 入口，支持 IdP 发起和 SP 发起两种模式

### 7-2 限流与配额

- `hify-common` 新增基于 Redis 的统一限流/配额组件
- 支持维度：用户、应用、API Key、Provider、模型
- 超额返回标准 429 响应
- 已落地后端基础闭环：`t_rate_limit_quota` 解析为统一 `RateLimitRule`，对话链路支持 AGENT / USER / APP / API_KEY 表驱动配额，LLM 调用支持 PROVIDER / MODEL 维度配额。

### 7-3 Provider 健康看板

- 定时任务定期探测所有启用的 Provider（`t_provider_health` 表已有）
- 前端 Provider 列表展示实时健康状态（UP / DEGRADED / DOWN）
- Provider DOWN 时触发告警（邮件/Webhook，可配置）
- 已落地 Webhook 告警基础能力：连续失败达到阈值后状态转为 DOWN，并在 `hify.provider-health.alert.enabled=true` 且配置 `webhook-url` 时发送告警事件。Provider 列表补充最近检测、连续失败、告警状态和失败原因。

### 7-4 Prometheus / Grafana 面板

- 补全 `hify_` 前缀指标：LLM Token 用量、RAG 检索延迟、MCP 调用量/失败率、Workflow 运行数/成功率、SSE 活跃连接数
- 提供 Grafana Dashboard JSON 模板，开箱可导入
- 已落地 `hify_sse_active_connections`、`hify_llm_tokens_total`、`hify_rag_retrievals_total`、`hify_rag_retrieval_latency_seconds`、`hify_workflow_runs_total`、`hify_workflow_run_latency_seconds`，并接入对话 SSE、RAG 检索、LLM 调用和 Workflow run 状态变更。

### 7-5 后台 Job 管理

- Provider 健康检查定时任务
- 过期 Session Token 清理
- 文档处理超时任务恢复（PROCESSING 超阈值自动重置为 FAILED）
- 工作流运行超时检测
- RAG 向量孤儿 chunk 清理（文档删除后 pgvector 残留）
- 已落地 app 侧统一 Job 日志：过期 Session、运行/审计/Job 日志清理、知识库 PROCESSING 超时恢复、Workflow RUNNING/WAITING 超时失败化、pgvector 孤儿 chunk 清理。

---

## 实现顺序一览

```
Stage 1  权限漏洞修复 + CONDITION增强 + TOOL执行器       ← 立即
Stage 2  RAG可靠性 → 对话可观测 → Workflow排障 → 记忆    ← P0 核心
Stage 3  API_CALL增强 → REPLY → ITERATION → 变量赋值     ← 节点能力
Stage 4  项目权限激活 → 应用发布 → 日志中心              ← 生产化基础
Stage 5  混合检索 → Rerank → 元数据过滤 → 重建索引       ← RAG质量
Stage 6  SSRF防护 → 参数校验 → 工具超时                  ← 安全加固
Stage 7  限流配额 → Provider看板 → Job → 监控 → SSO      ← 规模化运营
```

---

## 工作流节点现状速查

AI 新增或修改工作流节点时，先对照以下表格确认范围：

| 节点类型 | 枚举 | 配置类 | 执行器 | Stage |
|---------|-----|-------|-------|-------|
| START | ✓ | ✓ | ✓ | 已完成 |
| END | ✓ | ✓ | ✓ | 已完成 |
| LLM | ✓ | ✓ | ✓ | 已完成 |
| CONDITION | ✓ | ✓ | ✓（仅 == !=） | Stage 1 增强 |
| API_CALL | ✓ | ✓ | ✓（GET/POST 无 body） | Stage 3 增强 |
| KNOWLEDGE | ✓ | ✓ | ✓ | 已完成 |
| HUMAN_REVIEW | ✓ | ✓ | ✓ | 已完成 |
| CODE_TASK | ✓ | ✓ | ✓ | 已完成 |
| TOOL | ✓ | ✓ | ✗ 抛异常 | Stage 1 实现 |
| REPLY | ✓ | ✓ | ✗ 抛异常 | Stage 3 实现 |
| ITERATION | ✗ | ✗ | ✗ | Stage 3 新增 |
| VARIABLE_ASSIGNER | ✗ | ✗ | ✗ | Stage 3 新增 |
