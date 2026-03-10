# Java 版本 Pi 实施任务

## 开发流程原则

1. 默认采用 `TDD`，每个任务都按 `red -> green -> refactor` 执行。
2. 任何 bug 修复都先写失败测试，再改实现。
3. 新 provider、新工具、新 session 语义都必须先落 fixture 或 contract test。
4. 没有测试的代码可以作为实验代码存在，但不能作为阶段完成标准。
5. 每个阶段收尾都要先清空该阶段测试红灯，再进入下一阶段。
6. 具体测试模板、命名、fixture/golden 策略以 `docs/tdd.md` 为准。

## 当前任务状态

更新时间：2026-03-10

- 阶段 0：已完成。
- 阶段 1：已完成 `core model / event types`、`AssistantMessageEventStream / EventStream`、`ApiProviderRegistry / ModelRegistry / CredentialResolver / PiAiClient facade`、`AssistantMessage partial assembler`、`SSE parser / WebSocket adapter`、`openai-responses provider`、`openai-completions provider`、`anthropic-messages provider`、`google-generative-ai provider`、`bedrock-converse-stream provider`、`message transform / validation / compat` 抽象层、provider 交叉行为测试矩阵（`abort` / `handoff` / `image input` / `cross-provider coverage`）。
- 阶段 1：已收尾。
- 阶段 2：已完成 `AgentMessage / AgentTool / AgentToolResult / AgentState / AgentEvent / AgentLoopConfig` 核心类型、`AgentEventStream`、`convertToLlm / transformContext` 两阶段上下文管线、`AssistantMessageEvent -> AgentEvent` 生命周期桥接、顺序工具执行、基础 JSON Schema 参数校验、steering / follow-up 队列、`Agent` facade 与事件/状态订阅、interrupt / lifecycle tests。
- 阶段 3：已完成 `SessionHeader / SessionEntry / SessionTreeNode / SessionContext / SessionDocument`、`Session JSONL parse/write skeleton`、`v1 -> v2 -> v3 migration`、`buildSessionContext()` replay contract tests、`SessionManager` skeleton。
- 阶段 3：下一步补 session tree / fork / persisted append contract tests。
- 阶段 4 到阶段 9：未开始。

## 阶段 0：项目骨架

1. 建立 Gradle 多模块骨架，至少包含 `pi-ai`、`pi-agent-runtime`、`pi-session`、`pi-tools`、`pi-extension-spi`、`pi-tui`、`pi-cli`、`pi-sdk`。
2. 锁定 Java 版本为 `21`，统一测试与代码风格插件。
3. 建立共享的 `jackson`、`junit`、`picocli`、`jline`、`aws sdk`、`google auth` 依赖管理。
4. 建立 golden fixtures 目录，导入现有 TypeScript 生成的 `session jsonl`、`message json`、`event json` 样例。

## 阶段 1：`pi-ai`

1. 定义 `Model`、`Context`、`Message`、`Tool`、`AssistantMessageEvent`、`StreamOptions`、`SimpleStreamOptions`。
2. 实现 `AssistantMessageEventStream` 与通用 `EventStream` 基础设施。
3. 实现 `ApiProviderRegistry`、`ModelRegistry`、`CredentialResolver`。
4. 实现通用 `SSE parser` 与 `WebSocket adapter`。
5. 落地 `openai-responses` provider。
6. 落地 `openai-completions` 兼容 provider。
7. 落地 `anthropic-messages` provider。
8. 落地 `google-generative-ai` provider。
9. 落地 `bedrock-converse-stream` provider。
10. 增加 `abort`、`tool calling`、`handoff`、`serialization`、`reasoning`、`image input` 测试。

## 阶段 2：`pi-agent-runtime`

1. 定义 `AgentState`、`AgentTool`、`AgentEvent`、`AgentLoopConfig`。
2. 实现 `convertToLlm` 与 `transformContext` 两阶段上下文管线。
3. 实现 streaming assistant response 组装器。
4. 实现顺序工具执行与参数校验。
5. 实现 steering / follow-up 队列。
6. 实现 `Agent` facade 与订阅接口。
7. 增加 agent loop 生命周期测试、工具中断测试、steering 跳过剩余工具测试。

## 阶段 3：`pi-session`

