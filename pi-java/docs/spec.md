# Java 版本 Pi 规格

## 概述

`pi-java` 是 Pi 的 Java 实现，目标不是复刻 TypeScript 源码结构，而是在 Java 生态中实现与现有 Pi 等价的核心产品能力：

- 统一的 LLM provider abstraction
- 有状态的 agent runtime
- 面向终端的 interactive coding harness
- JSONL 会话树与上下文压缩
- Skills / Prompt Templates / Themes / Extensions 资源系统
- Interactive / Print / JSON / RPC / SDK 多运行模式

本规格同时受以下现有规范约束：

- `openspec/config.yaml`
- `openspec/specs/agent-runtime/spec.md`
- `openspec/specs/coding-agent-cli/spec.md`
- `openspec/specs/llm-provider/spec.md`

## 设计目标

### 目标

1. 保留现有 Pi 的核心语义，而不是只保留命令名。
2. 让 Java 版可以独立演进，但对会话、消息、工具、配置保持高兼容。
3. 为后续桌面集成、服务端托管、企业内网部署提供更稳的运行时基础。
4. 保持 “可扩展而非预设” 的产品哲学。

### 非目标

1. 不要求第一阶段支持所有 TypeScript provider。
2. 不要求第一阶段与 TypeScript TUI 像素级一致。
3. 不直接执行现有 TypeScript extension 文件。
4. 不以 GraalVM native image 作为第一优先级交付形式。

## 开发原则

### TDD 优先

Java 版 Pi 的实现流程默认采用 `TDD`，不是“实现完再补测试”。

最低要求：

1. 新增能力先写 contract test、golden test 或最小失败用例。
2. bug fix 必须先补一个能稳定复现该问题的失败测试。
3. 实现阶段只允许写到测试变绿所需的最小代码。
4. 测试变绿后再做重构，且重构不得跳过回归测试。

不同模块的测试形态应当不同：

- `pi-ai`：provider contract tests、streaming event tests、serialization tests
- `pi-agent-runtime`：agent loop lifecycle tests、tool execution tests、steering/follow-up tests
- `pi-session`：session replay tests、migration tests、settings merge tests
- `pi-tools`：golden output tests、truncate/diff tests、abort/timeout tests
- `pi-tui`：virtual terminal render tests、key input tests、IME cursor tests
- `pi-cli`：mode integration tests、session resume tests、command tests

## 必须保留的核心概念

### 1. LLM 抽象层语义

Java 版必须保留与 `pi-ai` 对齐的抽象：

- `Api` 与 `Provider` 分离
- `Model` 独立描述 provider、api、baseUrl、reasoning、input、cost、contextWindow、maxTokens
- `Context = systemPrompt + messages + tools`
- `Message` 至少包含 `user`、`assistant`、`toolResult`
- `AssistantMessageEvent` 采用统一流式事件模型

统一事件流必须保留这些事件类型：

- `start`
- `text_start` / `text_delta` / `text_end`
- `thinking_start` / `thinking_delta` / `thinking_end`
- `toolcall_start` / `toolcall_delta` / `toolcall_end`
- `done`
- `error`

### 2. Agent Runtime 语义

Java 版必须保留与 `pi-agent-core` 对齐的语义：

- `AgentState`
- `convertToLlm(messages)` 转换钩子
- `transformContext(messages)` 上下文变换钩子
- `steering` 与 `follow-up` 双队列
- 工具调用按顺序执行，且每个工具调用之间都要检查 steering
- `AgentEvent` 驱动 UI 和 SDK

统一的 agent 事件必须保留：

- `agent_start`
- `agent_end`
- `turn_start`
- `turn_end`
- `message_start`
- `message_update`
- `message_end`
- `tool_execution_start`
- `tool_execution_update`
- `tool_execution_end`

### 3. Coding Agent CLI 语义

Java 版必须保留与 `pi-coding-agent` 对齐的用户行为：

- 运行模式：`interactive`、`print`、`json`、`rpc`、`sdk`
- TUI 布局：头部、消息区、编辑区、底栏
- 内置工具：`read`、`bash`、`edit`、`write`、`grep`、`find`、`ls`
- slash commands 与 keybindings
- 会话树、fork、tree navigation、compaction
- `AGENTS.md` / `CLAUDE.md` / `SYSTEM.md` / `APPEND_SYSTEM.md` 的上下文装配
- Skills / Prompt Templates / Themes / Extensions 的资源发现与热重载

## 兼容性要求

### 数据兼容

Java 版优先保证以下数据格式可互通：

1. `Message` JSON 结构
2. `AssistantMessage` / `ToolResultMessage` JSON 结构
3. `AssistantMessageEvent` 事件名和关键字段
4. `Session JSONL` 文件格式
5. `settings.json` 全局与项目级配置语义
6. Skills / Prompt Templates / Themes 的文件格式

### 行为兼容

Java 版至少保持以下行为一致：

