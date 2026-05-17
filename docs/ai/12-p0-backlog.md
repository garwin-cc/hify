# Hify AI 路由文档

> 本文件由原 `CLAUDE.md` 拆分而来，保留对应章节原文。

## P0 后续建设重点

Hify 下一阶段优先补齐“内部可用 AI 平台”的稳定闭环，而不是继续横向复制 Dify 的完整产品线。P0 范围聚焦四类能力：RAG 任务可靠性、对话运行可观测、Agent 记忆一期、Workflow 运行排障。

### 1. RAG 任务可靠性

#### 目标

知识库文档上传、解析、分块、向量化、入库必须可控、可恢复、可解释。用户不能只看到“处理中”或“失败”，必须知道失败原因，并可以重试或取消。

#### 必须补齐

- 文档处理任务状态需要覆盖完整生命周期：
  - `PENDING`
  - `PROCESSING`
  - `DONE`
  - `FAILED`
  - `CANCELED`
- 文档处理失败必须保存结构化错误信息：
  - `error_code`
  - `error_message`
  - `failed_stage`
  - `retryable`
- 支持失败文档重试，重试时必须清理旧的半成品 chunk，避免重复向量。
- 支持取消处理中任务，取消后不得继续写入 chunk。
- 文档处理必须有资源保护：
  - 单文件大小限制。
  - 最大分块数限制。
  - 单任务内存使用控制。
  - embedding 批次大小控制。
  - 并发任务数限制。
- 大文件处理禁止一次性把完整内容、所有 chunk、所有 embedding 全部常驻内存。
- 文档处理进度应按阶段暴露给前端：
  - 文件接收完成。
  - 文本解析完成。
  - 分块完成。
  - embedding 处理中。
  - pgvector 写入完成。

#### 不做

- 不做分布式任务调度系统。
- 不引入复杂 MQ，除非当前线程池和数据库状态机无法支撑。
- 不做跨 MySQL 和 PostgreSQL 的分布式事务。

#### 验收标准

- 200MB 以内受支持文件不会导致后端 OOM。
- 任务失败后前端能显示明确失败阶段和原因。
- 用户可以对失败文档执行重试。
- 删除或取消文档后，不会留下可被检索到的脏 chunk。
- 后端日志中能通过 traceId 定位完整处理链路。

### 2. 对话运行可观测

#### 目标

一次 Agent 对话必须能解释“为什么这样回答”。尤其是 RAG、MCP、模型调用、工作流触发这些链路，不能只在失败时给用户一个系统错误。

#### 必须补齐

- 每次对话请求生成统一 traceId，并贯穿：
  - 用户消息入库。
  - Agent 配置加载。
  - RAG 检索。
  - MCP 工具调用。
  - LLM 请求。
  - SSE 输出。
  - assistant 消息落库。
- 对话详情页或调试面板应能展示：
  - 使用的 Agent。
  - 使用的模型 Provider 和 modelId。
  - 是否触发 RAG。
  - RAG 命中的知识库、文档、chunk、score。
  - 是否触发 MCP 工具。
  - 工具名称、参数 key、耗时、成功/失败。
  - LLM 首 token 延迟、总耗时、错误信息。
- 对用户暴露的错误要可读，对日志保留完整排障信息。
- SSE 断开时要区分：
  - 用户主动断开。
  - 模型调用失败。
  - 后端异常。
  - 超时。
- 对话失败后，assistant 消息必须进入明确状态：
  - `ERROR`
  - 或保存部分输出并标记未完成。

#### 不做

- 不做完整 APM 平台。
- 不在前端暴露 API Key、完整请求头、敏感工具返回。
- 不把所有 debug 信息直接拼进用户对话内容。

#### 验收标准

- 任意一次失败对话都能通过 traceId 查到后端日志。
- RAG 回答能看到引用来源和 score。
- 工具调用失败时，用户看到可理解说明，开发者能看到工具调用失败细节。
- SSE 中断不会留下状态不明的 assistant 消息。

### 3. Agent 记忆一期

#### 目标

Agent 不能只依赖简单历史裁剪。短期上下文用于保持当前会话连贯性，长期摘要记忆用于保留跨轮次的重要信息，同时必须允许用户清理或关闭。

#### 记忆分层

- 短期上下文：
  - 来自当前会话最近消息。
  - 受 token budget 控制。
  - 可裁剪。
- 会话摘要：
  - 当历史消息超过阈值时生成摘要。
  - 摘要参与后续上下文构造。
  - 摘要必须记录更新时间和来源会话。
- 长期记忆：
  - 一期只做轻量设计或预留，不强制实现复杂向量记忆。
  - 后续可扩展为用户偏好、事实记忆、项目记忆。

