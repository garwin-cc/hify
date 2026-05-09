---
name: service-unit-test
description: Hify Service 方法单元测试工作流。Use when the user invokes /单测 or asks to write, plan, review, or fix unit tests for any Java Service method, especially Spring Service methods with Mapper dependencies, DTO validation, ErrorCode/BizException behavior, or external collaborators that must be mocked.
---

# Service Unit Test

## 目标

按“先分析、再计划、确认后写代码、最后验证”的流程为 Hify 任意 Service 方法补单元测试。不要一上来写测试代码。

## 触发方式

- 用户输入 `/单测 <ServiceClass.method>`。
- 用户要求“给某个 Service 方法写单测/补单测/按 CLAUDE.md 单测规范测试”。
- 用户要求分析某个 Service 方法的执行路径、边界条件、测试计划，并准备后续落地。

## 工作流

### 1. 读代码

按顺序读取，避免只看被测方法：

1. 先读被测 Service 类完整上下文，至少包括：
   - 被测方法。
   - 被测方法调用的 private helper。
   - 构造器注入依赖。
   - 事务、缓存、校验、异步相关注解。
2. 再读依赖 DTO：
   - Request/Command DTO 的字段。
   - `@NotBlank`、`@NotNull`、`@Size`、自定义校验注解。
   - 默认值和字段注释。
3. 再读 `ErrorCode` 和 `BizException`：
   - 确认可用错误码是否匹配用户预期。
   - 如果用户计划中的错误码不存在，必须指出。
4. 读已有测试：
   - 同模块同类测试。
   - 同项目类似 Service 测试。
   - 当前项目的 mock、AssertJ、Spring/Mockito 风格。
5. 必要时读 Mapper/PO：
   - 只为了确认字段名、默认值、insert 是否回填 id 的测试模拟方式。
   - 不把 Mapper SQL 行为当作单测目标。

### 2. 分析执行路径

先输出计划，不写代码。输出必须包含以下三块。

#### 执行路径树

用树形结构列出正常路径和异常路径：

```text
Service.method(req)
├── P1 正常路径：条件 -> 关键动作 -> 返回结果
├── P2 正常变体：条件 -> 默认值/分支 -> 返回结果
├── E1 异常路径：触发条件 -> 抛出异常/错误码 -> 是否继续写库
└── E2 异常路径：触发条件 -> 抛出异常/错误码 -> 是否继续写库
```

每条路径标注：

- 触发条件。
- 关键变量。
- 主要副作用：insert/update/delete、外部调用、缓存、事件、异步任务。
- 预期异常类型和 `ErrorCode`。

#### 边界条件

用表格列出最容易出错的边界：

| 边界条件 | 当前实现行为 | 风险 | 建议测试层级 |
|---|---|---|---|
| DTO 字段为 null | 例如 NPE / ConstraintViolationException | Service 是否自保护不清晰 | DTO 约束测试或 Service 单测 |

必须特别检查：

- `null`、blank、空集合、重复值。
- 大小写归一化、trim、默认值。
- id 回填、状态默认值、计数增减。
- 错误码是否存在且语义正确。
- Bean Validation 是否在 Service 层生效，还是只在 Controller 层生效。
- 外部依赖异常是否应透传、包装为 `BizException`，或触发 fallback。

#### 分优先级的场景表

按 P0/P1/P2 输出测试计划：

| 优先级 | 测试方法名 | 场景 | Given | When | Then 断言 |
|---|---|---|---|---|---|
| P0 | `should_x_when_y` | 核心成功/核心异常 | mock 和输入 | 调用被测方法 | 断言返回、错误码、副作用 |

要求：

- 方法名必须遵循 `should_[期望结果]_when_[输入条件]`。
- 每个测试只验证一个主要行为。
- Then 断言写业务结果，不只写“非空”。

### 3. 写代码前技术确认

输出测试计划后暂停，等待用户确认。确认前不要写测试代码。

确认时主动核对并指出技术问题：

