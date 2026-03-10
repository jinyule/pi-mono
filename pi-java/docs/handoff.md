# pi-java 交接文档

更新时间：2026-03-10

## 当前状态

`pi-java` 已从“纯设计文档目录”推进到“可运行的阶段 0 工程骨架 + 已收尾的阶段 1 `pi-ai` + 已收尾的阶段 2 `pi-agent-runtime` + 已收尾的阶段 3 `pi-session` + 已收尾的阶段 4 `pi-tools` + 已启动的阶段 5 `pi-extension-spi`”。

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
- 目前 `OpenAiResponsesProvider`、`OpenAiCompletionsProvider`、`AnthropicMessagesProvider`、`GoogleGenerativeAiProvider`、`BedrockConverseStreamProvider` 都已切到这层 compat/replay 语义。
- OpenAI 两条 provider 线额外补了各自的 provider-specific tool call id normalize / replay 兼容规则；公共层保留统一 synthetic tool result 与 aborted skip 管线。

### 14. 阶段 1：provider 交叉行为测试矩阵首版

已在 `modules/pi-ai/src/test/java/dev/pi/ai/provider/ProviderBehaviorMatrixTest.java` 下补上第一版 provider 交叉行为测试矩阵。

当前这批矩阵测试覆盖的行为：

- `openai-responses`、`openai-completions`、`anthropic-messages`、`google-generative-ai`、`bedrock-converse-stream` 五条 provider 线统一验证 `abort` -> `StopReason.ABORTED` 终结语义。
- 统一验证 cross-provider handoff replay：缺失 tool result 会被 synthetic 补齐，aborted assistant turn 不会进入后续请求 payload。
- 统一验证 image input replay：provider request payload 会保留用户图片输入，并按各自协议落到 `input_image` / `image_url` / `inlineData` / Bedrock image block。
- 统一验证 OpenAI 两条 provider 线已接到公共 compat/replay 语义：现在也会跳过 `error` / `aborted` assistant turn，并在 user follow-up 前补 `No result provided` synthetic tool result。

本次顺手收敛的实现点：

- `OpenAiResponsesProvider` 已补 `MessageHistoryCompat` 接线，并补上 OpenAI Responses 风格的 tool call id normalize（`call_id|fc_id`）。
- `OpenAiResponsesProvider` 现在对 same-provider-different-model 的 historical function call 会跳过 `fc_*` item id 回放，避免 pairing 校验问题。
- `OpenAiCompletionsProvider` 已补 `MessageHistoryCompat` 接线，tool result synthetic 补齐与 aborted assistant skip 语义已追平 TypeScript 版。
- 现在五条 provider 线都走统一的 handoff/replay 测试入口，后续新增 provider 时可以直接往矩阵里挂。

### 15. 阶段 2：`pi-agent-runtime` core types + loop skeleton 首版

已在 `modules/pi-agent-runtime/src/main/java/dev/pi/agent/runtime/` 下补上第一版 runtime 核心类型与 loop 骨架：

- `AgentMessage`
- `AgentMessages`
- `AgentTool`
- `AgentToolResult`
- `AgentContext`
- `AgentState`
- `AgentEvent`
- `AgentEventStream`
- `AgentLoopConfig`
- `AgentLoop`

当前这批 runtime 代码的实现特点：

- `AgentMessage` 定义了 runtime 原生消息层级：`UserMessage`、`AssistantMessage`、`ToolResultMessage`、`CustomMessage`，与 `pi-ai` 的 `Message` 通过 `AgentMessages` 做双向映射。
- `AgentTool` / `AgentToolResult` 给后续顺序工具执行、参数校验和 tool result 注入预留了统一契约。
- `AgentEvent` 定义了 agent loop 的基础生命周期事件：`agent_start`、`agent_end`、`turn_start`、`turn_end`、`message_start`、`message_update`、`message_end`，并为后续工具执行事件预留了类型。
- `AgentEventStream` 复用 `pi-ai` 的事件流基础设施，结果值统一收敛为本轮新增的 `AgentMessage` 列表。
- `AgentLoopConfig` 已支持 `convertToLlm` 与 `transformContext` 两阶段上下文管线，支持 model、thinking、transport、headers、api key、stream function 等最小运行时配置。
- `AgentLoop` 已支持两条基础入口：`start()` 和 `continueLoop()`；当前可完成单轮 prompt -> provider stream -> assistant lifecycle 转发。
- `AgentLoop` 会把 `AssistantMessageEvent` 的 `start` / `text_*` / `thinking_*` / `toolcall_*` / `done` / `error` 事件桥接为 runtime 层的 `message_start` / `message_update` / `message_end`。
- 基于这版 skeleton，后续切片已继续补上顺序工具执行、steering / follow-up 队列与 multi-turn 续跑，详见后面的阶段 16 / 17 记录。

### 16. 阶段 2：顺序工具执行与参数校验首版

已在 `modules/pi-agent-runtime/src/main/java/dev/pi/agent/runtime/` 下补上第一版 runtime tool execution 管线：

- `ToolArgumentsValidator`
- `AgentLoop`（顺序 tool execution、多轮 assistant -> toolResult -> assistant 续跑）

当前这批 runtime 代码的实现特点：

- `AgentLoop` 现在会在 assistant message 中提取 `ToolCall`，按出现顺序逐个执行工具，而不是并发执行。
- tool 执行期间会稳定发出 `tool_execution_start`、`tool_execution_update`、`tool_execution_end` 三类 runtime event，并继续发出 `ToolResultMessage` 对应的 `message_start` / `message_end`。
- 每次 tool 执行结束后，都会把 `ToolResultMessage` 追加回当前上下文，并驱动下一轮 assistant 请求，实现最小可用的 multi-turn tool loop。
- tool 未找到、参数校验失败、执行抛错三类失败路径，当前都会统一收敛为 `ToolResultMessage(isError=true)`，与 TS 侧 agent loop 的错误承载方式保持一致。
- `ToolArgumentsValidator` 当前实现了基础 JSON Schema 子集：`type`、`required`、`properties`、`additionalProperties`、`items`、`enum`、`const`、`minimum/maximum`、`minLength/maxLength`，并对 `boolean` / `integer` / `number` 做了最小字符串 coercion。
- 当前这批参数校验还不是完整 JSON Schema 实现；复杂组合规则（如 `oneOf` / `anyOf` / `allOf`）留到后续确实需要时再补。

### 17. 阶段 2：steering / follow-up 队列首版

已继续在 `modules/pi-agent-runtime/src/main/java/dev/pi/agent/runtime/AgentLoop.java` 下补上第一版 queue 语义。

当前这批 runtime 代码的实现特点：

- `AgentLoop` 现在会在 loop 开始前先轮询一次 steering source，和 TypeScript 版保持一致，允许用户在 assistant 请求发出前插入待处理消息。
- 每次 tool 执行结束后，runtime 都会再次轮询 steering source；一旦拿到 steering message，会跳过当前 assistant 余下的 tool calls，并把 steering message 插入下一轮 assistant 请求前。
- 被跳过的 tool call 会稳定生成 `ToolResultMessage(isError=true)`，内容固定为 `Skipped due to queued user message.`，同时照常发出 `tool_execution_start` / `tool_execution_end` 与消息生命周期事件。
- 当 agent 在当前轮没有更多 tool call 且没有 steering message 时，runtime 会轮询 follow-up source；若拿到 follow-up message，则继续开启下一轮 turn，而不是直接结束 agent。
- 当前 queue 行为仍保持最小 runtime 语义：一次 `MessageSource.get()` 返回多少条消息，就在下一轮一并注入多少条；更高层的 one-at-a-time / all 模式留给后续 `Agent` facade 管理。

### 18. 阶段 2：`Agent` facade 与订阅接口首版

已在 `modules/pi-agent-runtime/src/main/java/dev/pi/agent/runtime/Agent.java` 下补上第一版高层 facade。

当前这批 facade 代码的实现特点：

- `Agent` 现在统一管理 `AgentState`、steering / follow-up 队列、运行中的 `AgentEventStream`、以及事件/状态订阅者。
- facade 已提供最小可用的高层入口：`prompt(String|AgentMessage|List<AgentMessage>)`、`resume()`、`steer()`、`followUp()`、`waitForIdle()`、`reset()`、消息/模型/工具等 state mutator。
- facade 会把 `AgentLoop` 发出的 runtime events 重新折叠进本地 `AgentState`：`message_start/update/end` 驱动 `streamMessage/messages`，`tool_execution_*` 驱动 `pendingToolCalls`，`agent_end` 驱动 `isStreaming=false`。
- 当前已补事件订阅与状态订阅两层接口：事件订阅只接收后续 runtime event；状态订阅会先收到当前快照，再接收后续 state 变化。
- `AgentState` 当前允许 `thinkingLevel=null` 表示不下发 reasoning，这样 Java facade 的默认行为才和 TS 侧的 `off` 对齐。
- 当时这版 facade 先只接好了本地状态与队列管理；后续阶段 19 已把 abort 的 assistant-stream 关闭链路补齐。

