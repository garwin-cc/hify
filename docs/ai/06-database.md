# Hify AI 路由文档

> 本文件由原 `CLAUDE.md` 拆分而来，保留对应章节原文。

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
