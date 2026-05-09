---
name: api-contract
description: Hify 模块接口契约设计。当需要为某个模块设计或审查 REST API 接口时使用，包括：定义接口路径、入参/出参结构、HTTP 方法、分页格式、错误码约定，以及删除/非 CRUD 动词接口的特殊处理。
---

# 接口契约设计

## 步骤

1. 列出这个模块的核心业务操作（创建、查询、更新、删除、特殊操作）
2. 每个操作定义：HTTP 方法 + 路径 + 入参（字段名/类型/必填） + 出参（data 字段结构）
3. 路径遵循 `/api/v1/{资源复数名}` 格式
4. 所有接口统一返回 `Result<T>`：

```json
{ "code": 200, "message": "ok", "data": {} }
```

5. 列表接口统一支持分页参数 `page`（从 1 开始）和 `pageSize`，data 返回：

```json
{ "records": [], "total": 100, "page": 1, "size": 10 }
```

6. 删除接口需标注：是否需要关联检查（如删除 Agent 前检查是否有进行中的对话）
7. 非 CRUD 操作使用动词路径，例如：
   - `POST /api/v1/providers/{id}/test-connection`
   - `PUT /api/v1/agents/{id}/enabled/{enabled}`

## 输出格式

按以下结构输出每个接口：

```
### 操作名称
METHOD /api/v1/path

入参（Body / Query / Path）：
- field: 类型，必填/可选，说明

出参 data：
- field: 类型，说明

备注：（关联检查、特殊逻辑等）
```

## Hify 约定

- 软删除：逻辑删除，`deleted=1`，不物理删除
- 创建人/更新人由后端自动填充，不由前端传入
- 枚举值用字符串常量（如 `"OPENAI"`），不用数字
- 模块资源名：`providers`、`model-configs`、`agents`、`mcp-servers`、`conversations`、`messages`、`knowledge-bases`、`workflows`
- Toggle 操作用 `PUT /{id}/enabled/{0|1}`，不用 PATCH
- 批量操作返回每条结果，不因单条失败而整体 500
