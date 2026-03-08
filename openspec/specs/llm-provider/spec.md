# LLM Provider 抽象层规范

> 包名: `@mariozechner/pi-ai`

## 概述

`pi-ai` 是统一的 LLM API 抽象层，负责模型发现、provider 配置、流式事件标准化、token/成本统计，以及跨 provider 的上下文重放。

**核心原则**: 仅面向支持 tool calling 的模型，作为 agentic 工作流的基础层。

## 支持的提供商

### API Key 认证

| 提供商 | 环境变量 | 备注 |
|--------|----------|------|
| Anthropic | `ANTHROPIC_API_KEY` | Claude 系列 |
| OpenAI | `OPENAI_API_KEY` | GPT 系列 |
| Azure OpenAI | `AZURE_OPENAI_API_KEY` + `AZURE_OPENAI_BASE_URL` 或 `AZURE_OPENAI_RESOURCE_NAME` | Responses API |
| Google | `GEMINI_API_KEY` | Gemini 系列 |
| Google Vertex | `GOOGLE_CLOUD_PROJECT` / `GCLOUD_PROJECT` + `GOOGLE_CLOUD_LOCATION` + ADC | Gemini via Vertex AI |
| Amazon Bedrock | AWS 凭证 | 需配置 region |
| Mistral | `MISTRAL_API_KEY` | OpenAI-compatible |
| Groq | `GROQ_API_KEY` | OpenAI-compatible |
| Cerebras | `CEREBRAS_API_KEY` | OpenAI-compatible |
| xAI | `XAI_API_KEY` | OpenAI-compatible |
| OpenRouter | `OPENROUTER_API_KEY` | OpenAI-compatible 聚合层 |
| Vercel AI Gateway | `AI_GATEWAY_API_KEY` | OpenAI-compatible 网关 |
| zAI | `ZAI_API_KEY` | OpenAI-compatible，GLM 系列 |
| MiniMax | `MINIMAX_API_KEY` | OpenAI-compatible |
| Kimi For Coding | `KIMI_API_KEY` | Anthropic-compatible |
| GitHub Copilot | `COPILOT_GITHUB_TOKEN` / `GH_TOKEN` / `GITHUB_TOKEN` | 也可通过 OAuth 获取 |

### OAuth 订阅认证

- Anthropic Claude Pro/Max
- OpenAI ChatGPT Plus/Pro (Codex)
- GitHub Copilot
- Google Gemini CLI (Cloud Code Assist)
- Google Antigravity

说明:
- provider 不等于底层 API 实现文件。
- `Mistral`、`Groq`、`Cerebras`、`xAI`、`OpenRouter`、`Vercel AI Gateway`、`zAI`、`MiniMax` 等多数通过 `openai-completions` 兼容层接入。
- `Ollama`、`vLLM`、`LM Studio` 等任意 OpenAI-compatible 端点通常通过自定义 `Model<'openai-completions'>` 接入。

## 核心概念

### KnownApi / KnownProvider

```typescript
type KnownApi =
  | "openai-completions"
  | "openai-responses"
  | "azure-openai-responses"
  | "openai-codex-responses"
  | "anthropic-messages"
  | "bedrock-converse-stream"
  | "google-generative-ai"
  | "google-gemini-cli"
  | "google-vertex";

type KnownProvider =
  | "amazon-bedrock"
  | "anthropic"
  | "google"
  | "google-gemini-cli"
  | "google-antigravity"
  | "google-vertex"
  | "openai"
  | "azure-openai-responses"
  | "openai-codex"
  | "github-copilot"
  | "xai"
  | "groq"
  | "cerebras"
  | "openrouter"
  | "vercel-ai-gateway"
  | "zai"
  | "mistral"
  | "minimax"
  | "minimax-cn"
  | "huggingface"
  | "opencode"
  | "kimi-coding";
```

### Model

```typescript
interface Model<ApiType extends Api> {
  id: string;
  name: string;
  api: ApiType;
  provider: Provider;
  baseUrl: string;
  reasoning: boolean;
  input: ("text" | "image")[];
  cost: {
    input: number;
    output: number;
    cacheRead: number;
    cacheWrite: number;
  };
  contextWindow: number;
  maxTokens: number;
  headers?: Record<string, string>;
  compat?: ApiType extends "openai-completions"
    ? OpenAICompletionsCompat
    : ApiType extends "openai-responses"
      ? OpenAIResponsesCompat
      : never;
}
```

### Context

```typescript
interface Context {
  systemPrompt?: string;
  messages: Message[];
  tools?: Tool[];
}
```

### StreamOptions / SimpleStreamOptions

```typescript
interface StreamOptions {
  temperature?: number;
  maxTokens?: number;
  signal?: AbortSignal;
  apiKey?: string;
  transport?: "sse" | "websocket" | "auto";
  cacheRetention?: "none" | "short" | "long";
  sessionId?: string;
  onPayload?: (payload: unknown) => void;
  headers?: Record<string, string>;
  maxRetryDelayMs?: number;
  metadata?: Record<string, unknown>;
}

interface SimpleStreamOptions extends StreamOptions {
  reasoning?: "minimal" | "low" | "medium" | "high" | "xhigh";
  thinkingBudgets?: {
    minimal?: number;
    low?: number;
    medium?: number;
    high?: number;
  };
}
```

