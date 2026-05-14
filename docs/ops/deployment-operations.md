# Hify 部署和运维指南

本文面向 1000 人团队本地部署。Docker Compose 保留单机交付能力；生产长期运行推荐 K8s + 独立数据库/缓存 + Prometheus/Grafana。

## 部署形态

### Docker Compose

适用范围：演示、验收、小团队单机交付、离线安装验证。

建议配置：

- 4 核 16GB 起步，磁盘 SSD 200GB 起步。
- 后端、前端、MySQL、Redis、PostgreSQL/pgvector 同机运行。
- 默认不启用 Prometheus/Grafana；需要观测时使用 `--profile observability`。
- 不建议承载 1000 人长期生产流量，瓶颈会集中在数据库 I/O、LLM 长连接线程和 pgvector 检索。

启动：

```bash
docker compose up -d --build
docker compose --profile observability up -d prometheus grafana
```

### K8s 生产

适用范围：1000 人团队内网生产、长期运行、需要滚动发布和弹性扩容。

推荐基线：

- `hify-backend`：2 副本起步，HPA 2-8，单 Pod requests `500m/1Gi`，limits `2/2Gi`。
- `hify-frontend`：2 副本起步，HPA 2-4，单 Pod requests `50m/64Mi`。
- MySQL、Redis、PostgreSQL 不建议与业务 Pod 混部；优先使用托管服务或独立 StatefulSet。
- Ingress 必须关闭 SSE 缓冲，并设置至少 300s read/send timeout。

## K8s 发布策略

- 后端滚动发布使用 `maxUnavailable: 0`，确保 SSE 和 Workflow 运行期间至少保留旧副本。
- 后端设置 `preStop` 和 `terminationGracePeriodSeconds: 75`，给长连接和异步任务留出退出窗口。
- liveness 只检查进程存活，readiness 检查 MySQL、Redis、pgvector；deep health 供人工排障，不放进探针。
- HPA 使用 CPU 和内存双指标，LLM 延迟通常不是 CPU 问题，扩容只能缓解请求堆积，不能替代 Provider 熔断和 fallback。

## 数据库和缓存

### MySQL

连接池：

- 后端 Hikari `maximumPoolSize=20` 是单 Pod 默认值。
- 生产容量估算：`backendReplicas * maximumPoolSize + 管理连接 + 迁移连接` 不应超过 MySQL `max_connections` 的 70%。
- 1000 人基线：2-4 个后端 Pod，每 Pod 20 连接，MySQL `max_connections` 建议 200 起。

备份恢复：

- 每日全量备份，保留 7-14 天。
- 每 15-30 分钟增量或 binlog 归档，确保 RPO 可控。
- 每月至少做一次恢复演练，验证 Flyway 迁移、默认管理员和业务表一致性。

慢查询治理：

- 开启 slow query log，阈值建议 500ms 起步，生产稳定后可降到 200ms。
- 每周检查 `performance_schema.events_statements_summary_by_digest`。
- 对话、日志、审计、Workflow run 列表禁止深分页；超过 1000 offset 的接口必须改游标分页。

### Redis

用途：Session、缓存、限流、队列状态。

- 1000 人生产建议 Redis 独立实例，开启 AOF `everysec`。
- maxmemory 根据缓存和限流规模设置，基线 2-4GB。
- eviction 策略建议 `allkeys-lru` 或按业务拆库后使用 `volatile-lru`。
- 恢复时先恢复 Redis，再启动后端，避免 Session/限流状态瞬间丢失造成流量冲击。

### PostgreSQL + pgvector

用途：知识库 chunk embedding 和向量检索。

- 向量表按知识库、模型维度和 deleted 过滤，避免跨模型维度扫描。
- ivfflat `lists` 按 `sqrt(row_count)` 估算，小于 10 万行使用 100；查询 probes 默认 10，召回不足时逐步升高。
- 大规模知识库重建索引应在低峰执行，重建前记录 embedding 模型、chunk 策略和文档版本。
- 备份 PostgreSQL 数据和 MySQL 元数据必须使用同一时间窗口，避免文档状态和向量块不一致。

## 可观测性

Prometheus 抓取：

- Spring Actuator：`/actuator/prometheus`
- Backend Service：`hify-backend:8080`
- 抓取间隔：15s

Grafana 面板覆盖：

- 请求量和错误率：`http_server_requests_seconds_count`
- SSE 连接数：`hify_sse_active_connections`，当前为目标指标，接入前面板会显示无数据
- LLM token 和调用：`hify_llm_calls`、`hify_llm_tokens_total`
- RAG 延迟：`hify_rag_retrieval_latency_seconds`
- MCP 调用：`hify_mcp_tool_calls`
- Workflow run：`hify_workflow_runs`
- JVM、Tomcat、Hikari：Spring Boot/Micrometer 默认指标

## 日志和审计

运行日志：

- 输出到 stdout，交由容器日志系统采集。
- 用于排障、错误率分析和 traceId 串联。
- 默认保留 90 天，由 `hify.jobs.runtime-log-retention-days` 控制；超过周期归档到对象存储或日志平台冷存储。

审计日志：

- 落 MySQL `t_audit_log`，与运行日志分开管理。
- 用于追踪权限变更、Provider 修改、MCP 修改、Workflow 发布、版本恢复等高风险操作。
- 默认保留 180 天，由 `hify.jobs.audit-log-retention-days` 控制；生产合规要求更长时，使用归档表或离线仓库。

敏感字段：

- Provider API Key、clientSecret、MCP authConfig、API Key、密码、token 不允许明文进入日志或前端响应。
- 审计 before/after 快照必须脱敏，当前 `DefaultAuditLogService` 已对敏感 key 写入 `******`。
- 归档任务只移动脱敏后的审计记录，不导出运行时 secret。

## 容量规划基线

| 规模 | 后端 Pod | MySQL | Redis | pgvector | 说明 |
|------|----------|-------|-------|----------|------|
| 50 人 | 1-2 | 2C/4GB | 1GB | 2C/4GB | Compose 或小型 K8s |
| 300 人 | 2-3 | 4C/8GB | 2GB | 4C/8GB | 独立 DB，启用观测 |
| 1000 人 | 3-6 | 8C/16GB | 4GB | 8C/16GB | K8s + HPA + 独立存储 |

LLM Provider 延迟和限流通常是吞吐瓶颈。扩容 Hify Pod 只能提高排队和连接承载能力，模型调用必须依赖熔断、重试、fallback、限流和 Provider 健康检查共同保护。