### 19. 阶段 2：interrupt / lifecycle tests 与 abort 收尾

已继续在以下文件补上 runtime 中断链路：

- `modules/pi-ai/src/main/java/dev/pi/ai/stream/EventStream.java`
- `modules/pi-agent-runtime/src/main/java/dev/pi/agent/runtime/AgentLoop.java`
- `modules/pi-agent-runtime/src/main/java/dev/pi/agent/runtime/Agent.java`

当前这批收尾代码的实现特点：

- `EventStream` 现在支持 `onClose(Runnable)`，允许外层 runtime 在流关闭时触发资源清理。
- `AgentLoop` 现在会把外层 `AgentEventStream.close()` 级联到当前内层 `AssistantMessageEventStream.close()`，避免 abort 后 loop 线程继续卡在 `assistantStream.result().join()`。
- `Agent.abort()` 现在不再只改本地 state；它会关闭运行中的 outer stream，随后由 facade 的取消路径合成一个 `stopReason=aborted` 的 assistant message，并补发 `message_start -> message_end -> turn_end -> agent_end` 生命周期。
- `waitForIdle()` 现在在 abort 场景下会稳定收敛，不会留下运行中的 facade future。
- 当前 abort 语义已经能覆盖 assistant streaming 阶段，但还不能主动取消已经开始执行的 Java tool；根因是当前 `AgentTool` API 还没有取消信号参数。这一限制已明确保留。

### 20. 阶段 3：`pi-session` model + JSONL parse/write skeleton 首版

已在 `modules/pi-session/src/main/java/dev/pi/session/` 下补上第一版 session 数据模型与 JSONL 编解码骨架：

- `SessionFileEntry`
- `SessionHeader`
- `SessionEntry`
- `SessionTreeNode`
- `SessionContext`
- `SessionDocument`
- `SessionJsonlCodec`

当前这批 `pi-session` 代码的实现特点：

- `SessionHeader` 已兼容当前 v3 头部字段，也兼容种子 fixture 里的 legacy header 字段：`provider`、`modelId`、`thinkingLevel`。
- `SessionEntry` 已覆盖 `message / thinking_level_change / model_change / compaction / branch_summary / custom / custom_message / label / session_info` 九类 body line。
- `message`、`custom`、`custom_message` 相关 payload 当前统一保留为 `JsonNode`，避免在 session 持久化层过早丢失 `custom` / `bashExecution` / legacy `hookMessage` 等非 LLM message 形状。
- `SessionContext` 当前先定义为“可送入 LLM 的稳定上下文”：`List<Message> + thinkingLevel + model`；后续 `buildSessionContext()` 会把 `compaction` / `branch_summary` / `custom_message` 语义折叠成最终消息列表。
- `SessionJsonlCodec` 已支持：
  - 逐行 JSONL 解析
  - 跳过 malformed / unknown line
  - header 校验后的 `SessionDocument` 装配
  - 稳定的 `writeLine()` / `writeLines()` / `writeDocument()`
  - 从文件读取和回写
- 当前 `writeDocument()` 固定输出 `\n` 结尾，和现有 JSONL 习惯保持一致。

这一刀的边界：

- 还没有实现 `v1 -> v2 -> v3` migration。
- 还没有实现 `buildSessionContext()` replay 逻辑。
- 还没有实现 `SessionManager` 与 `SettingsManager`。

### 21. 阶段 3：`pi-session` migration + replay 首版

已继续在 `modules/pi-session/src/main/java/dev/pi/session/` 下补上第一版 migration 与 replay 逻辑：

- `SessionMigrations`
- `SessionContexts`

当前这批 `pi-session` 代码的实现特点：

- `SessionMigrations` 已支持 `CURRENT_SESSION_VERSION=3`，并提供 `migrateToCurrentVersion()` 主入口。
- `v1 -> v2` 已覆盖：
  - 为 legacy entries 补 `id / parentId`
  - 将 `compaction.firstKeptEntryIndex` 迁移为 `firstKeptEntryId`
  - 把 header version 升到 `2`
- `v2 -> v3` 已覆盖：
  - 把 legacy `hookMessage` role 迁移为 `custom`
  - 把 header version 升到 `3`
- `SessionContexts.buildSessionContext()` 已支持：
  - 默认按当前 leaf 回放
  - 指定 `leafId` 时只走 root -> leaf 路径
  - `null leafId` 显式表示“before first entry”
  - `thinkingLevel` / `model` 按路径上最近一次变更收敛
  - compaction summary -> kept messages -> post-compaction messages 的 replay 顺序
  - `branch_summary` / `custom_message` 转成最终 LLM user message
  - legacy `bashExecution` / `custom` / `compactionSummary` / `branchSummary` message role 向 LLM message 语义收敛
- 当前 `SessionContext` 仍保持 `List<Message>` 目标形态，也就是已经是“可送给 LLM”的稳定上下文，而不是原始 app message 列表。

这一刀的边界：

- 还没有实现 `SessionManager`。
- 还没有实现 tree navigation / fork / persisted append/flush 节奏。
- 还没有实现 `SettingsManager`。

### 22. 阶段 3：`pi-session` `SessionManager` skeleton 首版

已继续在 `modules/pi-session/src/main/java/dev/pi/session/SessionManager.java` 下补上第一版 `SessionManager`。

当前这批 `pi-session` 代码的实现特点：

- 已支持 `inMemory()`、`create(Path, cwd)`、`open(Path)` 三条基础入口。
- 已支持 `appendMessage`、`appendRawMessage`、`appendThinkingLevelChange`、`appendModelChange`、`appendCompaction`、`appendBranchSummary`、`appendCustomEntry`、`appendCustomMessage`、`appendLabelChange`、`appendSessionInfo`。
- 已支持 `leafId` 跟踪、`navigate()`、`branch()`、`buildSessionContext()`、`tree()`、`entry()`、`label()` 等最小会话树操作。
- persistent 模式已补了最关键的 flush 节奏：
  - 仅有 user-side entries 时只保存在内存
  - 第一个 assistant entry 到来后一次性回写完整 JSONL
  - 后续 entry 走 append-only
- `open(Path)` 现在会自动跑 migration；如果读到 legacy session，会在打开时直接重写成 current version。

这一刀的边界：

- 还没有实现 fork / branched session file 提取。
- 还没有实现更完整的 persisted append/tree navigation contract tests。
- 还没有实现 `SettingsManager`。

### 23. 阶段 3：session tree / fork / persisted append contract tests + branched session file 提取

已继续在 `modules/pi-session/src/main/java/dev/pi/session/SessionManager.java` 与 `modules/pi-session/src/test/java/dev/pi/session/SessionManagerTest.java` 下补上第一版 fork / branch 收尾能力与合同测试。

当前这批 `pi-session` 代码的实现特点：

- `SessionManager` 已支持 `branchWithSummary()`，用于在指定 branch point 后插入 `branch_summary` entry，并把 leaf 切到新 summary 节点。
- `SessionManager.createBranchedSession(String)` 已支持从任意 leaf 提取 root -> leaf 路径，重写当前 manager 的 `SessionDocument / SessionHeader / sessionFile`，生成新的 branched session。
- 新 branched session 的 header 已补 `parentSession` 指向原 session file，便于后续上层做 fork 来源追踪。
- create-branched 过程会过滤旧 path 上的 `label` entry，再按保留路径重新生成 label entries，只保留路径上的最终 label，不把旁支 label 带进新 session。
- persistent fork 现在已支持两种 flush 节奏：
  - branched path 内还没有 assistant message 时，新的 session file 延迟到第一条 assistant message 才整体落盘；
  - branched path 已包含 assistant message 时，新的 session file 会立即写出完整 JSONL。
- 这批 contract tests 已覆盖：
  - tree branching 结构
  - `branchWithSummary()` 语义
  - in-memory fork 的路径裁剪与 label 保留
  - persisted fork 的 delayed flush / immediate flush
  - fork 后 JSONL 只有一个 header，entry id 不重复

这一刀的边界：

- 还没有实现 `SettingsManager`。
- 还没有实现 settings deep merge / lock / migration 语义。

### 24. 阶段 3：`Settings` / `SettingsManager` + deep merge / lock / migration tests

已继续在 `modules/pi-session/src/main/java/dev/pi/session/` 与 `modules/pi-session/src/test/java/dev/pi/session/SettingsManagerTest.java` 下补上第一版 settings layering 基础设施。

本次新增的入口：

- `Settings`
- `SettingsMigrations`
- `SettingsManager`
- `SettingsManagerTest`

