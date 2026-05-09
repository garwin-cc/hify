---
name: hify-integration-test
description: Hify 模块集成测试工作流。Use when the user invokes /集成测试 or asks to plan, write, debug, or extend Spring Boot + MockMvc integration tests for any Hify module, especially tests using mock profile, H2, @Sql data setup, external API mocks, SSE, Controller-to-DB flows, or known bug red-green verification.
---

# Hify Integration Test

## 目标

为 Hify 任意模块规划和落地集成测试。默认测试 Hify 自身从 Controller 到数据库的完整链路：Spring Boot Test + MockMvc + mock profile + H2。外部系统 mock 掉，数据库使用真实 H2。

不要一开始批量写代码。先确认技术基础，先给清单；用户指定场景后，从最简单场景开始，一个跑通再写下一个。

## 触发方式

- 用户输入 `/集成测试 <模块/链路/接口/场景>`。
- 用户要求为某个模块规划或编写集成测试。
- 用户要求验证 Controller 到数据库完整链路、SSE 流、MockMvc、mock profile、H2、`@Sql` 数据准备。
- 用户提到“已知 bug，要先写失败测试再修”。

## 工作流

### 1. 先读配置文件，确认技术基础

先确认项目已有测试基础，不要凭印象创建新 profile。

按顺序读取：

1. `CLAUDE.md`
   - 核心链路地图。
   - 风险集中区域。
   - 集成测试或单测相关规范。
2. app 测试配置：
   - `hify-app/src/test/resources/application-mock.yml`
   - `hify-app/src/test/resources/db/mock/schema.sql`
   - 已有 `application-test.yml` 只有明确存在且用户允许时才使用；Hify 默认不新建。
3. app 测试依赖：
   - `hify-app/pom.xml` 是否有 `spring-boot-starter-test`、H2。
4. 目标链路代码：
   - Controller：URL、HTTP method、请求/响应格式、SSE produces。
   - Service/domain：事务、异步、外部依赖、错误处理。
   - Mapper/PO：表名、字段、逻辑删除、JSON type handler。
   - DTO：`@Valid`、字段名、默认值。
   - `GlobalExceptionHandler` 和 `Result`：确认 HTTP status 与 body code 规则。
5. 已有类似集成测试：
   - 测试基类。
   - `@Sql` 用法。
   - MockMvc/SSE 收集方式。
   - mock profile 下的外部依赖替身。

如果用户声称某个 mock profile 或 mock bean 已存在，但代码里没找到，必须指出，并给出最小补齐方案。

### 2. 先规划测试清单，不写代码

基于 `CLAUDE.md` 核心链路和风险地图，按 P0/P1/P2 输出表格：

| IT 编号 | 场景 | 验证点 | 优先级 | 依赖准备 | mock 策略 |
|---|---|---|---|---|---|
| IT-01 | Provider 创建 | HTTP 200；body.code=200；DB 有记录 | P0 | `@Sql` 插 provider 前置数据 | 无外部 mock |

优先级定义：

- P0：核心链路必须覆盖；失败会导致主流程不可用或数据错误。
- P1：主要功能应该覆盖；覆盖常用分支、关键异常、权限/状态/分页等。
- P2：边缘场景有余力再做；低频输入、展示型字段、简单透传。

每条测试清单必须说明：

- 测什么。
- 验证什么断言。
- 为什么是这个优先级。
- 要不要 mock 外部依赖。
- 是否需要新增/补齐 mock schema。

### 3. 写代码前技术确认

计划后暂停，除非用户已经明确指定“先写某个场景”。写代码前确认：

- profile：使用 `mock` profile，H2 内存库；默认不新建 `application-test.yml`。
- 测试基类：是否复用已有基类。
- MockMvc：`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureMockMvc`。
- 数据准备：每个测试方法独立 `@Sql`，不共享数据。
- 事务策略：
  - 普通同步接口默认 `@Transactional + @Rollback`。
  - SSE/异步线程测试不要依赖测试事务回滚；用 isolated `@Sql` 插入和清理，避免异步线程看不到未提交数据。
- mock 策略：
  - 外部 LLM API、MCP Server、Redis、pgvector、第三方 HTTP 必须 mock 或用 mock profile 替身。
  - MySQL 主库链路用真实 H2，不 mock Mapper/Repository。
- 已知 bug：先写失败测试并运行，确认红灯原因正确，再修生产代码。

### 4. 标准 mock 策略决策表

