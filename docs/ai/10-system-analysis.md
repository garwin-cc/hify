# Hify AI 路由文档

> 本文件由原 `CLAUDE.md` 拆分而来，保留对应章节原文。

## 系统分析清单

### 1. 核心链路清单

#### 1. 对话 SSE 主链路

- 涉及模块和类：
  - `hify-conversation`
    - `ConversationController.stream`
    - `ConversationServiceImpl.sendMessage`
    - `ConversationServiceImpl.doStream`
    - `ConversationServiceImpl.completeAssistantStream`
  - `hify-agent`
    - `AgentServiceImpl.getDetail`
  - `hify-model`
    - `LlmCallServiceImpl.streamChat`
    - `OpenAiAdapter.streamChat`
    - `LlmHttpClient.stream`
  - `hify-conversation.infra`
    - `ChatSessionMapper`
    - `ChatMessageMapper`

- 为什么是核心链路：
  - 这是用户实际使用频率最高的路径：发消息、建立会话、加载上下文、调用模型、SSE 返回 token、落库消息状态。
  - 任何异常都会直接表现为对话失败、SSE 中断、消息状态错误或会话计数不一致。

#### 2. RAG 文档入库链路

- 涉及模块和类：
  - `hify-knowledge`
    - `KnowledgeServiceImpl.uploadDocument`
    - `KnowledgeServiceImpl.processDocumentAsync`
    - `KnowledgeServiceImpl.extractText`
    - `KnowledgeServiceImpl.splitChunks`
    - `KnowledgeServiceImpl.embedChunks`
    - `KnowledgeServiceImpl.saveChunks`
    - `PgvectorKnowledgeVectorRepository.saveDocumentChunks`
  - `hify-model`
    - `EmbeddingServiceImpl.embed`
  - `hify-knowledge.infra`
    - `KnowledgeBaseMapper`
    - `KnowledgeDocumentMapper`

- 为什么是核心链路：
  - 这是知识库可用性的前置链路，文档上传后必须完成解析、切片、向量化、写入 pgvector。
  - 失败会导致知识库看似有文档，但对话时无法检索到有效上下文。

#### 3. RAG 检索注入链路

- 涉及模块和类：
  - `hify-conversation`
    - `ConversationServiceImpl.buildSystemPrompt`
  - `hify-knowledge`
    - `KnowledgeServiceImpl.searchSimilar`
    - `PgvectorKnowledgeVectorRepository.search`
  - `hify-model`
    - `EmbeddingServiceImpl.embed`

- 为什么是核心链路：
  - 决定 Agent 回答是否能使用知识库内容。
  - 检索质量、向量维度、score 阈值、知识库启用状态都会直接影响最终回答质量。

#### 4. MCP 工具调用链路

- 涉及模块和类：
  - `hify-agent`
    - `AgentServiceImpl.bindTools`
    - `AgentServiceImpl.validateToolIds`
  - `hify-conversation`
    - `ConversationServiceImpl.loadBoundTools`
    - `ConversationServiceImpl.toToolSchemas`
    - `ConversationServiceImpl.streamAfterToolCalls`
    - `ConversationServiceImpl.executeToolCall`
  - `hify-mcp`
    - `McpServiceImpl`
    - `McpClientServiceImpl.callTool`
    - `McpSdkClientFactory`
    - `McpRawHttpClient`

- 为什么是核心链路：
  - 这是 Agent 从“聊天”变成“可执行工具”的关键路径。
  - 工具 schema、模型 tool call 解析、MCP 服务调用、二次 LLM 生成任一环节失败，都会影响 Agent 的实际能力。

#### 5. 工作流执行链路

- 涉及模块和类：
  - `hify-conversation`
    - `ConversationServiceImpl.doStream`
  - `hify-workflow`
    - `WorkflowServiceImpl.create`
    - `WorkflowServiceImpl.update`
    - `WorkflowEngine.execute`
    - `NodeConfigParser`
    - `NodeExecutorRegistry`
    - `ConditionNodeExecutor`
    - `LlmNodeExecutor`
    - `KnowledgeNodeExecutor`
    - `ApiCallNodeExecutor`
  - `hify-workflow.infra`
    - `WorkflowRunMapper`
    - `WorkflowNodeRunMapper`
    - `WorkflowNodeMapper`
    - `WorkflowEdgeMapper`

