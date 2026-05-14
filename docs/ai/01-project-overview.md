# Hify AI 路由文档

> 本文件由原 `CLAUDE.md` 拆分而来，保留对应章节原文。

## 项目概览

Hify 是一个简化版内部 AI Agent 平台，基于 Dify 思路设计。

- **当前基线**：1 人开发，20-50 人内部使用，本地部署
- **规模演进目标**：后续支持约 1000 人团队本地部署使用，从“轻量内部工具”升级为“内部生产级 AI 应用平台”
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
