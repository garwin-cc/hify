# Hify AI 路由文档

> 本文件由原 `CLAUDE.md` 拆分而来，保留对应章节原文。

## 单元测试规范

### 1. 必须写单测的代码

以下代码属于核心链路或风险集中区域，必须有单元测试覆盖。单测目标是验证业务分支、状态转换、异常处理和边界条件，不依赖真实数据库、真实网络或真实 LLM。

#### 1. 对话 SSE 主链路

- 必须覆盖：
  - `ConversationServiceImpl.sendMessage`
  - `ConversationServiceImpl.doStream`
  - `ConversationServiceImpl.completeAssistantStream`
  - `ConversationServiceImpl.handleLlmStreamError`
  - `ConversationServiceImpl.buildContextMessages`
  - `ConversationServiceImpl.buildSystemPrompt`
  - `ConversationServiceImpl.streamAfterToolCalls`
  - `ConversationServiceImpl.executeToolCall`

- 必测场景：
  - 新会话创建、已有会话归属校验、Agent 禁用拦截。
  - 用户消息、助手占位消息、助手完成消息的状态流转。
  - LLM 超时、429、普通异常时 assistant message 标记 `ERROR`。
  - 客户端断开后不继续发送 SSE，但最终结果仍能落库。
  - `maxContextTurns` 限制历史上下文窗口，且消息顺序正确。
  - 绑定知识库时 RAG 内容正确注入 system prompt；无命中时不污染 prompt。
  - 绑定 MCP 工具时 tool schema 生成、工具调用、最大工具轮次限制。

#### 2. LLM 适配器与响应解析

- 必须覆盖：
  - `LlmCallServiceImpl.chat`
  - `LlmCallServiceImpl.streamChat`
  - `OpenAiAdapter.chat`
  - `OpenAiAdapter.streamChat`
  - `OpenAiAdapter` 中普通响应、流式 token、usage、reasoning_content、tool_calls 的解析逻辑
  - `LlmHttpClient` 的异常分类逻辑

- 必测场景：
  - modelConfig / provider 不存在或禁用时抛 `BizException`。
  - request 显式参数优先于 model extraParams。
  - 流式 delta 能累积完整 content。
  - tool_calls delta 分片能按 index 合并。
  - HTTP 401 / 403 / 429 / 5xx / timeout 映射为正确异常类型。

#### 3. RAG 文档处理与检索编排

- 必须覆盖：
  - `KnowledgeServiceImpl.validateUploadFile`
  - `KnowledgeServiceImpl.splitChunks`
  - `KnowledgeServiceImpl.embedChunks`
  - `KnowledgeServiceImpl.processDocumentAsync`
  - `KnowledgeServiceImpl.searchSimilar`
  - `EmbeddingServiceImpl.embed`

- 必测场景：
  - 空文件、超大文件、不支持类型被拒绝。
  - txt / md / pdf 解析失败时文档状态为 `FAILED`。
  - 切片 chunkIndex 连续，overlap 不导致死循环。
  - embedding 返回数量不匹配时抛业务异常。
  - 文档处理成功时状态为 `DONE`，chunkCount 和知识库 chunk_count 更新。
  - 向量检索结果映射为 `KnowledgeSearchResp`，score 和 metadata 保留。

#### 4. MCP 工具链路

- 必须覆盖：
  - `AgentServiceImpl.validateToolIds`
  - `AgentServiceImpl.replaceTools`
  - `McpClientServiceImpl.callTool`
  - `McpClientServiceImpl.listTools`
  - `McpRawHttpClient.extractJsonPayload`

- 必测场景：
  - Agent 最多绑定 10 个工具。
  - toolIds 去重，`null` 不修改，空列表清空。
  - SDK 调用失败时 fallback 到 raw HTTP。
  - MCP 返回 `isError=true` 时转换为 `BizException`。
  - raw HTTP 能解析 JSON 响应和 SSE `data:` 响应。

#### 5. 工作流执行链路

- 必须覆盖：
  - `WorkflowServiceImpl.validateDefinition`
  - `WorkflowEngine.execute`
  - `WorkflowEngine.findNext`
  - `WorkflowEngine.resolveEndOutput`
  - `NodeConfigParser`
  - 各类 `NodeExecutor`

- 必测场景：
  - START -> LLM -> END 正常执行。
  - CONDITION true / false 分支选择正确。
  - 节点 key 重复、缺少 startNode、边引用不存在节点时拒绝保存。
  - 执行目标节点不存在时抛 `WORKFLOW_CONFIG_INVALID`。
  - 超过 `MAX_STEPS` 时抛 `WORKFLOW_EXECUTE_FAILED`。
  - 节点失败时 workflow run 和 node run 标记 `FAILED`。

