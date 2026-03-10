# pi-java 交接文档

更新时间：2026-03-10

## 当前状态

`pi-java` 已从“纯设计文档目录”推进到“可运行的阶段 0 工程骨架 + 阶段 1 的前五种 `pi-ai` provider”。

本次工作只改动了 `pi-java/`，没有改动现有 TypeScript 包的实现逻辑。

## 已完成内容

### 1. 阶段 0：Gradle 多模块工程骨架

已新增并打通以下入口：

- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle.properties`
- `gradlew`
- `gradlew.bat`

已建立的模块目录：

- `modules/pi-ai`
- `modules/pi-agent-runtime`
- `modules/pi-session`
- `modules/pi-tools`
- `modules/pi-extension-spi`
- `modules/pi-tui`
- `modules/pi-cli`
- `modules/pi-sdk`

这些模块目前都能参与 Gradle 构建和测试，其中大部分仍是最小骨架/marker 状态。

### 2. 已导入的种子 fixture

已从现有 TypeScript 仓库复制两份最早可复用的 fixture，作为 Java 侧测试起点：

- `modules/pi-ai/src/test/resources/fixtures/ts/assistant-message-with-thinking-code.json`
- `modules/pi-session/src/test/resources/fixtures/ts/large-session.jsonl`

### 3. 阶段 1：`pi-ai` 核心类型首版

已在 `modules/pi-ai/src/main/java/dev/pi/ai/model/` 下补上第一批核心模型：

- `StreamOptions`
- `SimpleStreamOptions`
- `ThinkingLevel`
- `ThinkingBudgets`
- `CacheRetention`
- `Transport`
- `StopReason`
- `UserContent` / `AssistantContent`
- `TextContent`
- `ThinkingContent`
- `ImageContent`
- `ToolCall`
- `Usage`
- `Tool`
- `Message`
- `Context`
- `Model`
- `AssistantMessageEvent`

当前这批类型的实现特点：

- 使用 Java sealed interface / record 表达消息和事件层次。
- `api` / `provider` 当前用 `String` 保持开放性，避免过早锁死到 enum。
- 消息内容块和事件块保留了 TS 版的 `type` / `role` / `stopReason` 语义。
- `Tool.parametersSchema` 和 `ToolCall.arguments` 当前使用 Jackson `JsonNode`。

### 4. `pi-ai` 事件流骨架

已在 `modules/pi-ai/src/main/java/dev/pi/ai/stream/` 下新增：

- `Subscription`
- `EventStream`
- `AssistantMessageEventStream`

当前行为：

- 支持订阅事件。
- 新订阅者会回放历史事件，避免错过已发出的 `start` / 增量事件。
- `done` / `error` 事件都会完成 `result()`。
- `error` 不会把 `result()` 变成异常，而是返回错误态的 `AssistantMessage`，与现有 TS 语义一致。

### 5. 阶段 1：`pi-ai` control plane 首版

已在 `modules/pi-ai/src/main/java/dev/pi/ai/` 下补上第一批主干组件：

- `provider/ApiProvider`
- `registry/ApiProviderRegistry`
- `registry/ModelRegistry`
- `auth/CredentialResolver`
- `auth/CredentialSource`
- `auth/EnvironmentCredentialSource`
- `PiAiClient`

当前这批主干的实现特点：

- `PiAiClient` 已提供 `stream()` / `complete()` / `streamSimple()` / `completeSimple()` facade。
- provider 分发按 `model.api` 进行，缺失 provider 时会明确报错。
- 凭证解析按 `model.provider` 进行，显式 `apiKey` 优先，环境变量 resolver 作为 fallback。
- `ApiProviderRegistry` 支持按 `sourceId` 卸载，便于后续插件/扩展场景接入。
- `ModelRegistry` 已支持按 provider 维度注册、查询和列举模型。

### 6. 阶段 1：`AssistantMessage` partial assembler 首版

已在 `modules/pi-ai/src/main/java/dev/pi/ai/stream/AssistantMessageAssembler.java` 下补上最小 partial assembler。

当前这批 assembler 的实现特点：

- 以 `Model` 元信息初始化 partial assistant message。
- 支持 `start`、`text_*`、`thinking_*`、`toolcall_*`、`done`、`error` 事件的渐进组装。
- `toolcall_delta` 会累积 JSON 字符串，并在可解析时更新 `ToolCall.arguments`。
- 对 block 类型错配和未初始化索引会明确抛错，便于 provider contract test 直接定位问题。

### 7. 阶段 1：transport primitives 首版

已在 `modules/pi-ai/src/main/java/dev/pi/ai/stream/` 下补上第一批通用 transport 组件：

- `SseEvent`
- `SseEventParser`
- `WebSocketMessageEvent`
- `WebSocketStreamAdapter`

当前这批 transport 组件的实现特点：

- `SseEventParser` 支持增量解析 chunked 输入，兼容 `CRLF/LF`、注释行、`id`、`retry`、多行 `data`、EOF flush。
- `SseEventParser` 会保留 SSE 的 `lastEventId` / `retry` 语义，便于后续 provider 直接复用。
- `WebSocketStreamAdapter` 直接实现 `java.net.http.WebSocket.Listener`，可把 fragmented text / binary frame 归一成完整文本消息。
- `WebSocketStreamAdapter` 通过统一事件流暴露 `message` / `close` / `error`，后续 provider 可以直接订阅而不必自己管理 frame 拼接。

### 8. 阶段 1：`openai-responses` provider 首版

已在 `modules/pi-ai/src/main/java/dev/pi/ai/provider/openai/` 下补上第一版 `openai-responses` provider：

- `OpenAiResponsesProvider`
- `OpenAiResponsesTransport`
- `HttpOpenAiResponsesTransport`

当前这批 provider 代码的实现特点：

- `OpenAiResponsesProvider` 已实现 `stream()` / `streamSimple()`，并通过 `HttpClient + SSE` 路径读取 OpenAI Responses API 流。
- 请求体已支持 `systemPrompt`、`user text/image`、`assistant text/thinking/toolCall`、`toolResult`、`tools`、`reasoning`、`prompt cache` 等基础字段映射。
- 响应流已支持 `reasoning`、`message`、`function_call` 三类 output item 的标准化事件映射，并复用 `AssistantMessageAssembler` 组装 partial/final assistant message。
- 当前 contract test 使用 fixture + fake transport 驱动，不依赖真实网络请求。
- `AssistantMessageAssembler` 新增了 tool call final-arguments 覆写入口，便于 provider 在 `function_call_arguments.done` / `output_item.done` 阶段做最终归一化。

### 9. 阶段 1：`openai-completions` provider 首版

已在 `modules/pi-ai/src/main/java/dev/pi/ai/provider/openai/` 下补上第一版 `openai-completions` provider：

- `OpenAiCompletionsProvider`
- `OpenAiCompletionsTransport`
- `HttpOpenAiCompletionsTransport`

当前这批 provider 代码的实现特点：

- `OpenAiCompletionsProvider` 已实现 `stream()` / `streamSimple()`，并通过 `HttpClient + SSE` 路径读取 Chat Completions 流。
- 请求体已支持 `systemPrompt`、`user text/image`、`assistant text/thinking/toolCall`、`toolResult`、`tools`、`temperature`、`reasoning_effort`、`max_completion_tokens` 等基础字段映射。
- 兼容层已覆盖 `supportsStore`、`supportsDeveloperRole`、`supportsReasoningEffort`、`maxTokensField`、`requiresToolResultName`、`requiresThinkingAsText`、`requiresMistralToolIds`、`thinkingFormat` 等关键差异。
- 响应流已支持 `content`、`reasoning_content`、`tool_calls`、`usage`、`finish_reason` 的标准化事件映射，并复用 `AssistantMessageAssembler` 组装 partial/final assistant message。
- 当前 contract test 使用 fixture + fake transport 驱动，不依赖真实网络请求。

### 10. 阶段 1：`anthropic-messages` provider 首版

已在 `modules/pi-ai/src/main/java/dev/pi/ai/provider/anthropic/` 下补上第一版 `anthropic-messages` provider：

- `AnthropicMessagesProvider`
- `AnthropicMessagesTransport`
- `HttpAnthropicMessagesTransport`

当前这批 provider 代码的实现特点：

- `AnthropicMessagesProvider` 已实现 `stream()` / `streamSimple()`，并通过 `HttpClient + SSE` 路径读取 Anthropic Messages 流。
- 请求体已支持 `systemPrompt`、`user text/image`、`assistant text/thinking/toolCall`、`toolResult`、`tools`、`cache_control`、`thinking`、`output_config.effort`、`metadata.user_id` 等基础字段映射。
- `streamSimple()` 已支持 Anthropic 的两种 reasoning 路径：自适应 thinking（Opus/Sonnet 4.6）和 budget-based thinking（旧模型）。
- 消息回放前已补了最小 compat 变换：跨模型 thinking 降级、tool call id 归一化、跳过 error/aborted assistant turn、缺失 tool result 的 synthetic 填充。
- 响应流已支持 `message_start`、`content_block_*`、`message_delta`、`message_stop`、`error` 的标准化事件映射，并复用 `AssistantMessageAssembler` 组装 partial/final assistant message。
- 当前 contract test 使用 fixture + fake transport 驱动，不依赖真实网络请求。

### 11. 阶段 1：`google-generative-ai` provider 首版

已在 `modules/pi-ai/src/main/java/dev/pi/ai/provider/google/` 下补上第一版 `google-generative-ai` provider：

- `GoogleGenerativeAiProvider`
- `GoogleGenerativeAiTransport`
- `HttpGoogleGenerativeAiTransport`

当前这批 provider 代码的实现特点：

- `GoogleGenerativeAiProvider` 已实现 `stream()` / `streamSimple()`，并通过 `HttpClient + SSE` 路径读取 Gemini API 流。
- 请求体已支持 `systemInstruction`、`user text/image`、`assistant text/thinking/toolCall`、`toolResult`、`tools`、`generationConfig.temperature`、`generationConfig.maxOutputTokens`、`generationConfig.thinkingConfig` 等基础字段映射。
- `streamSimple()` 已支持 Gemini 3 的 `thinkingLevel` 路径，以及 Gemini 2.5 的 budget-based thinking 路径。
- 消息回放前已补了最小 compat 变换：跨模型 thinking 降级、历史 thought signature 保留、Gemini 3 下 unsigned historical tool call 降级为文本提示、缺失 tool result 的 synthetic 填充。
- 响应流已支持 `thinking`、`text`、`functionCall` 的标准化事件映射，并复用 `AssistantMessageAssembler` 组装 partial/final assistant message。
- 当前 contract test 使用 fixture + fake transport 驱动，不依赖真实网络请求。

### 12. 阶段 1：`bedrock-converse-stream` provider 首版

已在 `modules/pi-ai/src/main/java/dev/pi/ai/provider/bedrock/` 下补上第一版 `bedrock-converse-stream` provider：

- `BedrockConverseStreamProvider`
- `BedrockConverseStreamTransport`
- `AwsBedrockConverseStreamTransport`

当前这批 provider 代码的实现特点：

- `BedrockConverseStreamProvider` 已实现 `stream()` / `streamSimple()`，并通过 AWS SDK Java v2 的 `BedrockRuntimeAsyncClient` 读取 ConverseStream。
- 请求体已支持 `systemPrompt`、`user text/image`、`assistant text/thinking/toolCall`、`toolResult`、`tools`、`inferenceConfig`、`additionalModelRequestFields` 等基础字段映射。
- `streamSimple()` 已支持 Bedrock 上 Anthropic Claude 模型的两种 reasoning 路径：自适应 thinking（4.6 模型）和 budget-based thinking（旧 Claude 模型）。
- 消息回放前已补了最小 compat 变换：跨模型 thinking 降级、tool call id 归一化、跳过 error/aborted assistant turn、缺失 tool result 的 synthetic 填充。
- 响应流已支持 `messageStart`、`contentBlockStart`、`contentBlockDelta`、`contentBlockStop`、`messageStop`、`metadata` 的标准化事件映射，并复用 `AssistantMessageAssembler` 组装 partial/final assistant message。
- 当前 contract test 使用 fixture + fake transport 驱动，不依赖真实网络请求。
- 受当前锁定的 AWS SDK 版本 `2.30.38` 限制，真实 transport 暂未把 `cachePoint` 与 cache read/write usage 字段下沉到 SDK 请求/响应；provider payload 与测试已保留这层语义，后续若需要完整运行时对齐，建议先升级 AWS SDK。

### 13. 阶段 1：`message transform / validation / compat` 抽象层首版

已在 `modules/pi-ai/src/main/java/dev/pi/ai/provider/MessageHistoryCompat.java` 下补上第一版公共 replay/compat 层。

当前这批抽象层代码的实现特点：

- `MessageHistoryCompat.transformMessages()` 统一承接历史消息回放前的两段式处理：assistant/tool-result 兼容变换、缺失 tool result 的 synthetic 填充。
- 公共层已统一处理 `error` / `aborted` assistant turn 跳过、pending tool call 追踪、tool result id remap、缺失 tool result 自动补齐。
- `CompatContext` 提供 `sameProviderAndModel()`、`normalizeToolCallId()`、`remapToolCallId()`，把 provider 的兼容策略和 replay 管线解耦。
- `MessageHistoryCompat.rebuildAssistantMessage()` 统一重建 assistant message，减少各 provider 内重复 record 拷贝逻辑。
- 目前 `AnthropicMessagesProvider`、`GoogleGenerativeAiProvider`、`BedrockConverseStreamProvider` 已切到这层公共 compat/replay 逻辑。
- 当前还没有把 OpenAI 两条 provider 线也并入这层；它们的 compat 规则更细，适合在下一轮统一抽象。

## 已完成的验证

已通过的命令：

```bash
.\gradlew.bat :pi-ai:test --no-daemon
npm.cmd run check
```

其中 `pi-ai` 新增测试覆盖了：

- `SimpleStreamOptions` builder 行为
- `AssistantMessage` 的 JSON round-trip
- `AssistantMessageEventStream` 对 `done` / `error` 的终结语义
- `ApiProviderRegistry` 的注册 / 查询 / source 卸载
- `ModelRegistry` 的注册 / 查询
- `CredentialResolver` 的 source 顺序、环境变量映射、显式 `apiKey` 优先级
- `PiAiClient` 的 facade 分发与凭证注入
- `AssistantMessageAssembler` 的 text / thinking / toolcall 组装与 error 终结语义
- `SseEventParser` 的 chunk / multiline / retry / EOF flush 解析语义
- `WebSocketStreamAdapter` 的 fragmented text / binary frame 归并与 close/error 终结语义
- `openai-responses` provider 的 payload 构造、SSE event 映射、tool call / reasoning / usage 归一化、error 终结语义
- `openai-completions` provider 的 payload 构造、SSE chunk 映射、tool call / reasoning / usage 归一化、error 终结语义
- `anthropic-messages` provider 的 payload 构造、SSE event 映射、adaptive/budget thinking、tool call / reasoning / usage 归一化、error 终结语义
- `google-generative-ai` provider 的 payload 构造、SSE event 映射、Gemini 3 thinking level / Gemini 2.5 budget thinking、tool call / reasoning / usage 归一化、error 终结语义
- `bedrock-converse-stream` provider 的 payload 构造、ConverseStream event 映射、Claude adaptive/budget thinking、tool call / reasoning / usage 归一化、error 终结语义
- `message transform / validation / compat` 公共 replay 层、tool result synthetic 填充、tool call id remap、assistant turn 跳过规则

对应测试文件：

- `modules/pi-ai/src/test/java/dev/pi/ai/model/SimpleStreamOptionsTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/model/AssistantMessageJsonTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/stream/AssistantMessageEventStreamTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/registry/ApiProviderRegistryTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/registry/ModelRegistryTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/auth/CredentialResolverTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/PiAiClientTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/stream/AssistantMessageAssemblerTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/stream/SseEventParserTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/stream/WebSocketStreamAdapterTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/provider/openai/OpenAiResponsesProviderTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/provider/openai/OpenAiCompletionsProviderTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/provider/anthropic/AnthropicMessagesProviderTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/provider/google/GoogleGenerativeAiProviderTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/provider/bedrock/BedrockConverseStreamProviderTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/provider/MessageHistoryCompatTest.java`

## 未完成 / 已知缺口

### `pi-ai`

以下内容还没开始或只完成了骨架：

- provider 交叉行为测试矩阵（`abort` / `handoff` / `image input` / cross-provider parity）

### 其他模块

以下模块目前还只是最小工程骨架，尚未开始功能实现：

- `pi-agent-runtime`
- `pi-session`
- `pi-tools`
- `pi-extension-spi`
- `pi-tui`
- `pi-cli`
- `pi-sdk`

## 当前环境状态

当前仓库检查已经可以通过，但前置条件需要写清楚。

已验证通过：

```bash
npm.cmd ci --ignore-scripts
npx.cmd tsgo -p tsconfig.build.json   # packages/ai
npx.cmd tsgo -p tsconfig.build.json   # packages/agent
npx.cmd tsgo -p tsconfig.build.json   # packages/web-ui
npm.cmd run check
```

说明：

- `npm.cmd ci` 直接执行会在当前 Windows 环境卡在 `canvas` 原生编译。
- 使用 `npm.cmd ci --ignore-scripts` 可以先把静态检查需要的 Node 依赖装齐。
- 由于根 `check` 会进入 `packages/web-ui` 及其 example，需要先为 `packages/ai`、`packages/agent`、`packages/web-ui` 产出声明文件。
- 在当前环境里，上述前置步骤完成后，`npm.cmd run check` 已通过。

## 建议下一步

建议严格按 `docs/tasks.md` 的顺序继续：

1. 先回补 `pi-ai` 的 `abort` / `handoff` / `image input` / cross-provider 测试矩阵。
2. 然后评估是否把 OpenAI 两条 provider 线也并入公共 compat/replay 抽象。
3. 再进入 `pi-agent-runtime`，先补 core types 与 loop skeleton。

更具体的下一步切片建议：

1. `pi-ai`：provider 交叉行为测试矩阵。
2. `pi-ai`：OpenAI compat/replay 抽象收敛。
3. `pi-agent-runtime`：core types + loop skeleton。

并行拆分文档入口：

- `docs/subtasks/README.md`

## 交接注意事项

- `pi-java` 当前进度已提交并推送到 GitHub。
- `pi-java/README.md` 已链接 `handoff.md` 和 `docs/subtasks/README.md`。
- `pi-java` 下的 `.gradle/` 和 `build/` 已通过本地 `.gitignore` 忽略。