#### 上下文构造顺序

LLM messages 构造必须保持稳定顺序：

```text
1. Agent system prompt
2. RAG 参考资料
3. 会话摘要 / 记忆
4. 最近历史消息
5. 当前用户消息
```

RAG 资料仍然放在 system prompt 后，不作为 user message 注入。

#### 裁剪与压缩原则

- 裁剪适合短期上下文，成本低、确定性强。
- 压缩适合长期会话，但会引入模型成本、延迟和摘要失真。
- Hify 一期采用“裁剪 + 摘要”的组合：
  - 最近消息保留原文。
  - 更早消息压缩成摘要。
  - 摘要失败时回退为纯裁剪，不阻断对话。

#### 必须补齐

- Agent 级别记忆开关。
- 会话级别摘要字段或摘要表。
- 摘要生成阈值：
  - 按消息数。
  - 或按估算 token 数。
- 摘要更新失败不能影响主对话。
- 用户可以清空某个会话的记忆摘要。
- 日志必须记录是否使用摘要、摘要版本、摘要耗时。

#### 不做

- 一期不做复杂人格记忆。
- 一期不做跨用户共享记忆。
- 一期不做自动写入不可见长期记忆。
- 一期不把 RAG chunk 写入记忆，避免污染。

#### 验收标准

- 长会话不会因为简单裁剪丢失全部早期目标。
- 摘要生成失败时，对话仍能继续。
- 用户可以关闭 Agent 记忆。
- 上下文构造过程可在调试信息中解释。

### 4. Workflow 运行排障

#### 目标

Workflow 不只是能运行，还必须能定位为什么运行失败、哪个节点慢、哪个节点输出了错误内容。工作流是 Hify 的核心产品能力，调试体验优先于新增节点数量。

#### 必须补齐

- 每次 workflow run 必须记录：
  - workflow version id。
  - start input。
  - final output。
  - current node。
  - status。
  - error message。
  - traceId。
- 每个 node run 必须记录：
  - node key。
  - node type。
  - input snapshot。
  - output snapshot。
  - status。
  - start time。
  - end time。
  - duration。
  - error message。
- 前端运行详情页必须展示：
  - 节点执行顺序。
  - 节点状态。
  - 节点耗时。
  - 节点输入输出。
  - 失败节点错误。
- 支持失败后重跑整个 workflow run。
- 节点调试仍然不能写正式 run，但要复用正式执行器，避免调试和正式运行行为不一致。
- `HUMAN_REVIEW`、`CODE_TASK`、`API_CALL`、`MCP` 相关节点必须额外记录外部调用耗时和失败原因。

#### 不做

- 一期不做任意节点级回滚。
- 一期不做复杂 DAG 并行执行。
- 一期不做 n8n 式通用自动化能力。
- 一期不允许 Hify 后端直接执行 shell。

#### 验收标准

- 任意失败 workflow run 都能定位失败节点。
- 节点输入输出可查看，且不会泄露敏感凭证。
- 用户能从运行详情判断是配置错误、模型错误、工具错误还是数据错误。
- 工作流版本恢复后，新运行仍能追踪到对应 version id。

### P0 实施顺序建议

1. 先做 RAG 任务可靠性。
   - 原因：文档处理中、失败、OOM 已经真实暴露，是当前稳定性最高风险点。
2. 再做对话运行可观测。
   - 原因：Agent、RAG、MCP、Workflow 都汇聚到对话入口，没有可观测性会持续增加排障成本。
3. 再做 Workflow 运行排障。
   - 原因：工作流能力已经较多，下一步瓶颈不是新增节点，而是调试和失败定位。
4. 最后做 Agent 记忆一期。
   - 原因：记忆会影响上下文构造和回答质量，必须在可观测性具备后再推进，避免问题不可解释。

### P0 总体边界

P0 不追求功能数量，而追求稳定、可解释、可恢复。

优先级判断规则：

- 能减少 OOM、卡死、处理中不结束的问题，优先做。
- 能让失败原因可见，优先做。
- 能让用户自助重试、取消、恢复，优先做。
- 只是增加新节点、新模型、新页面入口的，延后。
- 会显著增加权限、租户、插件市场复杂度的，延后或不做。

## P0 验收执行清单

以下清单用于每次声明 Stage 2 稳定闭环完成前的专项验收。自动化测试优先覆盖可重复的状态转换、trace 记录和脱敏逻辑；文件大小、长连接断开、真实浏览器交互等场景可作为集成测试或人工 smoke test。

### 2026-05-17 已完成更新

