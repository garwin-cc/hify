# Hify Conversation Runtime Logging Design

## Scope

本期做 `hify-conversation` 后端闭环，目标是提升 1000 人规模下对话主链路的稳定性、可查询性和治理能力。前端日志中心页面、复杂策略配置 UI、外部 API Endpoint 执行链路不在本期。

## Design

### SSE 稳定性

对话流式执行从普通 `llmExecutor` 切换到可选的 `llmStreamExecutor`。如果容器中没有该 Bean，兼容回退到 `llmExecutor`。提交任务前检查线程池容量，满载时把助手消息标记为错误并返回受控 SSE error，避免请求无限排队。

SSE 发送失败后设置 `cancelled=true` 并标记 trace 为 `CLIENT_DISCONNECTED`。LLM 回调仍尽量完成消息落库和 trace 收尾，但不再继续写 SSE。Agent 绑定 workflow 时保持现有优先级：只启动 workflow，不进入普通 LLM/RAG/MCP 链路。

### 游标分页

保留旧的 `listSessions` 和 `listMessages` 兼容接口，新增游标分页接口：

- 会话：按 `last_message_at DESC, id DESC` 查询，游标为 `lastMessageAt + id`。
- 消息：按 `created_at ASC, id ASC` 查询，游标为 `createdAt + id`。

响应统一返回 `records / nextCursorId / nextCursorTime / hasMore`，不使用 offset。

### 记忆摘要

保留现有摘要机制：摘要失败不影响主对话，只写 `FAILED` summary 和 memory trace。新增日志查询时返回 `summaryUsed / summaryLatencyMs / summaryErrorMessage`，让排障能看到摘要是否参与本轮对话。

### 应用级会话日志中心

在 `t_conversation_trace` 补充 `user_id / app_id / api_key_id`。新增日志查询 API，支持按 user、Agent、状态、模型、traceId、app、apiKey、时间范围过滤。日志响应返回 trace、会话、消息、模型、workflow、RAG/MCP/summary 标志，不返回完整 prompt 或知识库原文。

### 用户反馈

新增 `t_message_feedback`。用户可对 assistant message 做点赞/点踩、问题标记和人工修正答案。一个用户对一条消息保留一条有效反馈，重复提交执行 upsert。

### 调用限流

复用 `hify-common` 的 `RateLimitService`，不新增策略表。默认规则：

- 用户维度：60 次/分钟。
- Agent 维度：120 次/分钟。
- App 维度：120 次/分钟。
- API Key 维度：120 次/分钟。

本期从 `SendMessageReq` 接收可选 `userId / appId / apiKeyId`，没有传入时使用 userId=0。限流发生在会话和消息落库之前，失败时返回 `TOO_MANY_REQUESTS` SSE error，不创建空会话或空 trace。

## Migration

新增 `V35__conversation_runtime_logging.sql`：

- `t_chat_session` 增加 `app_id / api_key_id`。
- `t_conversation_trace` 增加 `user_id / app_id / api_key_id` 和一个复合日志查询索引。
- 创建 `t_message_feedback`。

索引数量控制：`t_conversation_trace` 只新增一个复合查询索引，不超过当前项目约束。

## Testing

覆盖以下后端行为：

- 游标分页只取下一页，返回下一游标。
- 日志查询按条件返回 trace 摘要。
- feedback upsert 更新已有反馈。
- 限流拒绝时不提交流式任务，返回受控错误。
- Agent 绑定 workflow 后仍优先执行 workflow，不进入普通 LLM/RAG/MCP 链路。
