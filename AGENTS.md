# Hify 项目开发规范

## 项目概览

Hify 是一个简化版内部 AI Agent 平台，基于 Dify 思路设计。

- **团队规模**：1 人开发，20-50 人内部使用，本地部署
- **技术栈**：Spring Boot + MyBatis-Plus + Vue + MySQL 8.x + Redis + pgvector
- **架构模式**：Maven 多模块的模块化单体（Modular Monolith），代码边界清晰，可平滑拆分为微服务

---

## 核心功能模块（MVP 范围）

| 模块 | 说明 |
|------|------|
| 模型管理 (model) | 管理 OpenAI / Claude / Gemini / Ollama / DeepSeek / 阿里百炼等 OpenAI-compatible Provider，支持模型类型（聊天/向量）和连通性测试 |
| Agent 配置 (agent) | 配置 Agent 名称、系统提示词、绑定模型、关联知识库和 MCP 工具 |
| 对话引擎 (conversation) | 多轮对话、历史记录、SSE 流式响应 |
| 知识库 RAG (knowledge) | 文档上传 → 异步向量化 → pgvector 余弦搜索 → 注入 LLM 上下文 |
| 简版工作流 (workflow) | 可视化编排：开始、LLM、条件、知识库、API 调用、人工审核、CODE_TASK、结束，支持异步运行、WAITING/RESUME、节点调试、版本快照 |
| MCP 工具接入 (mcp) | 接入外部 MCP 工具，供 Agent 和工作流调用 |

**砍掉或延后的功能**：多租户、自定义插件市场、实时协作、企业 SSO、精细化权限控制、复杂 n8n 式通用自动化、内置本机 shell 执行器。

---

## 当前实现基线

### 已落地能力

- 模型管理：
  - Provider 新增、编辑、测试、模型同步。
  - 支持手动新增模型，并用模型类型区分聊天模型和向量模型。
  - 阿里百炼等兼容 OpenAI API 的供应商通过 Provider Adapter 接入，注意 Base URL 不要重复拼接 `/v1`。

- Agent：
  - Agent 可绑定模型、知识库、工作流和 MCP 工具。
  - `workflowId` 不为空时，对话链路优先触发工作流，直接 LLM/RAG/MCP 对话逻辑不再执行。
  - 工具绑定通过 `agent_tool` 多对多关系，全量替换，最多 10 个工具。

- 对话：
  - SSE 流式响应。
  - 普通 Agent 对话支持 RAG 注入和 MCP tool calls。
  - 事件流恢复用于工作流运行事件，SSE 断开后可按 event sequence 继续订阅。

- 知识库：
  - 知识库 CRUD。
  - 文档上传后异步处理，状态流转为 `PENDING -> PROCESSING -> DONE / FAILED`。
  - 文档解析支持 txt / md / pdf，扫描 PDF 一期不支持。
  - 分块、向量化、pgvector 存储和相似度搜索已接入。
  - 知识库可配置 embedding model，默认走知识库配置的向量模型。

- 工作流：
  - 支持可视化编辑、保存、编辑、删除。
  - 支持节点：`START`、`LLM`、`CONDITION`、`KNOWLEDGE`、`API_CALL`、`HUMAN_REVIEW`、`CODE_TASK`、`END`。
  - 支持异步运行、运行事件流、运行恢复、节点运行记录。
  - `HUMAN_REVIEW` 节点进入 `WAITING` 后暂停，审批通过后 resume，拒绝后 run 进入 `CANCELED`。
  - `CODE_TASK` 只通过 MCP Code Worker 执行，不允许 Hify 后端直接执行本机 shell。
  - 支持节点调试接口和前端调试面板。
  - 支持工作流版本快照和版本恢复，运行记录保存 `workflow_version_id`。
  - 支持轻量 Workflow Template，用于从模板创建工作流。

- MCP：
  - MCP Server CRUD、连通性测试、工具列表同步。
  - Agent 可绑定多个 MCP 工具。
  - 对话链路支持模型原生 `tool_calls` 和部分兼容供应商的 inline tool call 格式。
  - MCP SDK 调用失败时可 fallback 到 raw HTTP。