### AssistantMessageEvent

统一事件流:
- `start`
- `text_start` / `text_delta` / `text_end`
- `thinking_start` / `thinking_delta` / `thinking_end`
- `toolcall_start` / `toolcall_delta` / `toolcall_end`
- `done`
- `error`

## API 参考

### 流式调用

```typescript
import { getModel, streamSimple, type Context } from '@mariozechner/pi-ai';

const model = getModel('anthropic', 'claude-sonnet-4-20250514');
const context: Context = {
  systemPrompt: 'You are a helpful assistant.',
  messages: [{ role: 'user', content: 'Hello!', timestamp: Date.now() }],
};

for await (const event of streamSimple(model, context, { reasoning: 'medium' })) {
  if (event.type === 'text_delta') {
    process.stdout.write(event.delta);
  }
}
```

### 工具定义

```typescript
import { Type, type Tool } from '@mariozechner/pi-ai';

const myTool: Tool = {
  name: 'get_weather',
  description: 'Get current weather for a location',
  parameters: Type.Object({
    location: Type.String({ description: 'City name' }),
  }),
};
```

### 跨提供商切换

```typescript
import { complete, getModel, type Context } from '@mariozechner/pi-ai';

const anthropicModel = getModel('anthropic', 'claude-sonnet-4-20250514');
const openaiModel = getModel('openai', 'gpt-4o');

const context: Context = {
  messages: [{ role: 'user', content: 'Summarize this repo', timestamp: Date.now() }],
};

const first = await complete(anthropicModel, context);
context.messages.push(first);

const second = await complete(openaiModel, context);
```

### 上下文序列化

```typescript
import type { Context } from '@mariozechner/pi-ai';

const context: Context = {
  messages: [{ role: 'user', content: 'Hello', timestamp: Date.now() }],
};

const serialized = JSON.stringify(context);
const restored = JSON.parse(serialized) as Context;
```

## 消息类型

### Message

```typescript
type Message =
  | UserMessage
  | AssistantMessage
  | ToolResultMessage;

interface UserMessage {
  role: 'user';
  content: string | (TextContent | ImageContent)[];
  timestamp: number;
}

interface AssistantMessage {
  role: 'assistant';
  content: (TextContent | ThinkingContent | ToolCall)[];
  api: Api;
  provider: Provider;
  model: string;
  usage: Usage;
  stopReason: StopReason;
  errorMessage?: string;
  timestamp: number;
}

interface ToolResultMessage {
  role: 'toolResult';
  toolCallId: string;
  toolName: string;
  content: (TextContent | ImageContent)[];
  details?: unknown;
  isError: boolean;
  timestamp: number;
}
```

## 错误处理

### 中止请求

```typescript
import { streamSimple } from '@mariozechner/pi-ai';

const controller = new AbortController();

const s = streamSimple(model, context, {
  signal: controller.signal,
});

controller.abort();
```

### 中止后继续

```typescript
// pi-ai 不提供 agent-level continue()。
// 上层应用保存当前 Context 和部分 AssistantMessage 后，自行决定是否重试或继续。
const partial = await s.result();

if (partial.stopReason === 'aborted') {
  context.messages.push(partial);
  // 稍后使用同一个 context 再次调用 complete()/stream()/streamSimple()
}
```

## 添加新提供商

需要修改的文件：

1. **`src/types.ts`** - 添加 `Api` / `KnownProvider` / options 类型
2. **`src/providers/`** - 实现 `stream<Provider>()` 和 `streamSimple<Provider>()`
3. **`src/providers/register-builtins.ts`** - 通过 `registerApiProvider()` 注册
4. **`src/env-api-keys.ts`** - 添加环境变量或凭证检测
5. **`scripts/generate-models.ts`** - 接入模型发现逻辑
6. **`test/`** - 补充 abort、tool calling、handoff、serialization 等测试

## 扩展点

### 自定义模型配置

通过代码直接构造 `Model` 对象接入自定义 endpoint：

```typescript
const model: Model<'openai-completions'> = {
  id: 'my-model',
  name: 'My Model',
  api: 'openai-completions',
  provider: 'custom-proxy',
  baseUrl: 'http://localhost:8000/v1',
  reasoning: false,
  input: ['text'],
  cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },
  contextWindow: 128000,
  maxTokens: 8192,
};
```

### 自定义 API / OAuth 提供商

- 新的底层 API 通过 `registerApiProvider()` 扩展
- 新的 OAuth 流程通过 OAuth provider registry 扩展

## 性能考虑

- **缓存**: 支持 prompt caching (Anthropic 1h, OpenAI 24h)
- **流式传输**: 默认使用 SSE，部分 provider 支持 WebSocket
- **兼容层**: OpenAI-compatible provider 通过 `compat` 覆盖差异

## 安全考虑

- API key 来自显式参数、环境变量或 OAuth 刷新流程
- OAuth token 由调用方持久化，库负责刷新与转换为可用 apiKey
- `onPayload` 和自定义 `headers` 会影响最终请求，应避免注入敏感调试信息
