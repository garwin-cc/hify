# hify-web 管理后台和用户体验设计

## 已有能力复用

- Agent、Workflow、Knowledge、MCP、Provider、User 已有基础列表和配置页，本次不重写基础 CRUD。
- Workflow 编辑器已有画布、节点配置、版本恢复、人审、节点调试、运行事件订阅和 latest run 恢复入口，本次补齐版本差异、发布信息、后端变量面板和运行事件回放。
- Knowledge 文档页已有上传、状态进度、重试、取消、分块查看、检索测试和轮询清理，本次补齐处理队列、重建索引、重向量化、元数据展示和 RAG trace 查看。

## 新增页面

### 应用发布页

- 复用 Agent 发布版本、Agent App、API Key 后端接口。
- 页面按 Agent 选择展示版本、应用、API Key、访问地址、调用示例和回滚操作。
- API Key 只在创建后展示一次，列表只展示 key prefix，避免密钥明文长期留在前端。

### 日志中心

- 统一承载会话日志、Workflow 运行日志、MCP 调用审计、LLM 用量和 RAG trace 查询。
- 会话日志使用游标分页，避免深分页；Workflow/MCP 使用后端分页；RAG trace 通过 traceId 精确查询。
- 页面不制造临时日志数据，后端缺少的审计日志查询只显示“接口待接入”。

### 审计和权限页

- 复用用户管理和身份源配置接口，展示项目角色模型和资源授权边界。
- 当前后端没有项目/空间/成员/统一审计查询 Controller，页面以只读说明加现有账号和身份源管理为主，避免误导成已完整闭环。

### 系统设置页

- 聚合 health/liveness/readiness/deep health、Provider、模型策略、用户、身份源等入口。
- 健康检查独立刷新，避免切换页面时依赖内存状态。

## 状态恢复要求

- 对话页保存 selectedAgentId/currentSessionId，刷新后从服务端重新加载会话和消息，不依赖页面内消息数组。
- Workflow 编辑器保存最新 runId 和 eventSeq，刷新后重新拉取 run detail 并从 eventSeq 继续订阅。
- Knowledge 文档页根据服务端文档状态和任务列表恢复轮询，不依赖上传时的临时状态。