当前这批 `pi-session` settings 代码的实现特点：

- `Settings` 目前以 `ObjectNode` 为底层承载，保留 `settings.json` 的开放 JSON 形状，而不是过早把整份配置锁成 Java field 列表。
- `Settings.merge()` 实现了递归 object deep merge；array 和 primitive 仍按 override 直接覆盖，语义与现有 TS 侧对齐。
- `SettingsManager` 已支持：
  - 全局：`~/.pi/agent/settings.json`
  - 项目：`.pi/settings.json`
  - `effective()` / `global()` / `project()`
  - `reload()`
  - `applyOverrides()`
  - `updateGlobal()` / `updateProject()`
  - `drainErrors()`
- file storage 现在通过两层锁保证稳定：
  - 进程内 `ConcurrentHashMap<Path, Object>` mutex，规避 Java `OverlappingFileLockException`
  - 进程间 `FileChannel.lock()` 文件锁，保证 cross-process read-modify-write 原子性
- `updateGlobal()` / `updateProject()` 都会在锁内重新读取磁盘上的最新 settings，再执行 update lambda 并写回，因此不会因为 manager 初始化较早而覆盖掉后续外部写入的无关字段。
- `SettingsMigrations` 当前已覆盖 TS 侧三条 legacy 迁移：
  - `queueMode -> steeringMode`
  - `websockets:boolean -> transport:sse|websocket`
  - 旧 `skills` object 形状 -> `skills[] + enableSkillCommands`
- `reload()` 当前会保留 last-known-good snapshot；如果磁盘文件损坏，只记录 error，不会把内存里的可用 settings 覆盖掉。

这批 contract tests 已覆盖：

- global/project/effective 的 deep merge 语义
- runtime-only overrides 语义
- legacy settings migration 与 modern-shape 持久化
- latest-on-disk merge 语义
- project settings 覆盖 global 语义
- 并发 scoped update 的 file lock 序列化
- reload parse error -> 保留上次成功快照 + `drainErrors()`

这一刀的边界：

- 还没有补齐 TypeScript `SettingsManager` 的全部 typed getter/setter surface；当前先以通用 JSON foundation 为主，后续按 CLI/runtime 消费点逐步加 typed facade。
- 还没有实现 `AGENTS.md` / `CLAUDE.md` / `SYSTEM.md` / `APPEND_SYSTEM.md` 资源装配。

### 25. 阶段 3：`AGENTS.md` / `CLAUDE.md` / `SYSTEM.md` / `APPEND_SYSTEM.md` 资源装配

已继续在 `modules/pi-session/src/main/java/dev/pi/session/` 与 `modules/pi-session/src/test/java/dev/pi/session/InstructionResourceLoaderTest.java` 下补上第一版 instruction resource loader。

本次新增的入口：

- `InstructionFile`
- `InstructionResources`
- `InstructionResourceLoader`
- `InstructionResourceLoaderTest`

当前这批资源装配代码的实现特点：

- `InstructionResourceLoader` 现在会把 resource assembly 限定在阶段 3 需要的四类输入：
  - 目录级上下文文件：`AGENTS.md` / `CLAUDE.md`
  - project/global system prompt：`SYSTEM.md`
  - project/global append system prompt：`APPEND_SYSTEM.md`
- context file 发现顺序已对齐现有 TS 语义：
  - 先加载 global `agentDir` 下的 `AGENTS.md|CLAUDE.md`
  - 再从文件系统 root -> cwd 方向加载祖先目录上下文文件
  - 同一个目录里优先 `AGENTS.md`，没有再退到 `CLAUDE.md`
  - 重复 path 会去重
- `SYSTEM.md` 和 `APPEND_SYSTEM.md` 的优先级已对齐 TS：
  - 先找项目级 `.pi/`
  - 项目不存在时再回退到 global `agentDir`
- `APPEND_SYSTEM.md` 当前按 TS 一致性先实现为“0 或 1 个 append prompt”；返回类型仍保留为 `List<String>`，为后续 extension/runtime append 扩展预留空间。
- loader 现在已支持 `reload()` 与 `drainErrors()`：
  - 读取失败的文件会被跳过
  - `IOException` 会进入 error buffer，避免因为单个不可读文件把整次 resource discovery 弄成 hard failure

这批 contract tests 已覆盖：

- reload 前的空状态
- global + ancestor context file 的稳定顺序
- 同目录 `AGENTS.md` 优先于 `CLAUDE.md`
- project `SYSTEM.md` 覆盖 global
- project `APPEND_SYSTEM.md` 覆盖 global
- 缺少 project 文件时回退 global

这一刀的边界：

- 目前只实现了 instruction/context 这条最小 resource assembly 线，还没有补技能、prompt templates、themes、extensions 的完整 Java 版 resource loader。
- 还没有做基于 settings 的 resource include/exclude、path metadata、collision diagnostics、热重载集成。

### 26. 阶段 4：`pi-tools` primitives

已继续在 `modules/pi-tools/src/main/java/dev/pi/tools/` 与 `modules/pi-tools/src/test/java/dev/pi/tools/` 下补上阶段 4 的第一批工具侧支撑类。

本次新增的入口：

- `TruncationLimit`
- `TruncationOptions`
- `TruncationResult`
- `LineTruncationResult`
- `TextTruncator`
- `ToolPaths`
- `EditDiffPreview`
- `EditDiffResult`
- `EditDiffError`
- `FuzzyMatchResult`
- `BomStrippedText`
- `EditDiffs`
- `ShellConfig`
- `ShellExecutionOptions`
- `ShellExecutionResult`
- `ShellExecutor`
- `Shells`
- `DefaultShellExecutor`
- `ImageResizeOptions`
- `ResizedImage`
- `ImageResizer`
- `TextTruncatorTest`
- `ToolPathsTest`
- `EditDiffsTest`
- `DefaultShellExecutorTest`
- `ImageResizerTest`

当前这批 primitives 的实现特点：

- truncation 已覆盖 head / tail 两种策略：
  - 独立处理 line limit 与 byte limit
  - head 截断时保持完整行，不回传 partial line
  - first line 单独超限时返回空内容并显式标记 `firstLineExceedsLimit`
  - tail 截断时保留 TS 一致的“超长单行只回最后一段”边界行为
  - `truncateLine()` 与 `formatSize()` 已补齐 grep / read / bash 侧通用格式化基础
- path policy 已覆盖 `@` / `~` 扩展与读路径兼容回退：
  - Unicode 空格归一化
  - 相对路径按 cwd 解析
  - `resolveReadPath()` 会尝试 macOS screenshot `AM/PM` 变体、NFD 变体、curly quote 变体及组合变体
- diff primitive 已覆盖 edit preview 需要的最小 contract：
  - line ending detect / normalize / restore
  - fuzzy match（trailing whitespace / smart quotes / dash / Unicode space 归一化）
  - UTF-8 BOM strip
  - 稳定的 line-number diff 文本生成
  - `computeEditDiff()` 的 not-found / duplicate / no-op 错误分支
- shell primitive 已提供后续 `bash` tool 需要的执行基础：
  - shell config 解析
  - binary/control char sanitize
  - timeout / cancel / process tree kill
  - 大输出 spill 到 temp file
  - rolling tail buffer + tail truncation
- image primitive 已提供后续 `read` tool 的图片输入基础：
  - JDK `ImageIO` 读取与 resize
  - PNG / JPEG 双格式尝试并选择更小产物
  - dimension note 生成
  - 不可读图片时回退原始 bytes，不抛 hard failure

这批 contract / unit tests 已覆盖：

- truncation 的 no-op、oversized first line、tail partial line、line truncation suffix、size formatting
- path expansion、cwd resolve、macOS screenshot / NFD / curly quote fallback
- diff 文本稳定性、fuzzy match、duplicate match 拒绝、BOM strip
- shell output streaming、temp file spill、tail truncation、timeout、sanitize 规则
- image resize 的 dimension limit、byte limit、dimension note、decode fallback

这一刀的边界：

- 目前只完成了工具支撑层，还没有开始 `read` / `write` / `edit` / `bash` / `grep` / `find` / `ls` 的实际 tool 实现。
- shell env 目前只做了基础 environment merge，还没有接 CLI 侧 bin-dir prepend 或 tool-specific command prefix。
- diff 目前服务于 edit preview / exact replacement primitive，还没有连到具体 tool result 文案与 golden output。

### 27. 阶段 4：`read` tool

已继续在 `modules/pi-tools/src/main/java/dev/pi/tools/` 与 `modules/pi-tools/src/test/java/dev/pi/tools/ReadToolTest.java` 下补上第一版 `read` tool。

本次新增的入口：