1. tool result 使用 `isError: true` 表示工具失败，而不是发明新的错误消息结构。
2. `read` 工具支持 `offset` / `limit`，并在输出被截断时提示继续读取。
3. `bash` 工具支持 streaming update、输出截断与临时文件回退。
4. `edit` 工具使用 exact text replacement 语义，不做 AST 级魔改。
5. `session tree` 使用 `id + parentId` 建模，而不是单纯线性历史。

## Java 版模块规格

### `pi-ai`

职责：

- Provider registry
- Model registry
- Credential resolution
- 流式请求与事件标准化
- Context serialization

首批 provider 范围：

- `openai-responses`
- `openai-completions` 兼容层
- `anthropic-messages`
- `google-generative-ai`
- `bedrock-converse-stream`

第二批 provider：

- `google-vertex`
- `azure-openai-responses`
- `openai-codex-responses`
- `github-copilot`
- 其他 OpenAI-compatible providers

### `pi-agent-runtime`

职责：

- Agent state machine
- Agent loop
- tool call validation
- steering / follow-up queue
- event stream aggregation

### `pi-session`

职责：

- Session JSONL 读写
- 版本迁移
- tree navigation
- fork / compact / branch summary
- settings layering

### `pi-tools`

职责：

- 内置工具实现
- truncation policy
- shell execution abstraction
- 文件系统 / 图片 / diff 辅助能力

### `pi-extension-spi`

职责：

- Java-native plugin SPI
- tool / command / shortcut / renderer / provider registration
- extension event bus
- mode-aware UI context

### `pi-tui`

职责：

- terminal abstraction
- differential renderer
- overlay system
- focus / IME cursor positioning
- editor / markdown / selector / footer 等基础组件

### `pi-cli`

职责：

- CLI args
- interactive / print / json / rpc mode orchestration
- startup resource loading
- version / changelog / export / share 等命令

### `pi-sdk`

职责：

- 对外提供 `createAgentSession()`、`ModelRegistry`、`SessionManager` 等稳定 API

## 核心概念

### 消息模型

Java 版保持以下消息层次：

```java
sealed interface Message permits UserMessage, AssistantMessage, ToolResultMessage {}

record UserMessage(Object content, long timestamp) implements Message {}
record AssistantMessage(
    List<AssistantContent> content,
    String api,
    String provider,
    String model,
    Usage usage,
    StopReason stopReason,
    String errorMessage,
    long timestamp
) implements Message {}
record ToolResultMessage(
    String toolCallId,
    String toolName,
    List<UserContent> content,
    Object details,
    boolean isError,
    long timestamp
) implements Message {}
```

### AgentMessage 与扩展消息

必须保留“LLM message 与 app-specific message 分离”的能力：

- LLM 只理解 `user / assistant / toolResult`
- App 可以注入 `custom messages`
- `convertToLlm()` 决定哪些消息进入 LLM 上下文

### 会话树

Java 版会话模型必须保留：

- header line: `type=session`
- body lines: `message / thinking_level_change / model_change / compaction / branch_summary / custom / custom_message / label / session_info`
- tree edges: `parentId`

## API 参考

### LLM 层

```java
Model model = modelRegistry.get("openai", "gpt-5.1-codex");

Context context = new Context(
    "You are a helpful assistant.",
    List.of(new UserMessage("Hello", System.currentTimeMillis())),
    List.of()
);

AssistantMessageEventStream stream = aiClient.streamSimple(
    model,
    context,
    SimpleStreamOptions.builder().reasoning(ThinkingLevel.MEDIUM).build()
);
```

### Agent 层

```java
Agent agent = Agent.builder()
    .model(model)
    .systemPrompt("You are a coding agent.")
    .convertToLlm(messages -> MessageConverters.defaultConvert(messages))
    .transformContext(contextTransformer)
    .tools(defaultTools)
    .build();

agent.prompt("Read README.md and summarize it.");
```

### SDK 层

```java
AgentSession session = PiSessions.create(config -> config
    .cwd(Paths.get("."))
    .mode(SessionMode.IN_MEMORY)
    .modelRegistry(modelRegistry)
);

session.prompt("What files are in the current directory?");
```

## 使用示例

### Print 模式

```bash
pi-java -p --provider openai --model gpt-5.1-codex "Review this repo"
```

### JSON 模式

```bash
pi-java --mode json @README.md "Summarize this file"
```

### Resume 会话

```bash
pi-java -c
pi-java --session ~/.pi/agent/sessions/.../2026-03-08_xxx.jsonl
```

## 明确的偏移决策

Java 版有三处故意不做字面照搬：

1. TypeScript Extensions 改为 Java plugin SPI，因为 Java 运行时不应以热加载 `.ts` 文件为主路径。
2. TypeBox/AJV 改为 `JSON Schema + Jackson + validator`，但 tool schema 语义保持一致。
3. Node.js process/stdio 直接控制改为 Java terminal abstraction，但键位、paste、overlay、diff render 的语义保持一致。