#### 6. Agent / Model 配置关键规则

- 必须覆盖：
  - `AgentServiceImpl.create`
  - `AgentServiceImpl.update`
  - `AgentServiceImpl.bindTools`
  - `AgentServiceImpl.toggleEnabled`
  - `ModelConfigServiceImpl`
  - `ProviderServiceImpl`

- 必测场景：
  - Agent 名称唯一性。
  - modelConfig 不存在或禁用时不可创建 / 更新 Agent。
  - 工具绑定替换语义正确。
  - Provider / Model 禁用后不能被对话链路使用。
  - extraParams 默认值只在 request 未显式设置时生效。

### 2. 不写单测、用集成测试替代的代码

以下代码的价值在于验证真实框架、数据库、HTTP 协议或容器行为，单测大量 mock 反而容易制造虚假信心，应使用集成测试或端到端 smoke test。

#### 1. Mapper 与 SQL 行为

- 不写纯单测：
  - MyBatis-Plus `Mapper`
  - XML / 注解 SQL
  - `PageHelper`
  - 逻辑删除、分页、乐观锁、字段类型转换

- 用集成测试替代：
  - 使用测试数据库验证 SQL、索引、分页和逻辑删除。
  - `PgvectorKnowledgeVectorRepository.search` 必须通过 PostgreSQL + pgvector 集成测试验证向量查询、维度过滤、排序和索引命中。

#### 2. 外部 HTTP 与协议兼容

- 不写纯单测：
  - 真实 OpenAI / Claude / Gemini / Ollama 请求。
  - 真实 MCP Server 调用。
  - OkHttp / RestTemplate 本身行为。

- 用集成测试替代：
  - 使用 WireMock、MockWebServer 或本地假 MCP Server 验证 HTTP 状态码、超时、SSE 分片、非标准响应格式。
  - 不允许在自动化测试中调用真实 LLM 供应商，除非明确标记为手动验收或 nightly 外部依赖测试。

#### 3. Spring Web 与 SSE 框架行为

- 不写纯单测：
  - `Controller` 的 Spring 参数绑定。
  - `SseEmitter` 底层连接保持。
  - `GlobalExceptionHandler` 与 Spring MVC 的完整错误响应格式。

- 用集成测试替代：
  - 使用 `@SpringBootTest` / `MockMvc` 覆盖 Controller 参数校验、返回结构和异常映射。
  - SSE 主链路可用 MockMvc 或真实本地端口 smoke test 验证事件格式。

#### 4. 文件系统与文档解析

- 不写纯单测：
  - `Files.copy`
  - `PDDocument.load`
  - PDFBox 对真实 PDF 的解析兼容性。

- 用集成测试替代：
  - 使用临时目录和样例 txt / md / pdf 文件验证上传、保存、解析、失败状态。
  - 文件大小和扩展名校验可单测；真实文件 I/O 必须走集成测试。

#### 5. 缓存、事务、线程池

- 不写纯单测：
  - `@Transactional` 提交 / 回滚行为。
  - `@Cacheable` / `@CacheEvict` 是否真正命中缓存。
  - `ThreadPoolExecutor` 的真实调度、队列满载和拒绝策略。

- 用集成测试替代：
  - 使用 Spring 上下文验证事务、缓存和 afterCommit 行为。
  - 线程池满载、CallerRunsPolicy、SSE 超时属于压力测试或专项集成测试。

### 3. 测试命名规范

所有测试方法统一使用：

```java
should_[期望结果]_when_[输入条件]
```

命名要求：

- 使用英文小写单词和下划线。
- `should` 后写可观察结果，不写实现细节。
- `when` 后写触发条件，不写无意义的 `success`、`normal`、`test`。
- 一个测试方法只验证一个主要行为。

示例：

```java
@Test
void should_markAssistantMessageError_when_llmStreamTimeout() {}

@Test
void should_limitContextMessages_when_maxContextTurnsIsConfigured() {}

@Test
void should_fallbackToRawHttp_when_mcpSdkCallFails() {}

@Test
void should_throwWorkflowConfigInvalid_when_edgeReferencesMissingNode() {}
```

禁止命名：

```java
@Test
void testSendMessage() {}

@Test
void sendMessage_success() {}

@Test
void should_work() {}
```

### 4. 测试结构规范

所有单元测试使用 Given-When-Then 结构。每段之间用空行分隔，必要时用注释标识。