- `ReadTool`
- `ReadToolOptions`
- `ReadOperations`
- `ReadToolDetails`
- `SupportedImageMimeTypes`
- `ReadToolTest`

当前这版 `read` 的实现特点：

- 已接到 `AgentTool` contract：
  - `parametersSchema()` 暴露 `path` / `offset` / `limit`
  - `execute()` 返回 `AgentToolResult<ReadToolDetails>`
- 文本读取语义已对齐当前 TS contract：
  - 默认从头读取全文
  - `offset` 按 1-based line index 处理
  - `limit` 只裁用户请求窗口，不额外写 details
  - line truncation 与 byte truncation 会追加可继续读取的 offset 提示
  - 单行超出 `50KB` 时返回 bash 提示，不回传 partial line
- 图片读取语义已对齐当前 TS contract：
  - 按文件魔数识别 `png` / `jpeg` / `gif` / `webp`
  - 不依赖文件扩展名
  - 返回顺序固定为 `text` note + `image` block
  - 默认接入 `ImageResizer`，可通过 `ReadToolOptions.autoResizeImages=false` 关闭
- 路径解析已复用阶段 4 primitives：
  - `resolveReadPath()` 支持 `@` / `~`
  - 支持 macOS screenshot / NFD / curly quote fallback
- details 当前只在真正发生 truncation 时返回：
  - `ReadToolDetails.truncation`
  - 可直接序列化到 runtime 的 `ToolResultMessage.details`

这批 contract tests 已覆盖：

- 文本文件正常读取
- 缺失文件错误
- line limit / byte limit 截断提示
- `offset`
- `limit`
- `offset + limit`
- offset 越界错误
- oversized single line -> bash hint
- image magic MIME detect
- image extension 但非图片内容时按文本处理
- auto-resize 开关

这一刀的边界：

- 还没有开始 `write` / `edit` / `bash` / `grep` / `find` / `ls` 的实际 tool 实现。
- 还没有补 `read` tool 的 golden fixture / 真实 TS 输出对拍。
- 还没有处理更复杂的二进制文本检测与编码推断；当前文本读取固定按 UTF-8。

### 28. 阶段 4：`write` tool

已继续在 `modules/pi-tools/src/main/java/dev/pi/tools/` 与 `modules/pi-tools/src/test/java/dev/pi/tools/WriteToolTest.java` 下补上第一版 `write` tool。

本次新增的入口：

- `WriteTool`
- `WriteToolOptions`
- `WriteOperations`
- `WriteToolTest`

当前这版 `write` 的实现特点：

- 已接到 `AgentTool` contract：
  - `parametersSchema()` 暴露 `path` / `content`
  - `execute()` 返回 `AgentToolResult<Void>`
- 写入语义已对齐当前 TS contract：
  - 相对路径按 cwd 解析
  - 写入前自动创建父目录
  - 已存在文件直接覆盖
  - 成功消息使用 `Successfully wrote {content.length} bytes to {path}` 文案
- 当前实现保留了后续扩展点：
  - `WriteOperations` 可替换为远端文件系统
  - `WriteToolOptions` 已包住可注入操作集

这批 contract tests 已覆盖：

- 正常写文件
- 自动创建父目录
- 相对路径按 cwd 写入

这一刀的边界：

- 还没有开始 `edit` / `bash` / `grep` / `find` / `ls` 的实际 tool 实现。
- 还没有补 `write` tool 的 golden fixture / TS 输出对拍。
- 当前成功文案只覆盖最小 TS contract，还没有扩展 write-side metadata/details。

### 29. 阶段 4：`edit` tool

已继续在 `modules/pi-tools/src/main/java/dev/pi/tools/` 与 `modules/pi-tools/src/test/java/dev/pi/tools/EditToolTest.java` 下补上第一版 `edit` tool。

本次新增的入口：

- `EditTool`
- `EditToolOptions`
- `EditOperations`
- `EditToolDetails`
- `EditToolTest`

当前这版 `edit` 的实现特点：

- 已接到 `AgentTool` contract：
  - `parametersSchema()` 暴露 `path` / `oldText` / `newText`
  - `execute()` 返回 `AgentToolResult<EditToolDetails>`
- 编辑语义已对齐当前 TS contract：
  - 相对路径按 cwd 解析
  - 先校验文件可读写，不存在时返回 `File not found`
  - 先做 exact match，失败后再做 fuzzy match
  - duplicate match 会拒绝执行
  - no-op replacement 会拒绝执行
- 已复用阶段 4 的 diff primitives，并把 Java 侧 fuzzy 归一化细节修正到与 TS 一致：
  - trailing whitespace per-line trim
  - smart quotes / Unicode dash / Unicode spaces 归一化
  - 保留末尾空行语义，避免 fuzzy replacement 多插一行
  - 保留原文件的 `CRLF/LF` line ending 与 UTF-8 BOM
- tool result details 当前对齐最小 TS contract：
  - `EditToolDetails.diff`
  - `EditToolDetails.firstChangedLine`

这批 contract tests 已覆盖：

- 正常替换文本
- text not found 错误
- duplicate match 错误
- trailing whitespace fuzzy match
- CRLF 文件编辑后保持 `CRLF`
- UTF-8 BOM 保留

这一刀的边界：

- 还没有开始 `bash` / `grep` / `find` / `ls` 的实际 tool 实现。
- 还没有补 `edit` tool 的 golden fixture / TS 输出对拍。
- 当前 `edit` 仍是单次整文件读写；后续若要追平更复杂的 remote filesystem 场景，再在 `EditOperations` 上扩展即可。

### 30. 阶段 4：`bash` tool

已继续在 `modules/pi-tools/src/main/java/dev/pi/tools/`、`modules/pi-tools/src/test/java/dev/pi/tools/BashToolTest.java` 与 `modules/pi-agent-runtime/src/test/java/dev/pi/agent/runtime/AgentLoopCancellationTest.java` 下补上第一版 `bash` tool，并把 tool cancel 信号从 runtime 接到了 tool 执行层。

本次新增的入口：

- `BashTool`
- `BashToolOptions`
- `BashToolDetails`
- `BashSpawnContext`
- `BashSpawnHook`
- `BashToolTest`
- `AgentLoopCancellationTest`

本次补的 runtime / primitive 收敛点：

- `AgentTool` 新增 cancellation-aware overload，旧工具实现保持兼容
- `AgentLoop` 现在会把 `AgentEventStream.close()` 变成 tool-side cancel supplier
- `DefaultShellExecutor` 现在会：
  - 缺失 cwd 时给出稳定错误文案
  - 把最终 `TruncationResult` 一并回传给上层 tool

当前这版 `bash` 的实现特点：

- 已接到 `AgentTool` contract：
  - `parametersSchema()` 暴露 `command` / `timeout`
  - `execute()` 返回 `AgentToolResult<BashToolDetails>`
  - 执行改成 virtual thread 异步，避免直接阻塞调用线程
- shell 执行语义已对齐当前 TS contract：
  - cwd 固定为 tool 初始化时的 cwd
  - 支持 `commandPrefix`
  - 支持 `spawnHook` 改写 command/cwd/env
  - stdout/stderr 合流
  - `(no output)`、`Command exited with code ...`、`Command timed out after ... seconds`、`Command aborted` 文案已对齐最小 contract
- streaming / truncation 语义已接上现有 shell primitive：
  - chunk 到达时会持续推 `tool_execution_update`
  - partial update 用 rolling tail buffer 做即时 tail truncation
  - 最终结果会带 `BashToolDetails.truncation`
  - 输出 spill 到 temp file 时会在最终文案里追加 `Full output: ...`
- abort 语义已真正打通：
  - `Agent.abort()` -> `AgentEventStream.close()` -> `AgentLoop` cancel flag -> `BashTool` cancelled supplier -> `DefaultShellExecutor` 杀进程树

这批 contract tests 已覆盖：

- 正常执行命令
- non-zero exit code 错误文案
- timeout 错误文案
- cwd 不存在错误文案
- `commandPrefix`
- partial streaming update
- truncation details + full output path
- cancelled supplier -> `Command aborted`
- runtime close stream -> tool cancel supplier

这一刀的边界：

- 还没有开始 `grep` / `find` / `ls` 的实际 tool 实现。
- 还没有补 `bash` tool 的 golden fixture / TS 输出对拍。
- 当前 `bash` tool 的 env surface 只到 `spawnHook`；CLI/bin-dir prepend 之类更高层策略留到后续 CLI/runtime 集成时接。

### 31. 阶段 4：`grep` tool

已继续在 `modules/pi-tools/src/main/java/dev/pi/tools/` 与 `modules/pi-tools/src/test/java/dev/pi/tools/GrepToolTest.java` 下补上第一版 `grep` tool。

本次新增的入口：

- `GrepTool`
- `GrepToolOptions`
- `GrepOperations`
- `GrepToolDetails`
- `RipgrepRunner`
- `GrepToolTest`