| 依赖类型 | 集成测试处理 | 原因 | 例子 |
|---|---|---|---|
| Controller/Service/Mapper | 不 mock | 要验证 Hify 自身完整链路 | Provider CRUD、Chat sendMessage |
| 主数据库 MySQL | 用 H2 mock profile | 快、可重复、保留真实 SQL/Mapper 行为 | `application-mock.yml` |
| 外部 LLM API | mock profile adapter 或 `@MockBean` | 不依赖网络、成本、供应商稳定性 | `MockProviderAdapter` |
| MCP Server 外部调用 | mock client/service | 外部工具不可控；只验证 Hify 调用行为 | `McpClientService.callTool` |
| pgvector/PostgreSQL | mock repository 或 profile 排除 | H2 不支持 vector；外部库不属于本链路 | `KnowledgeVectorRepository` |
| Redis/缓存 | mock 或禁用 | 集成重点不是缓存基础设施 | `spring.data.redis.repositories.enabled=false` |
| 时间/随机 ID | 优先断言业务结果，不强行 mock | 避免脆弱测试 | 断言 id 有值、时间非空 |
| Bean Validation | Controller 集成测试验证 | Web 层触发 `@Valid` 更真实 | 请求字段为空 |

规则：

- 不 mock 被测链路内部核心类。
- 不 mock Mapper 来替代数据库断言。
- mock 要有明确边界和 verify；不要用过宽 `any()` 掩盖关键入参。
- 如果必须用 `@MockBean`，检查 Mockito mock maker；sandbox 下优先 `mock-maker-subclass`。

### 5. 测试基类模板

优先复用项目已有基类。没有时按此模板创建：

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("mock")
@Transactional
@Rollback
public abstract class HifyMockIntegrationTest {
}
```

如果需要给 mock profile 补外部依赖替身：

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("mock")
@Transactional
@Rollback
@Import(HifyMockIntegrationTest.MockExternalConfig.class)
public abstract class HifyMockIntegrationTest {
    @TestConfiguration
    public static class MockExternalConfig {
        @Bean
        @Primary
        SomeExternalRepository someExternalRepository() {
            return new NoopSomeExternalRepository();
        }
    }
}
```

SSE/异步测试类覆盖父类事务：

```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatControllerIntegrationTest extends HifyMockIntegrationTest {
}
```

### 6. 场景递进原则

一次只写当前用户确认的最小场景：

1. 先写最简单成功路径。
2. 跑通。
3. 再写核心异常路径。
4. 跑通。
5. 再写状态变化/DB 副作用。
6. 跑通。
7. 最后写边界、并发、失败恢复、已知 bug。

不要批量生成一整组复杂测试。每新增一个场景后至少跑目标测试类。

测试命名遵循：

```text
should_[期望结果]_when_[输入条件]
```

测试结构使用 Given-When-Then。断言用 AssertJ，断言业务语义，不只断言非空。

### 7. `@Sql` 数据原则

- 每个测试方法独立准备数据。
- 固定 id，便于断言和清理。
- 不依赖其他测试执行顺序。
- 普通同步测试可用 `@Transactional + @Rollback`。
- 异步/SSE 测试用 `@SqlConfig(transactionMode = ISOLATED)`，并在 `AFTER_TEST_METHOD` 清理。
- `@Sql` 字符串尽量保持单条 SQL 一行，避免被解析成半条语句。
- mock schema 缺表时，补 `hify-app/src/test/resources/db/mock/schema.sql` 的最小字段集合；字段必须覆盖 PO 映射和测试断言。

### 8. 已知 bug 处理方式

严格红绿：

1. 先写能暴露 bug 的最小集成测试。
2. 运行该单条测试。
3. 确认失败类型正确：
   - 失败断言指向目标 bug：继续修。
   - 编译错误、SQL 缺表、mock 配置错：先修测试基础，再重跑直到红灯正确。
   - 测试直接通过：说明当前代码可能已修复，必须告知用户，不要为了制造红灯改坏代码。
4. 只修 bug 所在最小生产代码。
5. 重跑同一条测试，确认绿。
6. 跑相关测试类。
7. 跑已覆盖核心链路的回归组合。

### 9. 推荐命令

单条测试：

```bash
mvn -pl hify-app -am -Dtest=<TestClass>#<testMethod> -Dsurefire.failIfNoSpecifiedTests=false test
```

测试类：

```bash
mvn -pl hify-app -am -Dtest=<TestClass> -Dsurefire.failIfNoSpecifiedTests=false test
```

核心回归组合：

```bash
mvn -pl hify-app -am -Dtest=ProviderControllerIntegrationTest,ChatControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

如果 `RANDOM_PORT` 在 sandbox 下无法绑定本地端口，按工具规则申请提权运行同一命令。

### 10. 输出要求

规划阶段输出：

- 已确认的 mock profile/H2 技术基础。
- P0/P1/P2 测试清单表。
- mock 策略表或本次 mock 决策。
- 需要用户确认的下一条场景。

实现阶段输出：

- 写了哪个 IT 场景。
- 修改了哪些测试文件、mock 配置、mock schema、生产文件。
- 测试断言覆盖了哪些验证点。
- 跑了哪些命令和结果。
- 如果已知 bug 测试没有变红，明确说明当前实现已经满足预期，不修生产代码。
