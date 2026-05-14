# Hify AI 路由文档

> 本文件由原 `CLAUDE.md` 拆分而来，保留对应章节原文。

## LLM 调用规范

### Provider 与模型类型

- Provider 的 `baseUrl` 必须存储为供应商 API 根路径，Adapter 负责拼接具体 endpoint，禁止出现 `.../v1/v1/models` 这类重复路径。
- OpenAI-compatible Provider 优先复用统一 Adapter，通过 provider type 或 adapter capability 做差异处理。
- `model_config.type` 必须区分：
  - `CHAT`：用于 Agent、LLM 节点、普通对话。
  - `EMBEDDING`：用于知识库文档向量化和 RAG 检索。
- 前端选择模型时必须展示模型名称、modelId 和 Provider 名称，禁止只展示数据库 ID。
- 向量模型无法通过供应商 models 接口稳定枚举时，允许手动新增模型并标记为 `EMBEDDING`。

### 线程池配置

```java
// llm-pool: 非流式调用（阻塞等待完整响应）
@Bean("llmExecutor")
public ThreadPoolExecutor llmExecutor() {
    return new ThreadPoolExecutor(20, 50, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(100),
        new ThreadFactoryBuilder().setNameFormat("llm-pool-%d").setDaemon(true).build(),
        new ThreadPoolExecutor.CallerRunsPolicy()  // 满载时调用方线程执行，不丢任务
    );
}

// llm-stream: 流式 SSE 调用（长连接）
@Bean("llmStreamExecutor")
public ThreadPoolExecutor llmStreamExecutor() {
    return new ThreadPoolExecutor(30, 80, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(50),
        new ThreadFactoryBuilder().setNameFormat("llm-stream-%d").setDaemon(true).build(),
        new AbortPolicy()  // 流式超限直接拒绝，由上层返回 503
    );
}
```

### OkHttpClient 配置

```java
// 非流式：有 readTimeout
@Bean("standardLlmClient")
public OkHttpClient standardLlmClient() {
    return new OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
        .addInterceptor(new LoggingInterceptor())
        .build();
}

// 流式：readTimeout 设为 0（SSE 不能有读超时）
@Bean("streamLlmClient")
public OkHttpClient streamLlmClient() {
    return new OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();
}
```

### 超时层次（三层保护）

1. OkHttp connectTimeout = 5s（TCP 握手超时）
2. OkHttp readTimeout = 120s（单次读取超时，仅非流式）
3. CompletableFuture.get(90, TimeUnit.SECONDS)（总体超时兜底）

### 重试策略（Resilience4j）

- 普通 LLM：最多 3 次，初始等待 500ms，指数退避 2x，最大等待 10s
- Ollama（本地）：最多 5 次，初始等待 2s
- 仅对网络异常和 5xx 重试，4xx（参数错误）不重试

### 熔断器配置

```yaml
# COUNT_BASED 滑动窗口，20 次请求内失败率 >50% 触发熔断
# 慢调用（>30s）超过 80% 也触发熔断
# 熔断后等待 30s 进入 half-open，放行 5 次探测
failure-rate-threshold: 50
slow-call-duration-threshold: 30s
slow-call-rate-threshold: 80
wait-duration-in-open-state: 30s
permitted-calls-in-half-open-state: 5
```

### Fallback 路由

```yaml
hify.llm.fallback:
  openai: ollama
  claude: openai
  deepseek: openai
  gemini: ollama
```

主 Provider 熔断或异常时自动切换 fallback，fallback 失败则抛出 BizException。

### RAG 注入位置

- RAG 检索必须发生在构造 LLM messages 阶段，位置为 System Prompt 之后、历史消息之前。
- Agent 未绑定知识库时不做 embedding 和 pgvector 查询。
- Agent 绑定知识库后：
  1. 用用户当前消息生成 query embedding。
  2. 在绑定知识库内检索 topK chunk。
  3. 按 score 阈值过滤低相关 chunk。
  4. 将参考资料拼入 system prompt。
- RAG 注入格式必须稳定，避免破坏原始 system prompt：

```text
{Agent 原始 Prompt}

请基于以下参考资料回答用户问题。
如果资料中没有相关信息，直接说"我没有找到相关资料"，不要编造。

【参考资料】
[1] {chunk1内容}
[2] {chunk2内容}
```

- 后续改动不得把 RAG 内容作为 user message 注入；否则会污染用户消息语义，也不利于调试命中。

### MCP 工具调用

- 工具列表为空时，普通对话链路必须保持原行为。
- 工具列表不为空时，第一次 LLM 调用携带 tools schema。
- 如果返回 `finish_reason=tool_calls`，先执行工具，再追加 `role=tool` 消息发起第二次 LLM 流式调用。
- 工具调用失败不能直接中断 SSE，对话链路应把错误作为 tool message 交回 LLM，让模型生成可读说明。
- Agent 同时绑定知识库和工具时，RAG 与 tools 不冲突：RAG 在 system prompt，tools 在 LLM request 参数。
- 工具调用必须记录 toolName、arguments keys、耗时、成功/失败，避免排障时只能看到模型最终回答。

---
