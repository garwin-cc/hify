---
name: provider-adapter
description: 为 Hify 接入新的 LLM 供应商（如 Gemini、Mistral 等）。当用户说「接入 XX 供应商」、「支持 XX 模型」，或需要新增 Provider 类型时使用。当前架构已使用 Strategy 模式（ProviderAdapterFactory + 各 Adapter），新增供应商只需新建一个 Adapter 类并注册到 Factory。
---

# 新增供应商 Adapter

## 当前架构（Strategy 模式）

```
ProviderAdapter（接口）
  └── AbstractProviderAdapter（公共逻辑：HTTP 调用、超时、错误处理）
        ├── OpenAiAdapter          → OPENAI、DEEPSEEK（Bearer + /v1/models + "data" 键）
        │     └── OpenAiCompatibleAdapter  → OPENAI_COMPATIBLE（继承 OpenAiAdapter，空实现）
        ├── AnthropicAdapter       → ANTHROPIC（x-api-key + anthropic-version + /v1/models）
        └── OllamaAdapter          → OLLAMA（无认证 + /api/tags + "models" 键）

ProviderAdapterFactory → getAdapter(type) 返回对应 Adapter
ProviderServiceImpl    → factory.getAdapter(type).testConnection(provider)
ProviderHealthCheckJob → factory.getAdapter(type).testConnection(provider)
```

**新增供应商不再需要同步修改两个文件**，只改 Factory + 新建 Adapter 即可。

---

## 阶段 0：分析目标 API（⚑ 等待用户确认）

调研目标供应商的 API，确认以下五点后再写代码：

| 问题 | 选项 | 示例 |
|------|------|------|
| 认证方式 | Bearer / x-api-key + 版本头 / 无认证 | Anthropic 用 `x-api-key` + `anthropic-version` |
| 模型列表端点 | `GET /v1/models` / 其他路径 | Ollama 用 `GET /api/tags` |
| 响应数组键名 | `data` / `models` / 其他 | Ollama 返回 `{ models: [...] }` |
| 默认 base URL | `https://api.xxx.com` | DeepSeek 是 `https://api.deepseek.com` |
| 与哪个现有 Adapter 同协议 | OpenAI / Anthropic / Ollama / 全新 | 全新协议需新建 Adapter |

**给用户确认的结论格式**：

```
新供应商：GEMINI
认证：Bearer Token（Authorization: Bearer {key}）
模型端点：GET /v1/beta/models（响应键名 models）
默认 base URL：https://generativelanguage.googleapis.com
协议分组：全新协议，需新建 GeminiAdapter
```

---

## 阶段 1：判断改动范围

```
与 OpenAI 同协议（Bearer + /v1/models + "data" 键）
    → 直接在 ProviderAdapterFactory.getAdapter() 的 switch 加一行
    → 指向 openAiAdapter 或 openAiCompatibleAdapter，无需新建 Adapter

与 Anthropic / Ollama 同协议
    → 同上，Factory 加一行，复用现有 Adapter

全新协议
    → 新建 XxxAdapter extends AbstractProviderAdapter
    → Factory 注入 + switch 新增 case
```

---

## 阶段 2：后端实现

### 2a — 新建 Adapter（仅全新协议需要）

文件路径：`hify-model/src/main/java/com/hify/model/domain/adapter/GeminiAdapter.java`

```java
@Component
public class GeminiAdapter extends AbstractProviderAdapter {

    public GeminiAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }

    @Override
    public ConnectivityTestResult testConnection(ProviderPo provider) {
        String baseUrl  = resolveBaseUrl(provider);
        String apiKey   = extractApiKey(provider);
        Map<String, String> headers = new HashMap<>();
        if (!apiKey.isBlank()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        return doTest(baseUrl + "/v1/beta/models", headers, "models");
    }

    @Override
    public List<String> listModels(ProviderPo provider) {
        String baseUrl = resolveBaseUrl(provider);
        String apiKey  = extractApiKey(provider);
        Map<String, String> headers = new HashMap<>();
        if (!apiKey.isBlank()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        return doListModels(baseUrl + "/v1/beta/models", headers, "models", "name");
    }

    @Override
    protected String defaultBaseUrl() {
        return "https://generativelanguage.googleapis.com";
    }
}
```