- mock 方式：
  - 用户要求 `@MockBean` 时，使用 Spring 测试上下文和 `@MockBean`。
  - 普通纯单测优先 Mockito `@Mock` + `@InjectMocks`。
  - 明确哪些依赖必须 mock，哪些不能 mock。
- 断言库：
  - 默认 AssertJ。
  - 禁止 `assertTrue`、`assertEquals`、无意义 `isNotNull`。
- Bean Validation：
  - 如果计划包含 DTO 约束场景，确认是 Service 层触发还是 Controller/Web 层触发。
  - 如果当前 Service 没有校验能力，必须说明测试会失败，并询问/确认是否补 Service 校验或拆到 DTO 约束测试。
- 错误码：
  - 如果用户指定的错误码不存在，必须指出。
  - 确认是新增错误码、改用现有错误码，还是调整测试期望。
- 测试文件拆分：
  - Service 业务逻辑测试和 DTO 约束测试分开。
  - 不把 Bean Validation DTO 规则混在纯 Service mock 测试里，除非 Service 明确调用 validator。
- 测试类型边界：
  - Mapper SQL、事务回滚、缓存 AOP、真实 HTTP、真实 Redis/pgvector 不写普通单测。
  - 这些场景标记为集成测试，不在本轮实现，除非用户明确要求。

### 4. 写测试代码

用户确认计划后再写代码。执行顺序：

1. 先写失败测试，不改生产代码。
2. 跑目标测试，确认失败点是预期行为缺失，而不是测试编译错误或 mock 写错。
3. 如果失败原因是测试期望和用户确认的目标一致，补最小生产实现。
4. 如果失败原因是测试写错，修正测试，不改生产代码。
5. 保持测试结构为 Given-When-Then。
6. 使用 `ArgumentCaptor` 或 `argThat` 检查关键入参；不要用过宽的 `any()` 掩盖输入约束。

### 5. 测试文件拆分原则

按职责拆文件：

- `<ServiceClass>Test`
  - 测 Service 业务流程、Mapper/协作者交互、返回 DTO、`BizException`/`ErrorCode`。
  - mock Mapper、外部 Service、HTTP/LLM/MCP client。
- `<RequestDto>ValidationTest`
  - 测 DTO 注解约束，如 `@NotBlank`、`@Size`、`@NotNull`。
  - 使用 `jakarta.validation.Validator`。
  - 不 mock Service。
- `<Controller>Test` 或 Web 集成测试
  - 测 Spring 参数绑定、`@Valid` 自动触发、`GlobalExceptionHandler` 响应格式。

不要把以下内容塞进 Service 单测：

- Mapper SQL 是否正确。
- 真实数据库是否有记录。
- `@Transactional` 是否回滚。
- `@CacheEvict` 是否真的清缓存。
- 真实外部 HTTP/SSE 行为。

### 6. 跑测试和处理失败

优先跑最小范围：

```bash
mvn -pl <module> -Dtest=<TestClass> test
```

如果被测模块依赖其他模块的新代码：

```bash
mvn -pl <module> -am -Dtest=<TestClass> -Dsurefire.failIfNoSpecifiedTests=false test
```

最后跑模块测试：

```bash
mvn -pl <module> -am test
```

失败处理流程：

1. 先读失败信息，分类：
   - 编译失败：缺类、错误码不存在、导入错。
   - 测试写错：mock 没配置、断言错、测试没有触达目标行为。
   - 实现 bug：业务逻辑缺失、错误码不对、未校验、未写副作用。
   - 环境问题：缺依赖、端口、数据库、网络。
2. 明确告诉用户是哪一类。
3. 只修当前测试计划覆盖的最小范围。
4. 修完重跑同一条测试命令。
5. 通过后再跑模块范围验证。

### 7. 输出要求

分析阶段输出：

- 执行路径树。
- 边界条件表。
- P0/P1/P2 测试场景表。
- 技术确认清单和需要用户确认的问题。

实现完成输出：

- 修改了哪些测试文件和生产文件。
- 每个测试覆盖了哪个确认场景。
- 跑了哪些命令，结果如何。
- 如果为满足确认计划补了生产实现，明确说明。