- 为什么是核心链路：
  - Agent 绑定 `workflowId` 后，对话主链路会切换为工作流执行。
  - 节点配置、边选择、循环保护、运行记录都会影响最终返回结果和可观测性。

### 2. 风险集中区域

#### 1. `ConversationServiceImpl.sendMessage` / `doStream`

- 风险类型：并发 / 数据一致性 / 性能
- 可能失败场景：
  - `llmExecutor` 满载后使用 `CallerRunsPolicy`，Tomcat 请求线程可能被长时间占用，导致接口整体变慢。
  - SSE 已断开但 LLM 调用仍继续执行，会继续消耗模型额度和线程资源。
  - 用户消息已写入、助手占位消息已写入，但异步执行失败，可能留下 `ERROR` 或不完整状态。
  - `incrementSessionCount` 分散在多个异常分支，异常路径容易出现会话消息数与实际消息不一致。

#### 2. `ConversationServiceImpl.buildSystemPrompt`

- 风险类型：性能 / 安全 / 质量
- 可能失败场景：
  - 每次对话都会按知识库分组调用 embedding 和向量检索，知识库多时延迟明显上升。
  - RAG 内容直接拼进 system prompt，存在知识库内容 prompt injection 风险。
  - `RAG_MIN_SCORE = 0.65` 是固定阈值，模型或向量库切换后可能召回过少或召回噪声。
  - embedding 模型异常会直接影响整个对话链路。

#### 3. `OpenAiAdapter.streamChat` / `LlmHttpClient.stream`

- 风险类型：性能 / 可用性
- 可能失败场景：
  - `LlmHttpClient` 内部直接 new `OkHttpClient`，没有使用 `LlmHttpConfig.streamLlmClient` 中配置的 `readTimeout=0`，当前流式 readTimeout 为 120s，长回答可能被中断。
  - `CircuitBreakerService` 已存在，但当前 `LlmCallServiceImpl` 没有包裹熔断和重试，供应商故障时容易把请求线程和 LLM 线程池拖满。
  - 流式解析对 OpenAI-compatible 格式依赖较强，非标准供应商返回格式变化会导致 token、usage、tool_calls 丢失。

#### 4. `ConversationServiceImpl.streamAfterToolCalls` / `executeToolCall`

- 风险类型：安全 / 性能 / 可用性
- 可能失败场景：
  - MCP 工具由模型决定调用，若工具能力过强，可能造成越权操作或敏感数据泄露。
  - 工具调用在 LLM 流程中同步执行，慢工具会阻塞整条 SSE 链路。
  - inline tool call 依赖正则解析 DSML / DeepSeek 格式，格式轻微变化可能无法识别或误识别。
  - 工具失败被转成文本继续喂给模型，用户看到的可能是模型加工后的错误，排障难度较高。

#### 5. `KnowledgeServiceImpl.processDocumentAsync`

- 风险类型：数据一致性 / 性能
- 可能失败场景：
  - 文档表在 MySQL，向量块在 PostgreSQL，跨库无统一事务；MySQL 状态和 pgvector 数据可能不一致。
  - `document_count` 上传时增加，后续向量化失败不会回滚，可能出现文档数量正常但 chunk 为 0。
  - async 线程池只有 2-4 个线程，批量上传或大 PDF 会造成长时间排队。
  - embedding 分批调用外部模型，部分失败会导致整个文档 `FAILED`，没有断点续跑。

#### 6. `PgvectorKnowledgeVectorRepository.search`

- 风险类型：性能 / 数据正确性
- 可能失败场景：
  - 查询依赖 `ORDER BY embedding <=> vector`，如果 pgvector 索引未正确创建或未命中，会退化为全表扫描。
  - 查询时没有显式设置 `ivfflat.probes`，召回率和性能取决于数据库默认值。
  - `vector_dims(embedding) = :queryDimension` 可以避免维度错误，但也可能在模型切换后静默过滤掉所有历史数据。

#### 7. `WorkflowEngine.execute`

- 风险类型：数据一致性 / 可用性
- 可能失败场景：
  - 执行过程没有整体事务，运行记录写入失败只打 warn，不阻断执行，可能出现用户拿到结果但后台无完整运行轨迹。
  - `MAX_STEPS = 50` 能防循环，但复杂工作流可能被误杀。
  - `findNext` 对普通节点默认取第一条边，配置错误时可能走到非预期节点。
  - `execute` 查找 START 节点基于节点类型，不直接使用 `WorkflowPo.startNodeKey`，配置不一致时行为可能和前端预期不同。

