# Hify AI 路由文档

> 本文件由原 `CLAUDE.md` 拆分而来，保留对应章节原文。

## 编码规范（基于阿里巴巴 Java 开发手册）

### 命名

1. **类名用 UpperCamelCase**，方法名、变量名用 lowerCamelCase，常量用 UPPER_SNAKE_CASE，包名全小写无下划线。
2. **禁止用拼音或拼音缩写**命名，禁止单字母变量（循环变量 `i/j/k` 除外）。
3. **方法名体现动词**：查询用 `get/list/query`，修改用 `update`，删除用 `delete/remove`，新增用 `create/add`，布尔返回值用 `is/has/can`。
4. **Service 接口不加 I 前缀**，实现类加 `Impl` 后缀（`AgentService` + `AgentServiceImpl`）。
5. **数据库表名用 `t_` 前缀**，列名用 snake_case；PO 类用 `Po` 后缀，DTO 用 `Dto`/`Request`/`Response`，Mapper 用 `Mapper` 后缀。

### 异常处理

6. **禁止 catch 后 `e.printStackTrace()` 或空 catch**，必须记录日志或向上抛出。
7. **业务异常统一抛 `BizException(ErrorCode)`**，不用 RuntimeException 传递业务语义。
8. **只在顶层（GlobalExceptionHandler）处理并转换为 HTTP 响应**，中间层不捕获再包装。
9. **finally 块不写 return**，不在 finally 中抛出新异常（会吞掉原始异常）。
10. **NPE 防御**：方法返回值优先返回空集合（`Collections.emptyList()`）而非 null，接口入参用 `@NonNull`/`@Valid` 注解声明约束。

### 日志

11. **使用 SLF4J 接口 + Logback 实现**，类中用 `@Slf4j`（Lombok），禁止用 `System.out.println`。
12. **禁止在循环体内打日志**，高频路径只在异常分支记录。
13. **占位符格式 `log.info("xxx {}", var)`**，禁止字符串拼接（避免无效 toString 开销）。
14. **日志分级约定**：DEBUG=详细调试，INFO=关键业务节点，WARN=可恢复异常或配置缺失，ERROR=需人工介入的故障。生产环境 INFO 级别，日志文件按天滚动，保留 30 天。
15. **LLM 调用必须记录**：provider、model、耗时、token 数、是否命中缓存，便于成本分析。

### 并发

16. **线程池必须显式创建**（`ThreadPoolExecutor`），禁止用 `Executors.newFixedThreadPool`（无界队列 OOM）。
17. **ThreadLocal 用完必须 `remove()`**，防止线程池场景下数据泄漏。
18. **加锁粒度最小化**：只锁共享变量操作，不锁 I/O 和 LLM 调用；优先用 `ReentrantLock` 替代 `synchronized`（可设超时）。
19. **单例 Bean 的成员变量必须是线程安全的**：无状态 Service 天然安全；有状态则用 `ThreadLocal` 或局部变量，禁止用实例变量存请求上下文。
20. **`CompletableFuture` 异步调用必须指定线程池**（`supplyAsync(task, llmExecutor)`），禁止用默认 `ForkJoinPool.commonPool()`（会影响其他异步任务）。

---
