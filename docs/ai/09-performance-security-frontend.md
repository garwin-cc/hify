# Hify AI 路由文档

> 本文件由原 `CLAUDE.md` 拆分而来，保留对应章节原文。

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