#### 8. MCP Server endpoint 配置与 `McpRawHttpClient`

- 风险类型：安全 / 可用性
- 可能失败场景：
  - MCP endpoint 可配置外部 URL，如果没有白名单或内网访问限制，存在 SSRF 风险。
  - raw HTTP fallback 接受 `application/json, text/event-stream`，但只提取首个 JSON payload，复杂 SSE 响应可能解析失败。
  - 每次调用创建 SDK client，工具高频调用时连接开销和服务端压力较高。

### 3. 测试重心建议

#### 必须重点覆盖

1. 对话 SSE 主链路

- 覆盖点：
  - 新会话发送消息：创建 session、写入 user message、写入 assistant message、最终 assistant 为 `DONE`。
  - 已有 session 发送消息：校验 session 属于当前 agent。
  - LLM 超时 / 429 / 普通异常：assistant 标记 `ERROR`，SSE 返回 error event，session count 合理。
  - 客户端断开：不再发送 SSE，但最终结果仍能落库。
  - `maxContextTurns` 生效，历史消息窗口顺序正确。

2. LLM 适配器和流式解析

- 覆盖点：
  - 普通 token delta 解析。
  - `finish_reason`、usage、reasoning_content 解析。
  - tool_calls delta 分片累积。
  - 非 2xx、超时、空响应体的异常分类。
  - 长流式响应不应被 120s readTimeout 意外截断，当前实现需要专门回归测试暴露这个风险。

3. RAG 入库链路

- 覆盖点：
  - txt / md / pdf 基本文本提取。
  - 空文档、扫描 PDF、超大文件、不支持类型。
  - 切片 overlap、chunkIndex 连续性。
  - embedding 返回数量不匹配时文档状态为 `FAILED`。
  - MySQL 文档状态与 pgvector chunk 写入的一致性。

4. RAG 检索注入链路

- 覆盖点：
  - 知识库未绑定、禁用、无命中时不污染 system prompt。
  - 多知识库、多 embedding model 分组检索。
  - score 阈值过滤和 topK 排序。
  - embedding 维度不匹配时返回空结果或明确异常。
  - 注入 prompt 的格式稳定，避免破坏原 system prompt。

5. MCP 工具调用链路

- 覆盖点：
  - Agent 绑定工具数量限制、重复 ID 去重、禁用工具拦截。
  - tool schema 生成符合 OpenAI tools 格式。
  - 标准 tool_calls 和 inline tool call 都能解析。
  - 工具调用失败时不会中断整个对话，错误文本可控。
  - 最大工具轮次 `MAX_TOOL_ROUNDS = 3` 生效。

6. 工作流执行链路

- 覆盖点：
  - START -> LLM -> END 正常执行。
  - CONDITION true / false 分支选择。
  - 节点配置非法、目标节点不存在、循环超过 50 步。
  - 节点执行失败时 workflow run 和 node run 标记 `FAILED`。
  - Agent 绑定 workflow 后，对话链路走 workflow 而不是普通 LLM chat。

#### 次重点覆盖

- Agent 配置：
  - 名称唯一性。
  - modelConfig 启用校验。
  - toolIds 替换语义：`null` 不修改，空数组清空。
  - cache evict 后详情能读到最新值。

- Provider / Model 配置：
  - 禁用 provider 或 model 后不可被对话调用。
  - extraParams 默认值只在 request 未显式设置时生效。
  - API key、baseUrl 缺失时错误信息明确。

- 分页和索引相关查询：
  - 对话消息历史加载不能使用 offset 深分页。
  - session/message 高频查询字段与索引匹配。
  - pgvector 查询在测试库中至少有 EXPLAIN 覆盖，避免全表扫描回归。

#### 可以先跳过或轻覆盖

- 前端纯展示组件：
  - 列表样式、表单布局、静态页面交互可以先用少量 E2E smoke test 覆盖。
  - 不必为每个 Vue 组件写细粒度单测。

- Demo 模块：
  - `hify-app.demo` 属于样例代码，不在 MVP 核心链路内，可以只保留基础启动测试。

- 简单 CRUD 的 happy path：
  - Provider、Model、MCP Server、KnowledgeBase 的普通新增/查询/更新/删除可以用集成测试覆盖主路径，不必过度堆单元测试。

- 日志格式：
  - 除 LLM 调用、工作流运行记录、文档向量化状态外，普通日志内容不建议作为强断言。

---
