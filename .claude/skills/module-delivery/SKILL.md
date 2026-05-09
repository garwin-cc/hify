---
name: module-delivery
description: Hify 项目模块交付流程（7阶段）。当用户说「开发 XX 模块」、「实现 XX 功能」、「帮我做 XX 模块」、「按规范交付模块」，或使用 module-delivery 流程时，必须使用本技能。覆盖从咨询对齐到完整验收的全流程：咨询对齐 → Schema/Flyway → Entity/Mapper → DTO → Service → Controller → 前端对接 → 验收测试。即使用户只是说「从第一步开始」或「按流程做」，也应触发本技能。
---

# 模块交付流程

按以下阶段顺序推进，**每个阶段结束前等待用户确认再进入下一阶段**。

---

## 阶段 0：咨询对齐（⚑ 等待用户确认）

**目标**：在写任何代码之前，把设计决策问清楚。

逐项输出分析，每项末尾给出推荐方案，等待用户拍板：

1. **外部依赖分析**：有无现成库可用？成熟度如何？（如 Spring AI、LangChain4j）
   - 给出「用 / 不用」的推荐和理由，不擅自引入新依赖

2. **数据模型草案**：列出核心表及关键字段，重点标注：
   - 多值字段如何存储（JSON 列 vs 关联表）
   - 枚举字段的取值范围
   - 是否需要单独的状态/健康表
   - 多对多关联表：是否需要 deleted / version 字段（纯关联表用硬删除，不继承 BaseEntity）

3. **模块边界**：与其他模块的交互点，通过哪个 `api/` 接口调用
   - 跨模块验证时，调用**语义最精确**的 Service（如验证 modelConfigId 应走 `ModelConfigService`，而非 `ProviderService`）

4. **一期 MVP 范围**：哪些功能现在做，哪些明确不做

> **⚠ 注意**：不要在咨询阶段写代码，咨询结束拿到确认后再进入阶段 1。

---

## 阶段 1：Schema & Flyway 迁移（⚑ 等待用户确认 Schema 设计）

**产出物**：`hify-app/src/main/resources/db/migration/V{n}__{描述}.sql`

### 步骤

1. 确认当前最高版本号：
   ```bash
   docker exec mysql mysql -uhify -phify123 hify \
     -e "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"
   ```
2. 按 CLAUDE.md 数据库规范建表（id / created_at / updated_at / deleted 必须有）
3. 写好迁移文件后**重启 Spring Boot app** 触发迁移（Flyway 以 library 方式集成，**无 Maven plugin**，不要执行 `mvn flyway:migrate`）

### 验证