当前这版 `grep` 的实现特点：

- 已接到 `AgentTool` contract：
  - `parametersSchema()` 暴露 `pattern` / `path` / `glob` / `ignoreCase` / `literal` / `context` / `limit`
  - `execute()` 返回 `AgentToolResult<GrepToolDetails>`
  - 执行改成 virtual thread 异步，避免直接阻塞调用线程
- 搜索语义当前沿用 TS 合同：
  - 默认搜索 cwd
  - 单文件结果输出 `basename:line: text`
  - 目录搜索结果输出相对路径
  - `context` 会追加 `path-line- text` 风格上下文行
  - `limit` 是全局 match limit，不是按文件 limit
  - 无结果时返回 `No matches found`
- 底层实现当前拆成两层：
  - `RipgrepRunner` 负责跑 `rg --json --line-number --hidden`
  - `GrepOperations` 负责 `isDirectory` / `readFile`
  - 两层都可注入，方便后续 remote FS / remote grep
- 输出收敛语义已对齐当前 TS contract：
  - match limit notice：`[N matches limit reached. Use limit=... for more, or refine pattern]`
  - byte truncation notice：`[50.0KB limit reached]`
  - 长行截断 notice：`[Some lines truncated to 500 chars. Use read tool to see full lines]`
  - details 已保留 `truncation` / `matchLimitReached` / `linesTruncated`
- 当前实现已支持 tool cancel supplier：
  - `RipgrepRunner.local()` 轮询 cancelled flag
  - cancel 时会主动终止 rg 进程并返回 `Operation aborted`

这批 contract tests 已覆盖：

- 单文件输出包含文件名
- global limit + context lines
- no matches
- long line truncation notice

这一刀的边界：

- 还没有开始 `find` / `ls` 的实际 tool 实现。
- 还没有补 `grep` tool 的 golden fixture / TS 输出对拍。
- 默认运行时依赖本地 `rg`；如果环境缺失，会报 `ripgrep (rg) is not available`。

### 32. 阶段 4：`find` tool

已继续在 `modules/pi-tools/src/main/java/dev/pi/tools/` 与 `modules/pi-tools/src/test/java/dev/pi/tools/FindToolTest.java` 下补上第一版 `find` tool。

本次新增的入口：

- `FindTool`
- `FindToolOptions`
- `FindOperations`
- `FindToolDetails`
- `FindToolTest`

当前这版 `find` 的实现特点：

- 已接到 `AgentTool` contract：
  - `parametersSchema()` 暴露 `pattern` / `path` / `limit`
  - `execute()` 返回 `AgentToolResult<FindToolDetails>`
  - 执行改成 virtual thread 异步，避免直接阻塞调用线程
- 搜索语义当前覆盖了 TS 测试面的关键 contract：
  - 默认搜索 cwd
  - 返回相对路径
  - 匹配 `**/*.txt` 时，根目录文件也会命中
  - hidden files 会参与结果集
  - root `.gitignore` 会参与过滤
  - 无结果时返回 `No files found matching pattern`
- 本次没有走 `fd` 子进程，而是先落了纯 Java 文件树实现：
  - `FindOperations.local()` 用 `walkFileTree`
  - 内建跳过 `.git` / `node_modules`
  - 内建最小 root `.gitignore` 规则解析
  - `limit` 达到时直接终止遍历
- 输出收敛语义已对齐当前 TS contract：
  - result limit notice：`[N results limit reached. Use limit=... for more, or refine pattern]`
  - byte truncation notice：`[50.0KB limit reached]`
  - details 已保留 `truncation` / `resultLimitReached`

这批 contract tests 已覆盖：

- hidden files that are not gitignored
- root `.gitignore`
- result limit notice

这一刀的边界：

- 还没有开始 `ls` 的实际 tool 实现。
- 还没有补 `find` tool 的 golden fixture / TS 输出对拍。
- 当前 `.gitignore` 支持是 root-level 最小实现，还没有追平嵌套 `.gitignore` / negation rule 的完整 Git 语义。

### 33. 阶段 4：`ls` tool

已继续在 `modules/pi-tools/src/main/java/dev/pi/tools/` 与 `modules/pi-tools/src/test/java/dev/pi/tools/LsToolTest.java` 下补上第一版 `ls` tool。

本次新增的入口：

- `LsTool`
- `LsToolOptions`
- `LsOperations`
- `LsToolDetails`
- `LsToolTest`

当前这版 `ls` 的实现特点：

- 已接到 `AgentTool` contract：
  - `parametersSchema()` 暴露 `path` / `limit`
  - `execute()` 返回 `AgentToolResult<LsToolDetails>`
  - 执行改成 virtual thread 异步，避免直接阻塞调用线程
- 列目录语义当前覆盖了 TS 测试面的关键 contract：
  - 默认列 cwd
  - hidden files 会参与结果集
  - 目录项追加 `/`
  - 结果按大小写不敏感字母序排序
  - 空目录返回 `(empty directory)`
  - 路径不存在时返回 `Path not found: ...`
  - 路径不是目录时返回 `Not a directory: ...`
- 输出收敛语义已对齐当前最小 TS contract：
  - entry limit notice：`[N entries limit reached. Use limit=... for more]`
  - byte truncation notice：`[50.0KB limit reached]`
  - details 已保留 `truncation` / `entryLimitReached`
- 当前实现拆成两层：
  - `LsTool` 负责参数解析、排序、notice 拼装
  - `LsOperations` 负责 `exists` / `isDirectory` / `readdir`

这批 contract tests 已覆盖：

- hidden file / hidden dir 输出
- 路径不存在错误
- 非目录路径错误
- entry limit notice
- empty directory 输出

这一刀的边界：

- 还没有补 `ls` tool 的 golden fixture / TS 输出对拍。
- 当前 `ls` 仍是本地文件系统实现；后续若要接远端文件系统，可继续沿 `LsOperations` 注入扩展。

### 34. 阶段 4：内置工具 golden tests

已继续在 `modules/pi-tools/src/test/java/dev/pi/tools/BuiltinToolsGoldenTest.java` 与 `modules/pi-tools/src/test/resources/golden/tools/builtin-tools.json` 下补上第一版内置工具 golden tests，并顺手修了 tool details JSON 与 TS 的兼容问题。

本次新增的入口：

- `BuiltinToolsGoldenTest`
- `src/test/resources/golden/tools/builtin-tools.json`

本次收敛的实现点：

- 已为 `read` / `write` / `edit` / `bash` / `grep` / `find` / `ls` 固化第一批 golden 输出 fixture：
  - `read` oversized first line bash hint + truncation details
  - `write` success 文案
  - `edit` success 文案 + diff/details
  - `bash` 基础 stdout 输出
  - `grep` context + limit notice
  - `find` result-limit notice
  - `ls` directory suffix + entry-limit notice
- 为了让 Java 侧 details JSON 真正对齐 TS，本次补了两类兼容修正：
  - `TruncationLimit` 改成通过 `@JsonValue` 序列化为小写 `lines/bytes`
  - `ReadToolDetails` / `EditToolDetails` / `BashToolDetails` / `GrepToolDetails` / `FindToolDetails` / `LsToolDetails` 改成 `@JsonInclude(NON_NULL)`，避免把 TS 里的 `undefined` 字段序列化成显式 `null`
- 这批 golden tests 全部走 fake/injected operations，不依赖本地 shell、`rg`、`fd` 或真实文件系统状态，专门盯输出格式与 details shape

这批 golden tests 已覆盖：

- `read`：超长首行提示文案与 truncation JSON 形状
- `write`：success 文案
- `edit`：success 文案、diff 文本、`firstChangedLine`
- `bash`：基础 stdout 输出
- `grep`：context 行格式、match-limit notice、details shape
- `find`：result-limit notice、details shape
- `ls`：directory suffix、entry-limit notice、details shape

这一刀的边界：

- 当前 golden fixture 仍是“按 TS 合同手工固化”的第一版，不是自动从 TS 运行时录制产物生成。
- `bash` / `grep` / `find` 的更复杂 truncation/full-output 场景，后续若要继续追平，可以再补第二批 golden cases。

### 35. 阶段 5：`pi-extension-spi` core types + discovery skeleton

已继续在 `modules/pi-extension-spi/src/main/java/dev/pi/extension/spi/` 与 `modules/pi-extension-spi/src/test/java/dev/pi/extension/spi/ExtensionLoaderContractTest.java` 下补上第一版 SPI 类型面和扩展发现骨架。

本次新增的入口：