### 2b — 注册到 Factory

文件路径：`hify-model/src/main/java/com/hify/model/domain/adapter/ProviderAdapterFactory.java`

```java
@Component
@RequiredArgsConstructor
public class ProviderAdapterFactory {

    private final OpenAiAdapter            openAiAdapter;
    private final AnthropicAdapter         anthropicAdapter;
    private final OllamaAdapter            ollamaAdapter;
    private final OpenAiCompatibleAdapter  openAiCompatibleAdapter;
    private final GeminiAdapter            geminiAdapter;          // ← 注入新 Adapter

    public ProviderAdapter getAdapter(String type) {
        return switch (type) {
            case "ANTHROPIC"         -> anthropicAdapter;
            case "OLLAMA"            -> ollamaAdapter;
            case "OPENAI_COMPATIBLE" -> openAiCompatibleAdapter;
            case "GEMINI"            -> geminiAdapter;             // ← 新增 case
            default                  -> openAiAdapter;
        };
    }
}
```

### 验证后端

```bash
cd /Users/lanzelot/workspace/hify && mvn install -DskipTests -q
mvn spring-boot:run -pl hify-app

# 创建新供应商
curl -X POST http://localhost:8080/api/v1/providers \
  -H "Content-Type: application/json" \
  -d '{"name":"Gemini Pro","type":"GEMINI","apiKey":"AIza-test"}'

# 手动触发连通性测试（用假 key 验证路由正确，预期 success=false + 认证失败，不是 500）
curl -X POST http://localhost:8080/api/v1/providers/{id}/test-connection
```

---

## 阶段 3：前端注册（两处）

### `src/api/provider.ts` — PROVIDER_TYPES

```typescript
export const PROVIDER_TYPES = [
  { label: 'OpenAI',            value: 'OPENAI'             },
  { label: 'Anthropic (Claude)', value: 'ANTHROPIC'         },
  { label: 'DeepSeek',          value: 'DEEPSEEK'           },
  { label: 'Ollama',            value: 'OLLAMA'             },
  { label: 'OpenAI 兼容',       value: 'OPENAI_COMPATIBLE'  },
  { label: 'Gemini',            value: 'GEMINI'             },  // ← 新增
]
```

### `src/views/provider/ProviderList.vue` — TYPE_LABEL + TYPE_TAG_TYPE

```typescript
const TYPE_LABEL: Record<string, string> = {
  // ... 现有条目 ...
  GEMINI: 'Gemini',   // ← 新增
}

const TYPE_TAG_TYPE: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
  // ... 现有条目 ...
  GEMINI: 'success',  // ← 从 primary/warning/success/info/danger 选一个
}
```

---

## 阶段 4：验收

```
后端：
  □ mvn install -DskipTests -q 编译通过
  □ 创建新供应商，返回 code=200
  □ POST /test-connection 用假 key → success=false + 认证错误（不是 500）
  □ 用真实 key → success=true + latencyMs + modelCount
  □ 等 ~60s，GET /{id} 中 healthStatus 已更新（定时任务已跑）

前端：
  □ 新增弹窗下拉能看到新供应商类型
  □ 列表类型列显示正确 tag 文字和颜色
  □ 点「测试」按钮，提示连通结果
```

---

## 决策速查

| 目标供应商协议 | 改动 |
|--------------|------|
| OpenAI 兼容（Bearer + /v1/models + `data`）| Factory switch 加一行，指向 `openAiCompatibleAdapter` |
| 与 Anthropic/Ollama 同协议 | Factory switch 加一行，复用对应 Adapter |
| 全新协议 | 新建 XxxAdapter（继承 AbstractProviderAdapter）+ Factory 注入 + switch case |