```bash
mvn spring-boot:run -pl hify-app   # 启动时 Flyway 自动执行迁移

docker exec mysql mysql -uhify -phify123 hify \
  -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

### ⚠ 注意事项

- **软删除 + 唯一约束冲突**：有 `deleted` 字段的表不能用 DB 级 `UNIQUE`。改为应用层校验：`LambdaQueryWrapper` 查 `deleted=0` 的记录，count > 0 则抛 `BizException(CONFLICT)`。
- **纯关联表（join table）不继承 BaseEntity**：没有软删除语义，用硬删除。字段只留 `id`、外键、`sort_order`、`createdAt`，不加 `deleted`、`version`。
- 健康/状态类辅助表不需要 `deleted` 字段。

---

## 阶段 2：Entity + Mapper

**产出物**：`hify-{module}/src/main/java/com/hify/{module}/infra/` 下的 `*Po.java` 和 `*Mapper.java`

### 步骤

1. 主表 Po：继承 `BaseEntity`，加 `@TableName(autoResultMap = true)`（JSON 列必须加）
2. JSON 列：`@TableField(typeHandler = JacksonTypeHandler.class)`
3. 关联表 Po：**不继承 BaseEntity**，手动声明 `@TableId(type = IdType.AUTO)`
4. Mapper：继承 `BaseMapper<XxxPo>`，加 `@Mapper`

### 验证

```bash
# 从仓库根目录执行
cd /Users/lanzelot/workspace/hify && mvn install -DskipTests -q
```

### ⚠ 注意事项

- **`mvn install` 必须从仓库根目录执行**：`spring-boot:run -pl hify-app` 从 `~/.m2` 读依赖模块 JAR，`compile` 只更新 `target/classes` 不安装到本地仓库，改动不会生效。
- **`created_by` 不能依赖数据库默认值**：必须在 `HifyMetaObjectHandler.insertFill()` 中 `strictInsertFill` 填充 0L。

---

## 阶段 3：DTO 层（api/）

**产出物**：`hify-{module}/src/main/java/com/hify/{module}/api/` 下的请求/响应 DTO

### 规则

- **请求 DTO**：字段加 `@NotBlank` / `@Size` / `@NotNull` 等 Bean Validation 注解
- **响应 DTO**：不暴露 PO，不暴露 apiKey 明文
- **UpdateXxx 的 null 语义**：`null = 跳过`，`[] = 清空`，**必须在 DTO 注释中标注**

### 验证

```bash
cd /Users/lanzelot/workspace/hify && mvn install -DskipTests -q
```

---

## 阶段 4：Service 层（domain/）

**产出物**：`{Module}Service` 接口（api/）+ `{Module}ServiceImpl`（domain/）

### CRUD 实现检查清单

- [ ] 创建：校验业务唯一性，`@Transactional`
- [ ] 列表：支持过滤，返回 `PageResult<T>`；关联数据用批量 IN 查询（见下方）
- [ ] 详情：`@Cacheable(cacheNames = "xxx:detail", key = "#id")`
- [ ] 更新：null 字段跳过，`@CacheEvict(allEntries = true)`
- [ ] 删除：先 `findOrThrow`，关联表先硬删除，再软删主表，`@CacheEvict`
- [ ] Toggle：单字段切换，`@CacheEvict`

### 缓存策略

```
详情  → @Cacheable("xxx:detail", key="#id")
列表（含关联/时效数据）→ 不缓存，直接查库
写操作 → @Caching(evict={@CacheEvict("xxx:list",allEntries=true), @CacheEvict("xxx:detail",allEntries=true)})
```

### 跨模块调用规则（⚑ 关键决策点）

- 只能通过目标模块的 `api/` 接口，不能直接 import 其他模块的 Mapper 或 Po
- **选语义最精确的 Service**：验证 modelConfigId → `ModelConfigService`，不是 `ProviderService`

### 批量查询代替 N+1

```java
Map<Long, Long> countMap = CollectionUtils.isEmpty(ids) ? Collections.emptyMap()
    : mapper.selectList(new LambdaQueryWrapper<XxxPo>().in(XxxPo::getParentId, ids))
      .stream().collect(Collectors.groupingBy(XxxPo::getParentId, Collectors.counting()));
```

### 验证

```bash
mvn spring-boot:run -pl hify-app

curl -X POST http://localhost:8080/api/v1/{resources} \
  -H "Content-Type: application/json" -d '{"name":"test",...}'
curl "http://localhost:8080/api/v1/{resources}?page=1&pageSize=10"
curl -X PUT http://localhost:8080/api/v1/{resources}/1 -H "Content-Type: application/json" -d '{...}'
curl -X DELETE http://localhost:8080/api/v1/{resources}/1

# @Valid 拦截
curl -X POST http://localhost:8080/api/v1/{resources} \
  -H "Content-Type: application/json" -d '{}'
# 期望 code=400
```

### ⚠ 注意事项

- **Redis 序列化 LocalDateTime**：`CacheConfig` 用自定义 `redisJsonSerializer()` Bean，不要在两处各自 new ObjectMapper。
- **含时效性关联数据的列表不缓存**：健康状态每分钟更新，缓存反而有害。
- **关联表更新用全量删除 + 重新插入**，比 diff 更新简单且不易出错。

---

## 阶段 5：Controller 层（web/）

**产出物**：`{Module}Controller.java`

```java
@RestController
@RequestMapping("/api/v1/{resources}")
@RequiredArgsConstructor
public class XxxController {
    @GetMapping                 public PageResult<XxxListItemResp> list(XxxQuery query) { ... }
    @GetMapping("/{id}")        public Result<XxxDetailResp> get(@PathVariable Long id) { ... }
    @PostMapping                public Result<XxxResp> create(@Valid @RequestBody CreateXxxReq req) { ... }
    @PutMapping("/{id}")        public Result<XxxResp> update(@PathVariable Long id, @Valid @RequestBody UpdateXxxReq req) { ... }
    @DeleteMapping("/{id}")     public Result<Void> delete(@PathVariable Long id) { ... }
    @PostMapping("/{id}/action") public Result<XxxResult> action(@PathVariable Long id) { ... }
}
```

### 验证

同阶段 4 curl，确认 404/409/400 各返回正确 code。

---

## 阶段 6：前端对接

**产出物**：`src/api/{module}.ts` + 更新视图文件

### api 文件规范

```typescript
export interface XxxListItem { ... }
export const XXX_TYPES = [ { label: '...', value: '...' } ]
export const getXxxList = (page: number, pageSize: number): Promise<PageData<XxxListItem>> =>
  get('/v1/{resources}', { page, pageSize })
