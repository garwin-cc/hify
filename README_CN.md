# Hify

Hify 是一个面向内部使用的轻量级 AI Agent 平台，参考 Dify 的核心思路实现，适合 20-50 人团队本地部署和二次开发。

项目采用模块化单体架构，后端使用 Spring Boot + MyBatis-Plus，前端使用 Vue 3 + TypeScript + Vite，存储层包含 MySQL、Redis 和 PostgreSQL + pgvector。

English documentation: [README.md](README.md)

## 功能模块

| 模块 | 说明 |
|---|---|
| 账号体系 | 用户登录、Session Token、ADMIN / EDITOR / VIEWER 角色和接口鉴权 |
| 模型管理 | 管理 OpenAI、Anthropic、DeepSeek、Ollama、OpenAI Compatible 等 Provider，以及 CHAT / EMBEDDING / RERANK 模型配置 |
| Agent 配置 | 配置 Agent 名称、系统提示词、绑定模型、知识库和 MCP 工具 |
| 对话引擎 | 多轮对话、历史消息、SSE 流式响应、Function Calling |
| 知识库 RAG | 知识库、文档、切片、混合检索、可选 Rerank、元数据过滤和上下文注入 |
| MCP 工具接入 | 管理 MCP Server 和工具，供 Agent 对话调用 |
| 简版工作流 | 可视化编排，支持 START、LLM、CONDITION、KNOWLEDGE、API_CALL、HUMAN_REVIEW、CODE_TASK、END 节点 |
| 模板库 | 从模板创建工作流、从已有工作流沉淀模板、模板版本快照和导出 |

## 技术栈

后端：

- Java 17
- Spring Boot 3.2
- MyBatis-Plus
- MySQL 8
- Redis 7
- PostgreSQL 16 + pgvector
- Flyway
- OkHttp

前端：

- Vue 3
- TypeScript
- Vite
- Element Plus
- Pinia

部署：

- Docker / Docker Compose
- Kubernetes YAML 模板

## 目录结构

```text
hify
├── hify-app              # Spring Boot 启动模块、配置、数据库迁移、集成测试
├── hify-common           # 公共配置、异常、Result、日志、线程池、MyBatis 配置
├── hify-auth             # 账号、Session Token、角色鉴权
├── hify-model            # Provider / ModelConfig / LLM 调用适配器
├── hify-agent            # Agent 配置
├── hify-conversation     # 对话引擎、SSE、Function Calling
├── hify-knowledge        # 知识库、文档、pgvector 仓储
├── hify-mcp              # MCP Server 和工具接入
├── hify-workflow         # 简版工作流和模板库
├── hify-web              # Vue 前端
├── docker                # MySQL / PostgreSQL 初始化脚本
├── k8s                   # Kubernetes 部署模板
└── docs                  # 设计文档和实现计划
```

## 环境要求

本地开发：

- JDK 17+
- Maven 3.9+
- Node.js 18+
- npm

容器部署：

- Docker
- Docker Compose

## 本地开发启动

后端本地运行需要 MySQL、Redis、PostgreSQL + pgvector。可以先用 Docker Compose 启动依赖，也可以直接使用完整 Docker Compose 部署。

一键开发启动脚本：

```bash
./start.sh
```

脚本会：

- 构建后端 Maven 多模块项目
- 启动后端 `http://localhost:8080`
- 启动前端开发服务 `http://localhost:5173`
- 日志写入 `logs/`

停止：

```bash
./stop.sh
```

## 初始管理员

账号体系默认启用。首次启动时，如果系统中没有 `ADMIN` 用户，后端会根据环境变量创建初始管理员：

```bash
export HIFY_INIT_ADMIN_USERNAME=admin
export HIFY_INIT_ADMIN_PASSWORD='change-me'
```

如果未设置 `HIFY_INIT_ADMIN_PASSWORD`，系统不会创建弱默认密码。测试环境可通过 `hify.auth.enabled=false` 关闭认证拦截。

## Docker Compose 部署

复制并编辑环境变量文件：

```bash
cp .env.example .env
```

当前仓库不提交 `.env`，只提交不含真实密钥的 `.env.example`。账号体系相关变量：