```java
@Test
void should_markAssistantMessageError_when_llmStreamTimeout() {
    // Given
    AgentDetailResp agent = enabledAgent();
    given(agentService.getDetail(1L)).willReturn(agent);
    given(llmCallService.streamChat(anyLong(), any(), any()))
            .willThrow(new LlmApiException(LlmApiException.Type.TIMEOUT, "timeout"));

    // When
    SseEmitter emitter = conversationService.sendMessage(1L, null, "hello");

    // Then
    verify(messageMapper).update(eq(null), argThat(wrapperUpdatesStatus("ERROR")));
    assertThat(emitter).isNotNull();
}
```

结构要求：

- Given：只准备输入、fixture、mock 行为，不执行业务动作。
- When：只调用一个被测方法。
- Then：只做断言和必要的 verify。
- 同一个测试里不要混多个 When。
- 复杂 fixture 用私有 helper 方法构造，helper 名称必须表达业务含义。

### 5. Mock 使用规范

#### 什么时候 mock

- 单测中 mock 当前类的外部协作者：
  - Mapper / Repository。
  - 其他模块的 `api/` Service。
  - LLM / Embedding / MCP / HTTP client。
  - ObjectMapper 以外的重型基础设施。

- 用 mock 隔离以下不稳定因素：
  - 网络。
  - 数据库。
  - 文件系统。
  - 线程调度。
  - 当前时间。
  - 随机 ID。

#### 什么时候不 mock

- 不 mock 被测类自身。
- 不 mock 简单值对象、DTO、PO。
- 不 mock JDK 集合、`BigDecimal`、`LocalDateTime` 等基础类型。
- 不 mock `ObjectMapper` 的普通 JSON 序列化 / 反序列化，除非测试异常分支。
- 不 mock 私有方法；需要通过公开方法观察行为。
- 不为了覆盖率 mock 掉核心业务分支。

#### mock 验证要求

- 只 verify 对业务有意义的交互，例如：
  - 状态更新为 `DONE` / `ERROR`。
  - 调用了 fallback。
  - 没有调用真实工具。
  - 按预期调用 `streamChat` 或 `workflowEngine.execute`。

- 不 verify 无意义细节，例如：
  - getter 调用次数。
  - logger 调用。
  - DTO 字段读取顺序。
  - 与业务结果无关的内部 helper 调用。

### 6. 断言规范

统一使用 AssertJ：

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

断言要求：

- 断言必须验证业务含义，不能只断言非空。
- 集合断言要验证大小、顺序和关键字段。
- 异常断言要验证异常类型、错误码和关键消息。
- JSON / Map 断言要验证关键字段，不比较整段易碎字符串。
- 浮点 score 断言使用 offset / within。

示例：

```java
assertThat(messages)
        .extracting(ChatMessage::getRole)
        .containsExactly("user", "assistant", "user");

assertThat(result.getStatus()).isEqualTo("DONE");
assertThat(result.getChunkCount()).isEqualTo(3);

assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("模型配置");
```

禁止断言：

```java
assertThat(result).isNotNull();
assertTrue(result.size() > 0);
assertEquals("DONE", result.getStatus());
```

### 7. 禁止事项

- 禁止单测访问真实外部网络，包括真实 LLM、真实 MCP Server、真实 embedding 服务。
- 禁止单测依赖本机固定路径、固定端口、固定时区或执行顺序。
- 禁止使用 `Thread.sleep` 等待异步结果；使用同步 executor、Awaitility 或明确回调。
- 禁止为了覆盖率测试 private 方法；通过公开行为覆盖。
- 禁止只验证“方法被调用”而不验证业务结果。
- 禁止写没有断言的测试。
- 禁止吞掉异常或用空 catch 让测试通过。
- 禁止在测试中使用 `System.out.println` 作为验证手段。
- 禁止把多个不相关场景塞进一个测试方法。
- 禁止过度使用 `any()` 导致输入约束没有被验证；关键参数必须用 `eq`、`argThat` 或 captor 检查。
- 禁止让单元测试依赖真实数据库事务、真实 Redis、真实 pgvector、真实文件上传目录。
- 禁止在普通单测中启动完整 Spring 容器；需要 Spring 行为时改写为集成测试并放入对应测试分层。
- 禁止使用 Mockito mock final/static 作为常规设计手段；出现这种需求时优先调整设计边界。
- 禁止用快照式大字符串断言 prompt 全文；应断言 system prompt、参考资料标题、命中 chunk 内容等关键片段。

---