- 已补充本文件的 P0 验收执行清单，后续 Stage 2 收尾按 RAG、对话可观测、Workflow 排障、Agent 摘要记忆、脱敏安全五类逐项验收。
- 已验证现有 RAG 可靠性单测覆盖通过：`mvn -pl hify-knowledge test`。
- 已补齐对话 trace 详情的 RAG 知识库名称回填：当 `t_conversation_rag_trace` 只保存 `knowledge_base_id` 且 `knowledge_base_name` 为空时，详情接口通过 `KnowledgeService` 解析名称，并增加单测覆盖。
- 已补齐 Workflow node/run 快照落库前脱敏：`input_snapshot`、`outputs`、`context_snapshot` 写入前统一经过 `WorkflowSnapshotSanitizer`，并增加敏感字段不落明文的单测覆盖。
- 已修正 app 集成测试 H2 mock schema，使 `t_conversation_trace.project_id` 与生产迁移保持一致，避免对话 trace 落库在集成测试中被字段缺失异常吞掉。
- 已通过验证：`mvn -pl hify-workflow test`、`mvn -pl hify-conversation test`、`mvn -pl hify-knowledge test`、`mvn -pl hify-app -am test`、`mvn -pl hify-app -am -Dtest=ChatControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`、`npm run build --prefix hify-web`。
- 待后续专项验收：真实 200MB 内大文件处理、SSE 客户端断开/超时、前端浏览器人工 smoke test。

### 1. RAG 任务可靠性

- 上传支持格式的正常文档后，文档状态最终进入 `DONE`，`chunk_count` 和知识库 `chunk_count` 一致。
- 上传空文件、超大文件、不支持格式或无法解析文件时，文档进入 `FAILED`，并写入 `error_code`、`error_message`、`failed_stage`、`retryable`。
- 对 `FAILED` 或 `CANCELED` 文档执行重试时，必须先清理旧 chunk，再重置进度并创建新处理任务。
- 对 `PENDING` 或 `PROCESSING` 文档执行取消后，状态进入 `CANCELED`，不得继续写入可检索 chunk。
- 删除文档或知识库后，MySQL 元数据和 pgvector chunk 不得留下可检索脏数据。

### 2. 对话运行可观测

- 每次对话请求必须生成 `traceId`，并写入用户消息、assistant 消息、conversation trace、RAG trace、LLM trace 和 MCP 调用审计。
- 对话详情必须能展示 Agent、模型 Provider/modelId、RAG 命中知识库/文档/chunk/score、MCP 工具名/参数 key/耗时/结果、LLM 首 token/总耗时/token/错误。
- LLM 超时、限流、普通失败和后端异常时，assistant 消息必须进入 `ERROR` 或带 `partial` 的明确状态。
- SSE 超时或客户端断开时，trace 状态必须区分 `TIMEOUT`、`CLIENT_DISCONNECTED`、`ERROR` 或 `BACKEND_ERROR`。
- 前端 trace 面板不得暴露 API Key、token、clientSecret、MCP authConfig 或完整敏感工具返回。

### 3. Workflow 运行排障

- 每次 workflow run 必须记录 `traceId`、`workflow_version_id`、输入、输出、当前节点、状态、错误、耗时。
- 每个 node run 必须记录节点 key/type、执行状态、输入快照、输出快照、错误、开始/结束时间和耗时。
- `LLM`、`API_CALL`、`TOOL/MCP`、`CODE_TASK`、`HUMAN_REVIEW` 等外部调用必须记录 call trace，并保存脱敏后的 request/response、耗时和失败原因。
- 失败 run 的前端详情页必须能定位失败节点，并展示节点输入、输出、外部调用和错误原因。
- 失败 run 重跑必须复用原始 input，并记录 `rerun_from_run_id`。

### 4. Agent 会话摘要记忆

- Agent 记忆默认关闭，开启后才读取和更新会话摘要。
- 达到摘要阈值后异步生成摘要，摘要写入 `t_chat_session_summary`，并在后续上下文中按固定顺序注入。
- 摘要生成失败时只记录失败摘要和 trace 错误，不阻断主对话。
- 用户清空会话摘要后，后续对话不应继续使用旧摘要。
- trace 面板必须展示是否启用记忆、是否使用摘要、摘要版本、耗时和错误。

### 5. 脱敏和安全验收

- Workflow node run 的 `input_snapshot`、`outputs` 和 run `context_snapshot` 不得明文保存 `authorization`、`apiKey`、`api_key`、`token`、`secret`、`password`、`cookie`、`set-cookie` 等字段。
- Workflow call trace 的 request/response snapshot 必须使用同一类脱敏规则。
- 对话、RAG、MCP、LLM trace 中不得保存 Provider API Key、MCP authConfig、OAuth token 或完整请求头。
- 前端调试面板只展示排障必要字段，敏感字段只能显示掩码。