```

### 视图替换检查清单

- [ ] `HifyTable :api` 换成真实 API 函数
- [ ] 删除全部 mock 数据
- [ ] 表单 submit 改为调真实 API，catch 后调 `done(false)`
- [ ] 跨模块下拉数据（模型分组等）从对应模块接口拉取

### 跨模块下拉策略

| 场景 | 策略 |
|------|------|
| 模型选择（按供应商分组）| 调 `getProviderList`，前端用 `el-option-group` |
| 工具多选 | 调 `getMcpServerList`，后端只返回 enabled |

### 验证

1. Network 面板确认请求走真实 API
2. 新增 → 编辑（回显完整）→ 删除全流程
3. 关联字段（toolCount 等）数值正确

---

## 阶段 7：完整验收

```bash
# 1. 编译
cd /Users/lanzelot/workspace/hify && mvn install -DskipTests -q

# 2. 启动
mvn spring-boot:run -pl hify-app

# 3. 接口冒烟
curl -X POST http://localhost:8080/api/v1/{resources} \
  -H "Content-Type: application/json" -d '{"name":"验收测试",...}'
# → code=200，含所有关联字段

curl "http://localhost:8080/api/v1/{resources}?page=1&pageSize=10"
# → records 含 toolCount 等关联字段

curl -X DELETE http://localhost:8080/api/v1/{resources}/{id}
# → code=200

# 4. 边界验证
curl -X POST http://localhost:8080/api/v1/{resources} -H "Content-Type: application/json" -d '{}'  # → 400
curl -X POST http://localhost:8080/api/v1/{resources} -H "Content-Type: application/json" \
  -d '{"name":"验收测试",...}'  # 重复名称 → 409

# 5. 浏览器验收（手动）
# 列表加载 → 新增 → 编辑（回显正确）→ 启用/禁用 → 删除
```

---

## 快速排障手册

| 症状 | 原因 | 解决 |
|------|------|------|
| `Unknown column 'xxx' in field list` | 旧 JAR 未更新 | `mvn install -DskipTests -q`（根目录）再重启 |
| 修改代码后重启改动不生效 | 只跑了 `mvn compile` | 必须 `mvn install` 让新 JAR 进入 `~/.m2` |
| `Column 'created_by' cannot be null` | MetaObjectHandler 未 fill | `insertFill` 加 `strictInsertFill(meta,"createdBy",Long.class,0L)` |
| `LocalDateTime not supported`（Redis）| CacheConfig 用了无 JavaTimeModule 的序列化器 | `redisJsonSerializer()` 暴露为 `@Bean`，CacheConfig 注入共用 |
| Flyway checksum mismatch | 手动改了迁移文件 | 用正确 CRC32 更新 `flyway_schema_history.checksum` |
| `mvn flyway:migrate` 报 goal not found | 项目无 flyway-maven-plugin | 重启 app 触发迁移，不要用 Maven goal |
| `spring-boot:run` 找不到 main class | 从根目录直接跑 | 改为 `mvn spring-boot:run -pl hify-app` |
| curl 带中文参数返回 400 | Tomcat 拒绝 raw 非 ASCII | curl 测试用英文；axios 自动 URL encode，不是 bug |
| 软删除记录的名称无法复用 | 表有 DB UNIQUE 约束 | 去掉 UNIQUE，改为应用层 `checkNameUnique(name, excludeId)` |
| 编辑弹窗回显不全 | 列表接口未返回详情字段 | `@open` 事件触发时额外调 `getDetail(id)` 补全 |
| 列表关联字段长时间不更新 | 列表加了 `@Cacheable` | 含时效性数据的列表不缓存，去掉 `@Cacheable` |