- `PiExtension`
- `ExtensionApi`
- `ExtensionContext`
- `ExtensionCommandContext`
- `ExtensionUiContext`
- `ToolDefinition`
- `CommandDefinition`
- `CommandHandler`
- `MessageRenderer`
- `MessageRenderContext`
- `ExtensionClassLoader`
- `LoadedExtension`
- `ExtensionLoadFailure`
- `ExtensionLoadResult`
- `ExtensionLoader`
- `ExtensionLoaderContractTest`

当前这版 `pi-extension-spi` 的实现特点：

- 先把 Java-native SPI 面固化成最小可用 contract：
  - `PiExtension` 作为 `ServiceLoader` 入口
  - `ExtensionApi` 先支持 `registerTool` / `registerCommand` / `registerMessageRenderer`
  - `ToolDefinition` 直接复用 `AgentTool`，避免重复定义 tool 执行契约
  - `CommandDefinition` / `CommandHandler` 先落异步命令处理骨架
  - `ExtensionContext` / `ExtensionCommandContext` / `ExtensionUiContext` 先保留 cwd / session / settings / reload 这组最稳定的骨架能力
- discovery skeleton 已可工作：
  - `ExtensionLoader` 会为每个 jar/path 建 `ExtensionClassLoader`
  - 通过 `ServiceLoader.load(PiExtension.class, classLoader)` 发现扩展
  - 调用 `extension.register(api)` 捕获 tool / command / renderer 注册项
  - 返回 `LoadedExtension` / `ExtensionLoadResult`，并支持关闭 classloader
- 当前注册面已补最小防御：
  - 同一扩展内 duplicate tool / command / renderer name 会直接报错
  - 没有 `PiExtension` service entry 的 jar 会收敛成 `ExtensionLoadFailure`

这批 contract tests 已覆盖：

- 运行时动态编译一个最小扩展 jar，并通过 `ServiceLoader` 成功加载
- 成功捕获 tool / command / message renderer 三类注册项
- `LoadedExtension` 暴露 `id` / `version` / `source` / `classLoader` 元信息
- `ExtensionLoadResult.close()` 后 classloader 可关闭
- 空 jar / 缺 service entry 时会返回 `ExtensionLoadFailure`

这一刀的边界：

- 还没有开始扩展事件总线。
- 还没有开始 shortcut / flag / provider / custom entry 等更完整注册面。
- 还没有把 loader 真正接到 runtime `/reload` 生命周期里。
- 还没有落仓库内置的示例插件源码；当前只有测试内动态编译的最小扩展示例。

### 36. 阶段 5：扩展事件总线

已继续在 `modules/pi-extension-spi/src/main/java/dev/pi/extension/spi/` 与 `modules/pi-extension-spi/src/test/java/dev/pi/extension/spi/` 下补上第一版扩展事件总线与 typed event contract。

本次新增的入口：

- `ExtensionEvent`
- `ExtensionHandler`
- `ResourcesDiscoverEvent`
- `ResourcesDiscoverResult`
- `SessionStartEvent`
- `SessionShutdownEvent`
- `ExtensionEventFailure`
- `ExtensionEventDispatchResult`
- `ExtensionEventBus`
- `ExtensionEventBusTest`
- `ExtensionTestJars`

本次收敛的实现点：

- `ExtensionApi` 现在已支持事件注册：
  - `on(Class<E>, ExtensionHandler<E, R>)`
- `LoadedExtension` 现在会保留扩展注册下来的 typed event handlers，并和 tool / command / renderer 一起固化成只读快照。
- `ExtensionLoader` 的捕获 API 现在会一起记录 event handlers；扩展 jar 经过 `ServiceLoader` 加载后，handler 注册信息会随 `LoadedExtension` 一起返回。
- `ExtensionEventBus` 已提供最小可用的顺序派发语义：
  - `hasHandlers()` 用于探测某类 event 是否有监听者
  - `emit(event, context)` 按扩展加载顺序依次调用 handler
  - handler 可返回同步值或 `CompletionStage`
  - 单个 handler 抛错不会中断后续 handler，失败会收敛到 `ExtensionEventFailure`
- 当前先补了三类最稳定的 typed events：
  - `ResourcesDiscoverEvent`
  - `SessionStartEvent`
  - `SessionShutdownEvent`
- `ExtensionEventDispatchResult` 会聚合成功结果与失败列表，便于后续 runtime 在 `/reload`、resource discovery、session lifecycle 接线时统一消费。

这批 contract tests 已覆盖：

- 运行时动态编译一个最小扩展 jar，并验证 `ExtensionLoader` 能捕获 `SessionStartEvent` handler 注册。
- 运行时动态编译一个含多个 `ResourcesDiscoverEvent` handlers 的扩展 jar，并验证：
  - 成功结果会进入 dispatch result
  - handler 抛错会收敛到 failure 列表
  - 后续 handlers 不会因前一个失败而被中断
- `ExtensionTestJars` 已把测试内动态编译 / 打包扩展 jar 的公共逻辑抽出来，便于后续继续补 reload / resource integration tests。

这一刀的边界：

- 还没有开始 shortcut / flag / provider / custom entry 等更完整注册面。
- 还没有把 `ResourcesDiscoverEvent` 真正接到 session/resource loader。
- 还没有把 event bus 真正接到 runtime `/reload`、session start/shutdown 生命周期里。
- 还没有落仓库内置的示例插件源码；当前仍以测试内动态编译扩展 jar 为主。

### 37. 阶段 5：注册面收敛

已继续在 `modules/pi-extension-spi/src/main/java/dev/pi/extension/spi/` 与 `modules/pi-extension-spi/src/test/java/dev/pi/extension/spi/` 下补上第一版 shortcut / flag 注册面与 loader capture。

本次新增的入口：

- `ShortcutHandler`
- `ShortcutDefinition`
- `FlagDefinition`
- `ExtensionRegistrationSurfaceTest`

本次收敛的实现点：

- `ExtensionApi` 现在已补齐注册面上的缺口：
  - `registerShortcut(ShortcutDefinition)`
  - `registerFlag(FlagDefinition)`
  - `getFlag(String)`
- `ShortcutDefinition` 先用最稳定的 Java contract 表达快捷键注册：
  - `keyId`
  - `description`
  - `ShortcutHandler`
- `FlagDefinition` 先落最小 CLI flag 语义：
  - `name`
  - `description`
  - `type`（`BOOLEAN` / `STRING`）
  - `defaultValue`
  - 构造时会校验 default value 与 flag type 一致
- `ExtensionLoader` 的捕获 API 现在会一起记录：
  - `shortcutDefinitions`
  - `flagDefinitions`
  - `flagDefaults`
- `getFlag()` 当前会在扩展注册阶段返回该扩展已注册 flag 的默认值，便于扩展在 `register()` 期间做最小自举判断；后续 runtime 接线时可以在此基础上叠加 CLI / settings 实际值。
- `LoadedExtension` 现在已把 shortcut / flag 注册结果和默认值一起固化成只读快照，后续可直接喂给 CLI flag parser、keybinding merge 和 `/reload` rebuild。
- 同一扩展内 duplicate shortcut / flag 仍延续当前 loader 的防御策略：直接报错并收敛为 `ExtensionLoadFailure`，避免不稳定覆盖顺序进入 runtime。

这批 contract tests 已覆盖：

- 运行时动态编译一个最小扩展 jar，并验证：
  - `ShortcutDefinition` 会被 loader 捕获
  - `FlagDefinition` 会被 loader 捕获
  - flag 默认值会进入 `LoadedExtension.flagDefaults()`
  - 扩展可在 `register()` 阶段通过 `getFlag()` 读到默认值并据此派生注册项
- 运行时动态编译一个重复注册同一 shortcut 的扩展 jar，并验证 loader 会返回 `ExtensionLoadFailure`

这一刀的边界：

- 还没有补 command argument completions / richer autocomplete item contract。
- 还没有把 shortcut 真正接到 keybinding config merge / conflict diagnostics。
- 还没有把 flag 真正接到 CLI args / settings overlay。
- 还没有把 resource discovery 与 `/reload` runtime rebuild 接起来。

### 38. 阶段 5：资源发现扩展点

已继续在 `modules/pi-extension-spi/src/main/java/dev/pi/extension/spi/` 与 `modules/pi-extension-spi/src/test/java/dev/pi/extension/spi/` 下补上第一版 resource discovery 聚合器。

本次新增的入口：

- `ExtensionResourcePath`
- `ExtensionResourceDiscoveryResult`
- `ExtensionResourceDiscovery`
- `ExtensionResourceDiscoveryTest`

本次收敛的实现点：

- `ExtensionResourceDiscovery` 现在会顺序执行所有 `ResourcesDiscoverEvent` handlers，并把扩展返回的资源路径收敛成可消费的结构化结果。
- 聚合结果按资源类型拆分：
  - `skillPaths`
  - `promptPaths`
  - `themePaths`
