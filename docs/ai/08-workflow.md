# Hify AI 路由文档

> 本文件由原 `CLAUDE.md` 拆分而来，保留对应章节原文。

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
