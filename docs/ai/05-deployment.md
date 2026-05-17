# Hify AI 路由文档

> 本文件由原 `CLAUDE.md` 拆分而来，保留对应章节原文。

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
- 1000 人规模生产部署以 K8s 为主，Docker Compose 仅保留单机交付、演示和验收能力；详细容量和运维手册见 `docs/ops/deployment-operations.md`。

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

**Backend 容器规格**：

- 小团队基线：requests 512Mi/250m，limits 1Gi/1000m，replicas=2
- 1000 人生产基线：requests 1Gi/500m，limits 2Gi/2CPU，HPA 2-8

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
- K8s liveness 使用 `/api/v1/health/liveness`，readiness 使用 `/api/v1/health/readiness`，deep health 只供人工排障。
- readiness 需要纳入后台任务队列饱和状态；deep health 需要展示 SSE 活跃连接、日志归档状态、任务队列细节和 Provider 汇总。
- `/actuator/prometheus` 暴露 Micrometer 指标，指标统一使用 `hify_` 前缀。
- Grafana Dashboard 至少覆盖请求量、错误率、SSE 连接数、LLM token、RAG 延迟、MCP 调用、Workflow run、Hikari 连接池。
- JSON 日志输出到 stdout，由 K8s 日志采集系统收集。
- 同一请求链路必须共享 traceId；对话、LLM、MCP、工作流异常都必须带 traceId。
- 运行日志和审计日志分开治理：运行日志走容器日志系统，审计日志落 `t_audit_log`；保留周期、归档和敏感字段脱敏策略见运维手册。
- 1000 人本地部署必须配置 `HIFY_CONVERSATION_SSE_MAX_ACTIVE_CONNECTIONS`、归档批大小和日志保留周期，避免长连接和维护 Job 无上限增长。

---
