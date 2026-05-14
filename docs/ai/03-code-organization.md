# Hify AI 路由文档

> 本文件由原 `CLAUDE.md` 拆分而来，保留对应章节原文。

## 代码组织规范

### Maven 模块结构

当前仓库按 Maven 多模块组织：

```text
hify-common/         # 通用配置、异常、Result、日志、指标、HTTP 基础设施
hify-model/          # LLM Provider、ModelConfig、Embedding、Provider Adapter
hify-agent/          # Agent 配置、知识库/工作流/MCP 工具绑定
hify-conversation/   # 对话、消息、SSE、RAG 注入、MCP tool calls、workflow 触发
hify-knowledge/      # 知识库、文档上传、异步解析、向量化、pgvector 检索
hify-workflow/       # 工作流定义、执行引擎、节点执行器、人审、模板、版本
hify-mcp/            # MCP Server、MCP Tool、SDK/raw HTTP 调用
hify-app/            # Spring Boot 启动模块、Flyway migration、集成装配
hify-web/            # Vue 3 + Element Plus 前端
```

后端 Java 包仍统一在 `com.hify` 下，各业务模块分别放在对应 Maven module 中。

每个模块内部四层结构：

```text
hify-{module}/src/main/java/com/hify/{module}/
├── api/       # 对外暴露的接口（interface），供其他模块调用
├── domain/    # 业务逻辑：Service 实现、领域对象、Factory、Repository 接口
├── infra/     # 基础设施：Mapper、RepositoryImpl、外部 API 客户端、config
└── web/       # Controller，只处理 HTTP 层
```

### 各层职责边界

| 层 | 职责 | 禁止 |
|----|------|------|
| web/ | 接收请求、参数校验（@Valid）、调用本模块 api/ 接口、返回 Result<T> | 直接调用其他模块 domain/、直接操作数据库 |
| api/ | 定义跨模块调用的 interface 和 DTO | 包含业务逻辑实现 |
| domain/ | 业务逻辑、领域对象、事务边界（@Transactional） | 依赖 web 层、把 HTTP 参数直接向下传递 |
| infra/ | Mapper、RepositoryImpl（PO ↔ 领域对象转换）、外部调用 | 包含业务逻辑 |

### 跨模块调用规则

- **只能**通过目标模块的 `api/` 接口调用，禁止直接 import 其他模块的 `domain/` 或 `infra/` 类
- 跨模块传递使用 `api/` 包下定义的 DTO，不传递 PO 或领域对象
- 循环依赖视为架构错误，立即重构
- 历史代码中如果已有跨模块 `domain/infra` 依赖，新增改动不得扩大依赖范围；重构时优先补 `api` 接口再迁移调用方。

```java
// 正确：agent 模块通过 ModelService（api/ 接口）调用 model 模块
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {
    private final ModelService modelService; // 来自 model 模块的 api/ 接口
}
```

---