```env
HIFY_INIT_ADMIN_USERNAME=admin
HIFY_INIT_ADMIN_PASSWORD=change-me
```

其他常用变量：

```env
MYSQL_ROOT_PASSWORD=change-me
MYSQL_DATABASE=hify
MYSQL_USER=hify
MYSQL_PASSWORD=change-me
MYSQL_PORT=3306

REDIS_PASSWORD=
REDIS_PORT=6379

POSTGRES_USER=hify
POSTGRES_PASSWORD=change-me
POSTGRES_DB=hify_vector
POSTGRES_PORT=5433

FRONTEND_PORT=80
BACKEND_PORT=8080
SPRING_PROFILES_ACTIVE=default

OPENAI_API_KEY=
ANTHROPIC_API_KEY=
GEMINI_API_KEY=
DASHSCOPE_API_KEY=
DEEPSEEK_API_KEY=
```

启动：

```bash
docker compose up -d --build
```

访问：

- 前端：`http://localhost`
- 后端健康检查：`http://localhost:8080/api/v1/health`

查看日志：

```bash
docker compose logs -f backend
docker compose logs -f frontend
```

停止：

```bash
docker compose down
```

清理数据卷：

```bash
docker compose down -v
```

## Kubernetes 部署

`k8s/` 目录提供基础部署模板：

```text
k8s/
├── hify-backend.yaml
├── hify-frontend.yaml
├── hify-ingress.yaml
├── hify-configmap-template.yaml
├── hify-secret-template.yaml
└── prometheus.yaml
```

部署前需要：

1. 构建并推送 `hify-backend` 和 `hify-frontend` 镜像到你的镜像仓库。
2. 修改 YAML 中的镜像地址。
3. 根据环境修改 ConfigMap 和 Secret。
4. 准备 MySQL、Redis、PostgreSQL + pgvector 服务，或替换为集群内同名服务。

示例：

```bash
kubectl apply -f k8s/hify-secret-template.yaml
kubectl apply -f k8s/hify-configmap-template.yaml
kubectl apply -f k8s/hify-backend.yaml
kubectl apply -f k8s/hify-frontend.yaml
kubectl apply -f k8s/hify-ingress.yaml
kubectl apply -f k8s/prometheus.yaml
```

生产环境建议：

- Secret 不要直接提交真实密钥。
- 为上传目录配置持久化卷。
- 为 SSE 入口关闭代理缓冲，并设置较长 read timeout。
- MySQL、Redis、PostgreSQL 使用托管服务或独立 StatefulSet。
- 1000 人规模部署、容量规划、备份恢复、慢查询治理和日志保留策略见 `docs/ops/deployment-operations.md`。

## 数据库

主库使用 MySQL，迁移脚本位于：

```text
hify-app/src/main/resources/db/migration
```

向量库使用 PostgreSQL + pgvector，迁移脚本位于：

```text
hify-knowledge/src/main/resources/db/pgvector
```

启动后端时 Flyway 会执行迁移。

## 测试

运行所有后端测试：

```bash
mvn test
```

运行 app 集成测试：

```bash
mvn -pl hify-app -am -Dtest=ProviderControllerIntegrationTest,ChatControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

集成测试使用：

- `mock` profile
- H2 内存库
- MockMvc
- `@Sql` 独立数据
- mock LLM / MCP / pgvector 等外部依赖

前端构建：

```bash
cd hify-web
npm run build
```

## 开发约定

核心约定见：

- `AGENTS.md`
- `CLAUDE.md`

重点规则：

- 模块间调用只能通过目标模块 `api/` 接口。
- `web/` 只处理 HTTP、参数校验和响应转换。
- `domain/` 承载业务逻辑和事务边界。
- `infra/` 承载 Mapper、外部 API 客户端和基础设施实现。
- 外部 LLM/MCP 调用必须有超时、熔断、fallback 或可控 mock。

## 安全说明

- 不要提交 `.env`、真实 API Key、数据库密码或 Kubernetes Secret。
- `application-local.yml`、日志、上传文件、构建产物已通过 `.gitignore` 排除。
- Docker Compose 示例变量只用于本地开发，生产环境必须替换强密码。
- Provider API Key、MCP Server endpoint、用户管理等高风险操作仅允许 `ADMIN`。