- 每条资源路径现在都会带上最小来源元信息：
  - `declaredPath`
  - `resolvedPath`
  - `extensionId`
  - `extensionSource`
  - `baseDir`
- 当前路径归一化策略如下：
  - 绝对路径直接规范化
  - `~` / `~/...` 会展开到用户目录
  - 相对路径默认解析到扩展 source 所在目录（jar 用父目录，目录扩展用目录本身）
- 聚合阶段会按规范化后的绝对路径去重，保持首次出现顺序，避免同一扩展或多个扩展重复提供完全相同的资源路径。
- handler 抛错和无效返回路径不会中断后续扩展；它们都会继续收敛到 `ExtensionEventFailure`，便于后续 `/reload` 与 resource loader 统一消费。
- 这一步没有直接接 `pi-session` 的 `InstructionResourceLoader`，因为那一层目前只负责上下文文件与 system prompt；skills / prompts / themes 的真正 runtime 接线留给下一刀 `/reload` 和应用层 resource loader。

这批 contract tests 已覆盖：

- 运行时动态编译一个最小扩展 jar，并验证：
  - `skills` / `./skills` 会归一到同一个 `resolvedPath`
  - prompt / theme 路径会相对扩展 source 正确解析
  - `ExtensionResourcePath` 会保留 extension source 与 baseDir
- 运行时动态编译一个返回空白路径且后续 handler 抛错的扩展 jar，并验证：
  - 无效路径会被收敛成 failure
  - handler 异常会被收敛成 failure
  - 后续 handlers 不会因前一个失败而被中断

这一刀的边界：

- 还没有把 discovery result 真正接到 skills / prompts / themes 的 runtime loader。
- 还没有给资源路径补 package/source metadata 与 enable/disable overlay。
- 还没有把这条链路接到 `/reload` 生命周期和 classloader rebuild。

### 39. 阶段 5：`/reload` runtime rebuild

已继续在 `modules/pi-extension-spi/src/main/java/dev/pi/extension/spi/` 与 `modules/pi-extension-spi/src/test/java/dev/pi/extension/spi/` 下补上第一版 reloadable extension runtime。

本次新增的入口：

- `ExtensionRuntimeSnapshot`
- `ExtensionRuntime`
- `ExtensionRuntimeReloadTest`

本次收敛的实现点：

- `ExtensionRuntime` 现在把扩展加载结果和后续运行时视图绑定到一个可重载对象里：
  - 固定持有 `sources`
  - 持有当前 `ExtensionLoadResult`
  - 暴露当前 `ExtensionRuntimeSnapshot`
- `ExtensionRuntimeSnapshot` 会把当前 reload 之后需要消费的视图收敛在一起：
  - `extensions`
  - `failures`
  - `eventBus`
  - `resourceDiscovery`
- `reload()` 现在会执行最小 rebuild 流程：
  - 重新从相同 source 列表加载扩展
  - 基于新 load result 重建 `ExtensionEventBus`
  - 基于新 load result 重建 `ExtensionResourceDiscovery`
  - 关闭旧 `ExtensionLoadResult` 上关联的 classloaders
- `close()` 现在会关闭当前 runtime 持有的 classloaders，并把 snapshot 清空为 empty runtime，避免旧扩展对象继续被 runtime 持有。
- 这一层先只做 `pi-extension-spi` 模块内的 runtime rebuild，不直接负责 settings reload、resource loader reload、tool registry rebuild；这些仍属于后续 `PiAgentSession` / app runtime 的职责。

这批 contract tests 已覆盖：

- 运行时动态编译一个扩展 jar，构建 `ExtensionRuntime` 后再覆写同一路径 jar，并验证：
  - `reload()` 后旧 classloader 会被关闭
  - 新 snapshot 会看到新的 command 注册
  - `eventBus` / `resourceDiscovery` 会基于新扩展内容重建
- 调用 `close()` 后会关闭当前 classloader，并把 snapshot 清空

这一刀的边界：

- 还没有把 `session_shutdown -> settings reload -> resources reload -> runtime rebuild -> session_start` 串成应用层完整流程。
- 还没有恢复前一次 reload 之前的 flag values / active tools / UI bindings。
- 还没有把 resource discovery 结果接到 skills / prompts / themes runtime loader。
- 还没有落仓库内最小示例插件源码。

### 40. 阶段 5：仓库内最小示例插件

已继续在 `examples/minimal-extension/` 与 `modules/pi-extension-spi/src/test/java/dev/pi/extension/spi/` 下补上第一版 checked-in example plugin，并用真实源码热重载测试验证它。

本次新增的入口：

- `examples/minimal-extension/README.md`
- `examples/minimal-extension/src/main/java/dev/pi/examples/MinimalExtension.java`
- `examples/minimal-extension/src/main/resources/META-INF/services/dev.pi.extension.spi.PiExtension`
- `ExampleExtensionPluginTest`

本次收敛的实现点：

- 仓库里现在已有一个最小可读的 Java 插件示例，覆盖：
  - `PiExtension` 入口
  - `SessionStartEvent` 订阅
  - command 注册
  - shortcut 注册
  - flag 注册
  - `META-INF/services` service entry
- `examples/minimal-extension/README.md` 说明了这个示例的结构和当前测试验证点，后续可继续扩成真正的 sample plugin 文档入口。
- `ExtensionTestJars` 现在已支持从真实项目目录编译示例源码并打包 jar，不再只支持测试内的 inline source string。
- `ExampleExtensionPluginTest` 现在会：
  - 复制仓库内 example project 到临时目录
  - 把它编译成 jar
  - 通过 `ExtensionRuntime` 实际加载
  - 修改示例源码中的 command 名称
  - 重新编译并调用 `reload()`
  - 验证旧 classloader 已关闭且新注册项生效

这批 contract tests 已覆盖：

- checked-in example plugin 可以被 loader 真实发现并加载
- example plugin 的 command / shortcut / flag / event handler 注册可进入 `LoadedExtension`
- 修改示例源码后，`ExtensionRuntime.reload()` 会加载新版本并关闭旧 classloader

这一刀的边界：

- example plugin 目前还是最小骨架，没有接内置 tools、message renderer、resource discovery 或 UI 交互。
- 还没有给 example plugin 单独配 Gradle subproject；当前以仓库内源码 + 测试编译打包为主。
- 阶段 5 的 SPI 目标已基本收口，后续更完整的 skills / prompts / themes runtime 接线将随应用层推进。

### 41. 阶段 6：`pi-tui` 核心接口

已继续在 `modules/pi-tui/src/main/java/dev/pi/tui/` 与 `modules/pi-tui/src/test/java/dev/pi/tui/` 下补上第一版 TUI 核心接口与最小值对象。

本次新增的入口：

- `InputHandler`
- `Component`
- `Focusable`
- `Terminal`
- `Overlay`
- `OverlayAnchor`
- `OverlayMargin`
- `OverlayOptions`
- `PiTuiContractsTest`

本次收敛的实现点：

- `Component` 先按最稳定 contract 落地：
  - `render(int width)`
  - `handleInput(String data)` default no-op
  - `invalidate()` default no-op
- `Focusable` 先用 Java 接口表达 focus state：
  - `isFocused()`
  - `setFocused(boolean)`
- `Terminal` 先固化第一层终端抽象：
  - `start(InputHandler, Runnable onResize)`
  - `stop()`
  - `write()`
  - `columns()` / `rows()`
  - cursor / clear / title 相关方法先给 default no-op，便于后续逐步补 JLine-backed 实现
- `Overlay` 先表达最小 overlay state：
  - `component()`
  - `options()`
  - `isHidden()`
  - `setHidden(boolean)`
- `OverlayAnchor` / `OverlayMargin` / `OverlayOptions` 先把 overlay 的最小定位和 margin 值对象定下来，为后续 overlay stack 和 responsive 布局留稳定形状。

这批 contract tests 已覆盖：

- 一个组件可以同时实现 `Component + Focusable`
- `Terminal` contract 可以被最小 fake terminal 实现
- `OverlayOptions.defaults()` 会稳定给出 centered + zero-margin 的默认值
- `Overlay` contract 可以被最小 fake overlay 实现

这一刀的边界：

- 还没有接 JLine terminal 实现。
- 还没有实现 raw mode、resize、title、cursor、bracketed paste、kitty keyboard protocol。
- 还没有开始 diff renderer、overlay stack、IME cursor marker 或任何具体组件。

## 已完成的验证

已通过的命令：