- 交付和可观测性：
  - 后端/前端 Dockerfile、docker-compose、K8s Deployment/Service、Secret/ConfigMap 模板。
  - JSON 结构化日志，日志包含 traceId。
  - Actuator + Micrometer Prometheus 指标，统一 `hify_` 前缀。
  - `/api/v1/health` 检查 MySQL、Redis、pgvector。

### 当前产品定位

Hify 的定位不是全量复制 Dify、Coze、n8n，而是面向企业内部团队的轻量 AI 应用平台：

1. RAG 可解释。
2. Workflow 可调试、可暂停、可恢复。
3. Agent 工具调用可控、可审计。
4. 交付形态清晰，可在本地、Docker Compose、K8s 环境部署。

---

## 代码组织规范

### Maven 模块结构

当前仓库按 Maven 多模块组织：

```text
hify-common/         # 通用配置、异常、Result、日志、指标、HTTP 基础设施
hify-model/          # LLM Provider、ModelConfig、Embedding、Provider Adapter
hify-agent/          # Agent 配置、知识库/工作流/MCP 工具绑定
hify-conversation/   # 对话、消息、SSE、RAG 注入、MCP tool calls、workflow 触发
hify-knowledge/      # 知识库、文档上传、异步解析、向量化、pgvector 检索
hify-workflow/       # 工作流定义、执行引擎、节点执行器、人审、模板、版本
hify-mcp/            # MCP Server、MCP Tool、SDK/raw HTTP 调用
hify-app/            # Spring Boot 启动模块、Flyway migration、集成装配
hify-web/            # Vue 3 + Element Plus 前端
```

后端 Java 包仍统一在 `com.hify` 下，各业务模块分别放在对应 Maven module 中。

每个模块内部四层结构：

```text
hify-{module}/src/main/java/com/hify/{module}/
├── api/       # 对外暴露的接口（interface），供其他模块调用
├── domain/    # 业务逻辑：Service 实现、领域对象、Factory、Repository 接口
├── infra/     # 基础设施：Mapper、RepositoryImpl、外部 API 客户端、config
└── web/       # Controller，只处理 HTTP 层
```

### 各层职责边界

| 层 | 职责 | 禁止 |
|----|------|------|
| web/ | 接收请求、参数校验（@Valid）、调用本模块 api/ 接口、返回 Result<T> | 直接调用其他模块 domain/、直接操作数据库 |
| api/ | 定义跨模块调用的 interface 和 DTO | 包含业务逻辑实现 |
| domain/ | 业务逻辑、领域对象、事务边界（@Transactional） | 依赖 web 层、把 HTTP 参数直接向下传递 |
| infra/ | Mapper、RepositoryImpl（PO ↔ 领域对象转换）、外部调用 | 包含业务逻辑 |

### 跨模块调用规则

- **只能**通过目标模块的 `api/` 接口调用，禁止直接 import 其他模块的 `domain/` 或 `infra/` 类
- 跨模块传递使用 `api/` 包下定义的 DTO，不传递 PO 或领域对象
- 循环依赖视为架构错误，立即重构
- 历史代码中如果已有跨模块 `domain/infra` 依赖，新增改动不得扩大依赖范围；重构时优先补 `api` 接口再迁移调用方。

```java
// 正确：agent 模块通过 ModelService（api/ 接口）调用 model 模块
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {
    private final ModelService modelService; // 来自 model 模块的 api/ 接口
}
```

---

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

## 部署架构

### 支持的部署形态

- 本地开发：
  - 后端用 Maven 启动，前端用 Vite dev server。
  - MySQL、Redis、PostgreSQL + pgvector 可用本机服务或 Docker Compose。

- Docker Compose：
  - 适合单机交付、演示和小团队内部使用。
  - 包含 frontend、backend、MySQL、Redis、pgvector。
  - 敏感配置从 `.env` 读取，不写死在 compose 文件里。

- K8s：
  - 适合企业内部长期运行。
  - backend 使用 ClusterIP，frontend 可用 NodePort 或 Ingress 暴露。
  - 配置走 ConfigMap，密码和 API Key 走 Secret。
  - Prometheus 抓取 `/actuator/prometheus`，Grafana 导入 Hify Dashboard。

### K8s 目标架构

用户浏览器
    │
    ▼
