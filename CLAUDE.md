# Hify 项目开发规范（AI 入口）

本文件是 AI 读取 Hify 仓库时的入口和路由表。详细规范已拆分到 `docs/ai/`，处理具体任务时必须先读取对应路由文件；不要只依赖本文件做实现判断。

## 项目定位

Hify 是一个简化版内部 AI Agent 平台，基于 Dify 思路设计，面向企业内部本地部署使用。

- 当前基线：1 人开发，20-50 人内部使用。
- 规模目标：后续支持约 1000 人团队本地部署。
- 技术栈：Spring Boot + MyBatis-Plus + Vue 3 + Element Plus + MySQL 8.x + Redis + PostgreSQL/pgvector。
- 架构模式：Maven 多模块的模块化单体，后端包名统一在 `com.hify` 下。
- 产品边界：不追求完整复制 Dify/Coze/n8n，优先保证 RAG 可解释、Workflow 可调试、Agent 工具调用可控可审计、部署形态清晰。

## 当前建设阶段

> AI 接到实现任务时，先对照本节确认当前处于哪个 Stage，再查路由表读对应规范文件。

**当前处于 Stage 2（P0 稳定性）**，按顺序补齐稳定闭环：

1. **RAG 任务可靠性**：文档处理状态、失败原因、取消、重试、恢复和脏 chunk 清理必须可解释。
2. **Workflow 运行排障**：失败 run 必须能定位失败节点、输入输出、外部调用和错误原因。
3. **对话运行可观测**：traceId 贯穿 RAG、MCP、LLM、Workflow、SSE 和消息落库。
4. **Agent 会话摘要记忆**：默认关闭，开启后摘要失败不能阻断对话。

Stage 1 已完成：权限漏洞修复、CONDITION 节点增强、TOOL 节点执行器。

完整路线图（7 个 Stage、不做功能列表、工作流节点现状速查）见 [`docs/ai/13-implementation-roadmap.md`](docs/ai/13-implementation-roadmap.md)。

## 模块速览

| 模块 | 职责 |
|------|------|
| `hify-common` | 通用配置、异常、Result、日志、指标、HTTP、审计、限流、Trace、任务队列、缓存基础设施 |
| `hify-auth` | 账号、Session Token、角色鉴权、项目/空间权限、身份源配置 |
| `hify-model` | LLM Provider、ModelConfig、Embedding、Provider Adapter、健康检查 |
| `hify-agent` | Agent 配置、知识库/工作流/MCP 工具绑定 |
| `hify-conversation` | 对话、消息、SSE、RAG 注入、MCP tool calls、workflow 触发 |
| `hify-knowledge` | 知识库、文档上传、异步解析、向量化、pgvector 检索 |
| `hify-workflow` | 工作流定义、执行引擎、节点执行器、人审、模板、版本 |
| `hify-mcp` | MCP Server、MCP Tool、SDK/raw HTTP 调用 |
| `hify-app` | Spring Boot 启动模块、Flyway migration、集成装配 |
| `hify-web` | Vue 3 + Element Plus 前端 |

## 必须遵守的全局规则

1. 跨模块调用只能通过目标模块 `api/` 接口和 DTO，禁止新增对其他模块 `domain/` 或 `infra/` 的直接依赖。
2. `web/` 只处理 HTTP、参数校验和 `Result<T>` 返回；业务逻辑放 `domain/`，外部调用和 Mapper 放 `infra/`。
3. 所有可预期业务错误抛 `BizException(ErrorCode)`，由 `GlobalExceptionHandler` 统一转换响应。
4. 异步任务必须使用显式线程池或 `hify-common` 任务队列，并通过 `TraceContext.wrap` 或任务队列传播 trace。
5. Hify 后端禁止直接执行本机 shell；`CODE_TASK` 只能通过隔离的 MCP Code Worker。
6. Provider API Key、密码、token、clientSecret、MCP authConfig 等敏感字段不得写入日志、审计明文或前端响应。
7. SSE、Workflow run、知识库处理等异步/长连接链路必须考虑断开、超时、队列满、失败状态和 traceId。
8. 数据库 migration 使用 Flyway；新增表遵守通用字段、字符集、逻辑删除和索引规范。
9. 前端新增页面遵循 Vue 3 + Element Plus 既有风格，SSE 和轮询页面必须在组件销毁时清理资源。
10. 变更完成前必须运行与风险匹配的验证命令，并在结果中明确说明。

## 路由表

| 任务类型 | 必读文件 |
|----------|----------|
| 了解项目定位、模块职责、当前实现基线 | [`docs/ai/01-project-overview.md`](docs/ai/01-project-overview.md) |
| 1000 人规模路线图、功能取舍、模块建设清单 | [`docs/ai/02-1000-user-roadmap.md`](docs/ai/02-1000-user-roadmap.md) |
| 后端模块边界、包结构、跨模块调用 | [`docs/ai/03-code-organization.md`](docs/ai/03-code-organization.md) |
| LLM Provider、模型类型、RAG 注入、MCP tool call、熔断重试 | [`docs/ai/04-llm-calls.md`](docs/ai/04-llm-calls.md) |
| Docker Compose、K8s、Nginx/SSE、健康检查、可观测性 | [`docs/ai/05-deployment.md`](docs/ai/05-deployment.md) |
| MySQL、pgvector、索引、分页、大表、RAG 数据一致性 | [`docs/ai/06-database.md`](docs/ai/06-database.md) |
| Java 编码、命名、异常、日志、并发规范 | [`docs/ai/07-coding-style.md`](docs/ai/07-coding-style.md) |
| Workflow 节点、ExecutionContext、Engine、人审、CODE_TASK、版本快照 | [`docs/ai/08-workflow.md`](docs/ai/08-workflow.md) |
| 性能瓶颈、安全护栏、前端实现规范 | [`docs/ai/09-performance-security-frontend.md`](docs/ai/09-performance-security-frontend.md) |
| 核心链路、风险集中区域、系统分析 | [`docs/ai/10-system-analysis.md`](docs/ai/10-system-analysis.md) |
| 单元测试规范、测试优先级、Mock 策略、测试清单 | [`docs/ai/11-testing.md`](docs/ai/11-testing.md) |
| P0 后续建设重点和执行建议 | [`docs/ai/12-p0-backlog.md`](docs/ai/12-p0-backlog.md) |
| 分阶段实现路径、不做功能列表、工作流节点现状速查 | [`docs/ai/13-implementation-roadmap.md`](docs/ai/13-implementation-roadmap.md) |

## 常用验证命令

按变更范围选择，不要求每次全部运行：

```bash
mvn -pl hify-common test
mvn -pl hify-auth -am test
mvn -pl hify-app -am -DskipTests compile
npm run build --prefix hify-web
```

## 维护要求

- 新增或修改规范时，优先更新对应 `docs/ai/*.md` 文件。
- `CLAUDE.md` 和 `AGENTS.md` 只保留入口、全局硬规则和路由表。
- 如果新增新的大型规范章节，必须新增路由文件，并在本文件路由表中登记。
- `AGENTS.md` 应与 `CLAUDE.md` 保持一致，避免不同 AI 入口读取到冲突规范。