```bash
.\gradlew.bat :pi-ai:test --no-daemon
.\gradlew.bat :pi-agent-runtime:test --no-daemon
.\gradlew.bat :pi-session:test --no-daemon
.\gradlew.bat :pi-tools:test --no-daemon
.\gradlew.bat :pi-extension-spi:test --no-daemon
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
- provider 交叉行为测试矩阵、OpenAI provider 的 compat/replay 收敛、abort/handoff/image input cross-provider coverage
- `pi-agent-runtime` 的单轮 loop lifecycle、`transformContext -> convertToLlm` 顺序、`continueLoop()` 前置条件校验
- `pi-agent-runtime` 的顺序 tool execution、多轮 tool-result 续跑、tool execution update/end lifecycle、参数校验失败 -> error tool result 语义
- `pi-agent-runtime` 的 steering after tool -> skip remaining tool calls 语义、follow-up message -> reopen turn 语义
- `pi-agent-runtime` 的 `Agent` facade 事件订阅、状态订阅、`prompt()` / `resume()` 与 follow-up 集成语义
- `pi-agent-runtime` 的 abort -> inner assistant stream close -> aborted assistant lifecycle 语义
- `pi-session` 的 TS seed fixture 解析、malformed/unknown line 跳过、现代 session document parse/write round-trip 稳定性
- `pi-session` 的 `v1 -> v2 -> v3` migration、`buildSessionContext()` 的 leaf replay / compaction / branch summary / custom message / bashExecution 语义
- `pi-session` 的 `SessionManager` in-memory append/navigation/tree、persistent delayed flush、legacy open-and-migrate 语义
- `pi-session` 的 branch summary、in-memory fork path 裁剪与 label 保留、persisted fork delayed/immediate flush、fork 后 JSONL header/id 稳定性
- `pi-session` 的 settings deep merge、legacy settings migration、latest-on-disk merge、project override、file lock 序列化、reload parse-error 恢复语义
- `pi-session` 的 instruction resource assembly：global/ancestor context file 顺序、`AGENTS.md` 优先级、project/global system prompt 覆盖、append prompt 回退语义
- `pi-tools` 的 truncation line/byte limit 语义、path fallback、edit diff primitive、shell timeout/spill/truncation、image resize/dimension note/fallback 语义
- `pi-tools` 的 `read` tool：文本读取、图片魔数识别、`offset` / `limit` / truncation prompt、oversized line bash hint、auto-resize 开关
- `pi-tools` 的 `write` tool：文件覆盖写入、父目录自动创建、相对路径 cwd resolve、成功文案
- `pi-tools` 的 `edit` tool：exact/fuzzy match、duplicate/not-found 拒绝、CRLF/BOM 保留、diff details
- `pi-tools` 的 `bash` tool：streaming update、timeout/abort、tail truncation、full output path
- `pi-tools` 的 `grep` tool：rg JSON search、context lines、match limit notice、line truncation notice
- `pi-tools` 的 `find` tool：pure-Java tree walk、hidden files、root `.gitignore`、result limit notice
- `pi-tools` 的 `ls` tool：dotfiles、directory suffix、entry limit notice、empty directory
- `pi-tools` 的内置工具 golden fixtures：tool text output、limit notice、diff/details JSON shape、`TruncationLimit` 小写序列化
- `pi-extension-spi` 的 core types / loader skeleton：`ServiceLoader` 扩展发现、tool/command/renderer 注册捕获、classloader close、no-service failure 收敛
- `pi-extension-spi` 的 event bus：typed event handler 捕获、顺序派发、async handler 归一化、dispatch failure 收敛
- `pi-extension-spi` 的 registration surface：shortcut/flag 捕获、flag default lookup、duplicate shortcut failure 收敛
- `pi-extension-spi` 的 resource discovery：resource path 聚合、扩展 source 相对路径归一化、invalid path / handler failure 收敛
- `pi-extension-spi` 的 reload runtime：snapshot rebuild、old classloader close、event bus / resource discovery rebuild
- `pi-extension-spi` 的 checked-in example plugin：真实源码编译、service entry、热重载验证
- `pi-tui` 的 core contracts：`Terminal` / `Component` / `Focusable` / `Overlay` 接口与最小值对象
- `pi-agent-runtime` 的 tool cancellation：close event stream -> tool cancel supplier

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
- `modules/pi-ai/src/test/java/dev/pi/ai/provider/ProviderBehaviorMatrixTest.java`
- `modules/pi-agent-runtime/src/test/java/dev/pi/agent/runtime/AgentLoopContractTest.java`
- `modules/pi-agent-runtime/src/test/java/dev/pi/agent/runtime/AgentLoopToolExecutionTest.java`
- `modules/pi-agent-runtime/src/test/java/dev/pi/agent/runtime/AgentTest.java`
- `modules/pi-agent-runtime/src/test/java/dev/pi/agent/runtime/AgentLoopCancellationTest.java`
- `modules/pi-session/src/test/java/dev/pi/session/SessionJsonlCodecTest.java`
- `modules/pi-session/src/test/java/dev/pi/session/SessionMigrationsTest.java`
- `modules/pi-session/src/test/java/dev/pi/session/SessionContextReplayTest.java`
- `modules/pi-session/src/test/java/dev/pi/session/SessionManagerTest.java`
- `modules/pi-session/src/test/java/dev/pi/session/SettingsManagerTest.java`
- `modules/pi-session/src/test/java/dev/pi/session/InstructionResourceLoaderTest.java`
- `modules/pi-tools/src/test/java/dev/pi/tools/TextTruncatorTest.java`
- `modules/pi-tools/src/test/java/dev/pi/tools/ToolPathsTest.java`
- `modules/pi-tools/src/test/java/dev/pi/tools/EditDiffsTest.java`
- `modules/pi-tools/src/test/java/dev/pi/tools/DefaultShellExecutorTest.java`
- `modules/pi-tools/src/test/java/dev/pi/tools/ImageResizerTest.java`
- `modules/pi-tools/src/test/java/dev/pi/tools/ReadToolTest.java`
- `modules/pi-tools/src/test/java/dev/pi/tools/WriteToolTest.java`
- `modules/pi-tools/src/test/java/dev/pi/tools/EditToolTest.java`
- `modules/pi-tools/src/test/java/dev/pi/tools/BashToolTest.java`
- `modules/pi-tools/src/test/java/dev/pi/tools/GrepToolTest.java`
- `modules/pi-tools/src/test/java/dev/pi/tools/FindToolTest.java`
- `modules/pi-tools/src/test/java/dev/pi/tools/LsToolTest.java`
- `modules/pi-tools/src/test/java/dev/pi/tools/BuiltinToolsGoldenTest.java`
- `modules/pi-extension-spi/src/test/java/dev/pi/extension/spi/ExtensionLoaderContractTest.java`
- `modules/pi-extension-spi/src/test/java/dev/pi/extension/spi/ExtensionEventBusTest.java`
- `modules/pi-extension-spi/src/test/java/dev/pi/extension/spi/ExtensionRegistrationSurfaceTest.java`
- `modules/pi-extension-spi/src/test/java/dev/pi/extension/spi/ExtensionResourceDiscoveryTest.java`
- `modules/pi-extension-spi/src/test/java/dev/pi/extension/spi/ExtensionRuntimeReloadTest.java`
- `modules/pi-extension-spi/src/test/java/dev/pi/extension/spi/ExampleExtensionPluginTest.java`
- `modules/pi-tui/src/test/java/dev/pi/tui/PiTuiContractsTest.java`

## 未完成 / 已知缺口

### `pi-session`

阶段 3 已收尾，当前没有新的 blocker。

### `pi-tools`

阶段 4 当前已完成 truncation / diff / shell / path policy / image resize primitives，以及 `read` / `write` / `edit` / `bash` / `grep` / `find` / `ls` tool 和第一批 golden output fixtures。

下一步按任务顺序应继续：

- `pi-extension-spi`
- core types / extension discovery skeleton

### `pi-extension-spi`

阶段 5 当前已收尾。

下一步按任务顺序应继续：

- `pi-tui`
- terminal raw mode / resize / title / cursor / bracketed paste / kitty keyboard protocol
- diff renderer 与 synchronized output

### 其他模块

以下模块目前还只是最小工程骨架，尚未开始功能实现：

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

1. 进入阶段 5 `pi-extension-spi`。
2. 继续阶段 6 `pi-tui`。
3. 先补 terminal raw mode / resize / title / cursor / bracketed paste。

更具体的下一步切片建议：

1. `pi-tui`：terminal raw mode / resize / title / cursor / bracketed paste / kitty keyboard protocol。
2. `pi-tui`：diff renderer 与 synchronized output 骨架。
3. `pi-tui`：overlay stack、IME cursor marker、hardware cursor positioning。

并行拆分文档入口：

- `docs/subtasks/README.md`

## 交接注意事项

- `pi-java` 当前进度已提交并推送到 GitHub。
- `pi-java/README.md` 已链接 `handoff.md` 和 `docs/subtasks/README.md`。
- `pi-java` 下的 `.gradle/` 和 `build/` 已通过本地 `.gitignore` 忽略。