Ingress Nginx（L7 负载均衡 + SSL 终止 + SSE 支持）
    │
    ├──▶ hify-frontend（Vue SPA，Nginx 静态文件服务，2 副本）
    │
    └──▶ hify-backend（Spring Boot，2 副本）
              │
              ├──▶ MySQL 8.x（主数据存储）
              ├──▶ Redis（Session / 缓存 / 限流）
              └──▶ PostgreSQL + pgvector（向量存储）

**Ingress 关键配置（SSE 必须）**：

```yaml
nginx.ingress.kubernetes.io/proxy-read-timeout: "300"
nginx.ingress.kubernetes.io/proxy-buffering: "off"
nginx.ingress.kubernetes.io/limit-rps: "20"
```

**Backend 容器规格**：requests 512Mi/250m，limits 1Gi/1000m，replicas=2

**JVM 启动参数**：

```dockerfile
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseG1GC",
            "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
```

### Nginx / 前端代理要求

前端 Nginx 代理 `/api/` 到后端时必须适配 SSE：

```nginx
location /api/ {
    proxy_pass http://backend:8080;
    proxy_read_timeout 120s;
    proxy_buffering off;
    proxy_cache off;
    proxy_set_header Connection "";
}
```

如果使用 Ingress，也必须关闭 proxy buffering，否则 SSE 事件会被缓冲，前端无法实时收到 token 或工作流事件。

### 健康检查和可观测性

- `/api/v1/health` 必须检查 MySQL、Redis、pgvector，所有依赖 UP 才返回整体 UP。
- `/actuator/prometheus` 暴露 Micrometer 指标，指标统一使用 `hify_` 前缀。
- JSON 日志输出到 stdout，由 K8s 日志采集系统收集。
- 同一请求链路必须共享 traceId；对话、LLM、MCP、工作流异常都必须带 traceId。

---

## 数据库规范

### MySQL 通用字段约定

每张表必须包含以下字段：

```sql
id          BIGINT          NOT NULL AUTO_INCREMENT,  -- 主键，禁用 UUID
created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
deleted     TINYINT(1)      NOT NULL DEFAULT 0,       -- 逻辑删除标志
PRIMARY KEY (id)
```

- 字符集：`utf8mb4`，排序规则：`utf8mb4_unicode_ci`
- 禁用 `VARCHAR` 无长度约束，text 类 content 字段用 `MEDIUMTEXT`
- 金额用 `DECIMAL(19,4)`，禁用 `FLOAT/DOUBLE`
- 布尔用 `TINYINT(1)`，不用 `BIT`

### 索引设计原则

1. **区分度低的字段不单独建索引**（如 deleted、status 枚举），必须与高区分度字段组合
2. **组合索引遵循最左前缀**：等值查询字段在左，范围查询字段在右
3. **查询条件中含 `deleted`**，必须将 `deleted` 纳入索引
4. **每表索引不超过 5 个**（含主键），写多读少的表控制在 3 个以内
5. **禁止在 `TEXT/BLOB` 类型字段上建普通索引**，需要时建前缀索引或全文索引

```sql
-- 正确示例：conversation_id 高区分度在左，deleted 次之，created_at 范围在右
INDEX idx_conv_created (conversation_id, deleted, created_at)
```

### 大表处理策略

判断为大表的阈值：行数 > 500 万 或 数据量 > 2GB

| 场景 | 策略 |
|------|------|
| t_message | 按 conversation_id 分区，或按月归档冷数据 |
| 知识库向量表 | ivfflat 索引，lists = sqrt(行数) |
| 日志类表 | 只保留 90 天，定期 DELETE + OPTIMIZE TABLE |

### 分页查询规范

- **禁止** `LIMIT offset, size` 深分页（offset > 1000 全表扫描）
- 对话记录类使用**游标分页**：

