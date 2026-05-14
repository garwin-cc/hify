# Hify Knowledge RAG Production Design

## Scope

本期做 `hify-knowledge` 后端生产化闭环，目标是支撑 1000 人本地部署下的文档处理、重建索引、权限隔离、混合检索和可观测性。前端完整任务中心、真实 rerank 模型调用、逐用户文档 ACL 和 PostgreSQL EXPLAIN 集成测试不在本期。

## Current Baseline

现有实现已经包含文档上传、异步处理、取消、失败重试、进度字段、分批 embedding、pgvector 保存、向量维度过滤和 RAG trace。缺口在于任务状态只存在内存队列，重建/重向量化没有统一入口，检索只支持纯向量，chunk metadata 不能结构化过滤，知识库跨项目共享策略不明确。

## Design

### Processing Task

新增 `t_knowledge_task` 持久化处理任务，记录 `task_type`、`target_type`、`target_id`、状态、attempt、阶段、进度、错误和取消标志。文档上传、重试、重向量化、知识库重建都先写任务，再提交 `knowledgeTaskQueue`。队列满时任务和文档都进入可重试失败状态。

启动恢复沿用 `KnowledgeDocumentRecoveryRunner`，新增扫描 `t_knowledge_task` 中 `PENDING/RUNNING` 的未完成任务并重新入队。断点以阶段为边界：任务失败后保留阶段和 `last_processed_chunk_index`，本期重跑时先清理目标文档旧 chunk，再按当前配置重建，避免 MySQL 和 pgvector 跨库半提交导致脏数据。

### Rebuild And Revectorize

新增知识库级重建接口，支持原因：`EMBEDDING_MODEL_CHANGED`、`CHUNK_STRATEGY_CHANGED`、`DOCUMENT_UPDATED`、`MANUAL_REBUILD`。知识库级任务会把知识库下所有未删除文档重置为 `PENDING` 并逐个创建文档处理任务。单文档重向量化复用同一处理链路。

### Metadata Filtering

扩展 `KnowledgeSearchReq`，支持 `department`、`documentType`、`tags`、`createdAtStart/End`、`projectId`、`permissionScope`。文档表补充对应元数据字段；chunk metadata 写入文档名、文件类型、部门、文档类型、标签、项目 ID 和权限范围。检索时在 pgvector SQL 层过滤 metadata，避免先召回敏感 chunk 再丢弃。

### Hybrid Retrieval

`retrievalMode` 支持 `VECTOR` 和 `HYBRID`。`VECTOR` 保持现有语义；`HYBRID` 执行向量检索和关键词检索，按 chunk id 合并并计算：

```text
finalScore = max(vectorScore * 0.7, 0) + max(keywordScore * 0.3, 0)
```

返回结果保留 `vectorScore` 和新增 `keywordScore`。rerank 字段继续保留，但本期不调用 rerank 模型。

### Pgvector Governance

pgvector schema 增加 `search_vector`、metadata GIN、关键词 GIN 和知识库/维度过滤索引。Repository 查询设置可配置 `ivfflat.probes`，所有向量查询保留 `vector_dims(embedding)` 过滤。HNSW 作为可选索引 SQL 注释保留，不默认创建，避免不同 pgvector 版本部署失败。

### Permission And Sharing

知识库增加 `visibility` 和 `share_scope`：默认 `PROJECT`，只允许同项目检索；`WORKSPACE` 可在同空间共享；`PUBLIC` 允许跨项目显式绑定或测试检索。检索请求带 `projectId` 时，在 MySQL 先筛出可访问知识库，再传入 pgvector；无 `projectId` 时保持旧行为，兼容历史调用。

### Observability

文档响应补充任务 ID、任务状态、attempt、队列时间、阶段耗时和进度消息。任务失败记录结构化错误码、失败阶段和 retryable。RAG trace 记录检索模式、过滤条件和命中 chunk。

## Migration

新增 `V36__knowledge_rag_production.sql`：

- 扩展 `t_knowledge_base`：`visibility`、`share_scope`。
- 扩展 `t_knowledge_document`：元数据、处理任务引用、阶段耗时和进度消息。
- 新增 `t_knowledge_task`。

新增 pgvector migration：

- 扩展 `t_knowledge_chunk`：`search_vector`。
- 新增 metadata GIN、search_vector GIN、knowledge base + created_at 索引。

## Testing

单测覆盖：

- 上传/重试/重向量化创建持久化任务并入队。
- 任务失败、取消、队列满时文档和任务状态一致。
- 知识库重建为每个文档创建处理任务。
- 元数据过滤传递到 repository。
- HYBRID 检索合并向量和关键词分数，按 finalScore 排序。
- projectId 存在时只检索允许访问的知识库。