1. 定义 `SessionHeader`、`SessionEntry`、`SessionTreeNode`、`SessionContext`、`Settings`。（已完成 session model 首版，另补 `SessionDocument`）
2. 实现 `Session JSONL` 解析与回写。（已完成 skeleton）
3. 实现 `v1 -> v2 -> v3` migration。（已完成）
4. 实现 `buildSessionContext()`，覆盖 compaction、branch summary、custom message 语义。（已完成）
5. 实现 `SettingsManager`，支持 global/project deep merge 与 lock。
6. 实现 `AGENTS.md` / `CLAUDE.md` / `SYSTEM.md` / `APPEND_SYSTEM.md` 资源装配。
7. 增加 session tree replay、fork、compact、settings migration 测试。

## 阶段 4：`pi-tools`

1. 实现 truncation、diff、shell、path policy、image resize 支撑类。
2. 实现 `read` 工具，支持文本、图片、offset/limit、截断提示。
3. 实现 `write` 工具，支持自动建目录。
4. 实现 `edit` 工具，支持 exact match + diff details。
5. 实现 `bash` 工具，支持 streaming output、timeout、abort、tail truncation、full output temp file。
6. 实现 `grep`、`find`、`ls`。
7. 增加内置工具 golden tests，确认输出格式与 TS 版本一致。

## 阶段 5：`pi-extension-spi`

1. 定义 `ExtensionApi`、`ExtensionContext`、`ExtensionUiContext`、`ToolDefinition`、`CommandDefinition`、`MessageRenderer`。
2. 定义插件发现协议：`ServiceLoader + isolated ClassLoader`。
3. 实现扩展事件总线。
4. 实现 tool / command / shortcut / flag / renderer 注册。
5. 实现资源发现扩展点：extensions 可返回附加资源路径。
6. 实现 `/reload` 时 runtime 重建与 classloader 回收。
7. 编写一个最小示例插件验证注册与热重载。

## 阶段 6：`pi-tui`

1. 定义 `Terminal`、`Component`、`Focusable`、`Overlay` 接口。
2. 实现 terminal raw mode、resize、title、cursor、bracketed paste、kitty keyboard protocol 基础支持。
3. 实现 diff renderer 与 synchronized output。
4. 实现 overlay stack、IME cursor marker、hardware cursor positioning。
5. 实现基础组件：`Container`、`Text`、`TruncatedText`、`Input`、`Editor`、`Markdown`、`Loader`、`SelectList`、`SettingsList`、`Image`。
6. 构建 `VirtualTerminal` 测试工具，做渲染与键位 golden tests。

## 阶段 7：`pi-cli` 与 `pi-sdk`

1. 实现 CLI 参数解析。
2. 实现 `interactive` mode。
3. 实现 `print` mode。
4. 实现 `json` mode。
5. 实现 `rpc` mode。
6. 实现 `pi-sdk` facade：`createAgentSession()`、`ModelRegistry`、`SessionManager`。
7. 实现 `list-models`、`resume`、`new`、`export`、`copy`、`tree`、`fork`、`compact`、`reload`。

## 阶段 8：行为追平

1. 追平 keybinding 语义与配置格式。
2. 追平 session selector、tree selector、model selector、settings selector。
3. 追平 footer token/cost/model 信息。
4. 追平 changelog、share、HTML export。
5. 追平 auto-compaction、retry、branch summary。

## 阶段 9：生态与分发

1. 定义 Java 版插件打包规范。
2. 定义 `skills / prompts / themes` 搜索路径与 package source 规范。
3. 评估 `maven:`、`file:`、`git:` 三类资源源。
4. 产出 fat JAR 分发。
5. 评估 `jpackage` 安装包。

## 里程碑建议

### M1：核心可用

- `pi-ai`
- `pi-agent-runtime`
- `pi-session`
- `read/bash/edit/write`
- `print/json` mode

### M2：交互可用

- `pi-tui`
- `interactive` mode
- session tree
- settings
- model selection

### M3：扩展可用

- plugin SPI
- resource reload
- custom tools / commands / renderers

### M4：生态可用

- 更多 providers
- package sources
- installer / distribution

## 验收标准

1. Java 版可读取并回放现有 Pi 的 session JSONL。
2. `read/bash/edit/write` 输出语义与现有 Pi 保持一致。
3. `AssistantMessageEvent` 与 `AgentEvent` 生命周期稳定、可订阅、可测试。
4. Interactive 模式下可完成完整 coding loop：提问、工具执行、会话持久化、恢复、tree 导航。
5. 至少一个 Java 插件可以注册工具和命令，并在 `/reload` 后恢复工作。