```sql
SELECT id, role, content, created_at FROM t_message
WHERE conversation_id = ?
  AND deleted = 0
  AND (created_at < ? OR (created_at = ? AND id < ?))
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

- 管理后台必须分页时，用 `WHERE id > lastId LIMIT size` 替代 offset

### pgvector 索引规范

```sql
-- 余弦相似度索引，lists 值 = sqrt(总行数)，行数 <10 万时 lists=100
CREATE INDEX idx_document_chunk_embedding_ivfflat ON document_chunk
USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 查询时设置 probes，精度和速度平衡
SET ivfflat.probes = 10;
SELECT * FROM document_chunk
ORDER BY embedding <=> '[...]'::vector LIMIT 5;
```

### RAG 数据一致性规范

- 知识库元数据、文档元数据放 MySQL；chunk embedding 放 PostgreSQL + pgvector。
- 上传接口只能负责接收文件、写入 `document` 记录、提交异步任务，不能同步等待解析和向量化。
- 文档状态由后端驱动前端轮询：
  - `PENDING`
  - `PROCESSING`
  - `DONE`
  - `FAILED`
- 删除知识库时，关联 document 和 document_chunk 必须一起逻辑删除。
- 删除文档时，MySQL 文档记录和 pgvector chunk 都必须逻辑删除。
- 跨 MySQL 和 PostgreSQL 不做分布式事务；失败时必须通过状态字段和错误信息暴露给前端，并支持后续重试或重建索引。
- embedding model 变更后，历史 chunk 维度可能不一致，检索时必须过滤向量维度或按模型分组，避免维度错误。

### 索引检测措施

**开发阶段**：启用 p6spy，拦截执行 >10ms 的查询自动 EXPLAIN，type=ALL 时打印警告日志。

**CI 阶段**：关键查询写 `IndexCoverageTest`，EXPLAIN 结果中 type=ALL 则测试失败，阻断合并。

**生产阶段**：定期查询 `performance_schema.events_statements_summary_by_digest`，找出 `sum_no_index_used > 0` 的 SQL。

```sql
SELECT digest_text, count_star AS 执行次数, sum_no_index_used AS 未用索引次数
FROM performance_schema.events_statements_summary_by_digest
WHERE sum_no_index_used > 0
ORDER BY sum_no_index_used DESC LIMIT 20;
```

---

## 编码规范（基于阿里巴巴 Java 开发手册）

### 命名

1. **类名用 UpperCamelCase**，方法名、变量名用 lowerCamelCase，常量用 UPPER_SNAKE_CASE，包名全小写无下划线。
2. **禁止用拼音或拼音缩写**命名，禁止单字母变量（循环变量 `i/j/k` 除外）。
3. **方法名体现动词**：查询用 `get/list/query`，修改用 `update`，删除用 `delete/remove`，新增用 `create/add`，布尔返回值用 `is/has/can`。
4. **Service 接口不加 I 前缀**，实现类加 `Impl` 后缀（`AgentService` + `AgentServiceImpl`）。
5. **数据库表名用 `t_` 前缀**，列名用 snake_case；PO 类用 `Po` 后缀，DTO 用 `Dto`/`Request`/`Response`，Mapper 用 `Mapper` 后缀。

### 异常处理

6. **禁止 catch 后 `e.printStackTrace()` 或空 catch**，必须记录日志或向上抛出。
7. **业务异常统一抛 `BizException(ErrorCode)`**，不用 RuntimeException 传递业务语义。
8. **只在顶层（GlobalExceptionHandler）处理并转换为 HTTP 响应**，中间层不捕获再包装。
9. **finally 块不写 return**，不在 finally 中抛出新异常（会吞掉原始异常）。
10. **NPE 防御**：方法返回值优先返回空集合（`Collections.emptyList()`）而非 null，接口入参用 `@NonNull`/`@Valid` 注解声明约束。

### 日志

11. **使用 SLF4J 接口 + Logback 实现**，类中用 `@Slf4j`（Lombok），禁止用 `System.out.println`。
12. **禁止在循环体内打日志**，高频路径只在异常分支记录。
13. **占位符格式 `log.info("xxx {}", var)`**，禁止字符串拼接（避免无效 toString 开销）。
14. **日志分级约定**：DEBUG=详细调试，INFO=关键业务节点，WARN=可恢复异常或配置缺失，ERROR=需人工介入的故障。生产环境 INFO 级别，日志文件按天滚动，保留 30 天。
15. **LLM 调用必须记录**：provider、model、耗时、token 数、是否命中缓存，便于成本分析。

### 并发

16. **线程池必须显式创建**（`ThreadPoolExecutor`），禁止用 `Executors.newFixedThreadPool`（无界队列 OOM）。
17. **ThreadLocal 用完必须 `remove()`**，防止线程池场景下数据泄漏。
18. **加锁粒度最小化**：只锁共享变量操作，不锁 I/O 和 LLM 调用；优先用 `ReentrantLock` 替代 `synchronized`（可设超时）。
19. **单例 Bean 的成员变量必须是线程安全的**：无状态 Service 天然安全；有状态则用 `ThreadLocal` 或局部变量，禁止用实例变量存请求上下文。
20. **`CompletableFuture` 异步调用必须指定线程池**（`supplyAsync(task, llmExecutor)`），禁止用默认 `ForkJoinPool.commonPool()`（会影响其他异步任务）。

---

## 工作流实现规范

### 工作流数据结构

- 工作流定义拆分为三类数据：
  - `t_workflow`：基本信息、启用状态、开始节点。
  - `t_workflow_node`：节点定义，`config` 用 JSON 存储不同节点类型配置。
  - `t_workflow_edge`：节点连接关系，条件分支通过 `condition_expression` 区分。
- 工作流运行拆分为：
  - `t_workflow_run`：整次运行状态、输入、输出、错误、当前节点、上下文快照、版本 ID。
  - `t_workflow_node_run`：单个节点运行状态、输出快照、错误和耗时。
  - `t_workflow_run_event`：运行事件流，前端按 event sequence 恢复订阅。
  - `t_workflow_review_task`：人工审核任务。
  - `t_workflow_version`：保存 workflow + nodes + edges 的完整版本快照。

### NodeConfig 类型安全

- 工作流节点配置必须通过 `NodeConfigParser` 解析。
- 定义层配置实现 `domain.config.NodeConfig` sealed interface。
- 执行层配置实现 `engine.NodeConfigDef`。
- 新增节点类型必须同时修改：
  - `WorkflowNodeType`
  - `NodeConfig`
  - `NodeConfigParser.parse`
  - `NodeConfigParser.parseExecutionConfig`
  - 对应 `NodeExecutor`
  - 前端 `NodeType`、默认配置、表单、校验和节点展示
  - 单元测试 `NodeConfigParserTest`

### ExecutionContext

- `ExecutionContext` 内部使用 `LinkedHashMap<String, Object>`，保持变量写入顺序。
- 构造时必须写入 `start.userMessage`。
- 所有节点输出统一写入 `nodeKey.outputVariable`。
- 模板变量格式为 `{{nodeKey.varName}}`；变量不存在时保留原占位符，不抛异常。
- 节点执行记录中的 outputs 存 `ctx.snapshot()`，用于调试和恢复。

### WorkflowEngine

- `WorkflowEngine` 是同步执行引擎，不创建新线程。
- 异步运行由 `WorkflowServiceImpl` 提交到已有 `llmExecutor`。
- 执行循环必须有保护：
  - 找不到 START 节点时失败。
  - 找不到目标节点时失败。
  - 执行步数超过 `MAX_STEPS` 时失败，防止配置错误导致死循环。
  - 节点执行失败时，当前 node run 标记 `FAILED`，workflow run 标记 `FAILED` 或 `TIMEOUT`。
- `HUMAN_REVIEW` 节点必须暂停运行：
  - node run = `WAITING`
  - workflow run = `WAITING`
  - 保存 `context_snapshot`
  - 创建 review task
  - 发布 `REVIEW_WAITING` 事件
- 审批通过后从当前审核节点的下一条边继续执行；审批拒绝后 run 进入 `CANCELED`。

### CODE_TASK 节点

- `CODE_TASK` 用于把代码实现类任务交给外部 Code Worker。
- 一期只允许 `executor = MCP`，通过 `McpClientService.callTool` 调用外部 MCP 工具。
- 禁止在 Hify 后端直接执行本机 shell、脚本或任意命令。
- CODE_TASK 配置至少包含：
  - `task`
  - `executor`
  - `mcpServerId`
  - `toolName`
  - `timeoutSeconds`
  - `outputVariable`
- MCP Code Worker 返回建议包含：
  - `status`
  - `summary`
  - `changedFiles`
  - `diff`
  - `logs`
  - `error`
- CODE_TASK 后如涉及代码变更合入、发布或外部副作用，工作流中必须接 `HUMAN_REVIEW` 节点。

### 工作流版本快照

- 创建工作流时生成 v1。
- 每次保存工作流时写入完整版本快照。
- 执行 workflow run 时记录当前 `workflow_version_id`。
- 恢复版本时从快照重建 workflow、nodes、edges，并再次生成新版本。
- 不做 diff 更新，更新工作流时仍采用先删除旧 nodes/edges 再批量插入新定义的策略。

### 前端工作流编辑器

- 节点必须使用框图展示，不使用纯 JSON 编辑作为主入口。
- 右侧配置面板必须展示当前节点配置、最近一次 node run、调试结果。
- 运行进入 `WAITING` 时必须显示审批面板，不能只展示 run 状态。
- 节点调试只调试当前节点，不写正式 workflow run。
- 版本快照入口应在编辑页可见，支持恢复版本。

---

## 性能瓶颈优先级（一期处理清单）

| 级别 | 瓶颈 | 一期处理方式 |
|------|------|-------------|
| P0 | LLM API 延迟高（3-30s） | 线程隔离 + 熔断 + Fallback（已设计） |
| P0 | 向量检索无索引全表扫描 | 建 ivfflat 索引（建表时必须创建） |
| P1 | 对话消息深分页 | 游标分页（禁止 LIMIT offset） |
| P1 | N+1 查询 | MyBatis-Plus 批量查询，禁止循环单查 |
| P2 | 连接池耗尽 | HikariCP 配置：maximumPoolSize=20，connectionTimeout=3000ms |
| 延后 | 静态资源未压缩 | Nginx gzip，流量大时处理 |
| 延后 | JVM GC 停顿 | G1GC 已启用，暂不调优 |

---

## 安全边界和护栏

### MCP 和工具调用安全

- MCP Server endpoint 属于高风险配置，后续生产化必须考虑内网白名单或 SSRF 防护。
- Agent 绑定工具必须校验 toolId 存在且 MCP Server 启用。
- 一个 Agent 最多绑定 10 个工具，避免 tools 参数过长影响 LLM 效果。
- 工具调用参数由 LLM 生成，后端必须记录 arguments keys 和调用结果，便于审计。
- 工具执行失败时返回受控错误信息，不能把敏感异常栈直接暴露给用户。

### 代码执行安全

- Hify 后端不内置本机 Code Node，不直接执行 shell。
- 所有代码实现类任务必须通过隔离的 MCP Code Worker。
- Code Worker 应在独立进程、容器或沙箱中运行，限制工作目录、网络、Secret 和执行时间。
- CODE_TASK 输出 diff、changedFiles、logs 供 HUMAN_REVIEW 审批，不应默认自动合并或发布。

### RAG Prompt Injection 防护

- 知识库内容不可信，RAG 注入时必须明确“参考资料”边界。
- 不允许知识库 chunk 覆盖系统指令、工具权限或输出安全策略。
- 对外部文档进入知识库的场景，应在后续增加内容扫描和 chunk 禁用能力。

### Agent 和工作流权限

- 当前不做复杂 RBAC，但新增管理接口时应默认只有 Admin 可操作。
- 工作流发布、版本恢复、MCP Server 修改、Provider API Key 修改应记录审计日志。
- Agent 绑定工作流后，对话链路会执行工作流；工作流中如有 MCP 或 CODE_TASK，需要额外审核配置。

---

## 前端实现规范

- 前端使用 Vue 3 + Element Plus。
- 管理页表格统一使用 `el-table`，弹窗使用 `el-dialog`，表单校验使用 `el-form rules`。
- 新增/编辑弹窗必须支持回填、校验、提交后刷新列表。
- 列表空状态必须给提示文案，不留空白页面。
- SSE 页面必须在组件销毁时关闭 EventSource 或清理 polling timer。
- 文档处理、工作流运行这类异步状态必须以状态字段驱动 UI，不依赖固定等待时间。
- 工作流编辑器是产品核心页面，新增节点类型必须同时补：
  - 节点面板入口。
  - 画布节点样式。
  - 右侧配置表单。
  - 前端校验。
  - JSON normalization。
  - 变量引用插入逻辑。

---

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
