# Java 版本 Pi 实施任务

## 开发流程原则

1. 默认采用 `TDD`，每个任务都按 `red -> green -> refactor` 执行。
2. 任何 bug 修复都先写失败测试，再改实现。
3. 新 provider、新工具、新 session 语义都必须先落 fixture 或 contract test。
4. 没有测试的代码可以作为实验代码存在，但不能作为阶段完成标准。
5. 每个阶段收尾都要先清空该阶段测试红灯，再进入下一阶段。
6. 具体测试模板、命名、fixture/golden 策略以 `docs/tdd.md` 为准。

## 当前任务状态

更新时间：2026-03-12

- 阶段 0：已完成。
- 阶段 1：已完成 `core model / event types`、`AssistantMessageEventStream / EventStream`、`ApiProviderRegistry / ModelRegistry / CredentialResolver / PiAiClient facade`、`AssistantMessage partial assembler`、`SSE parser / WebSocket adapter`、`openai-responses provider`、`openai-completions provider`、`anthropic-messages provider`、`google-generative-ai provider`、`bedrock-converse-stream provider`、`message transform / validation / compat` 抽象层、provider 交叉行为测试矩阵（`abort` / `handoff` / `image input` / `cross-provider coverage`）。
- 阶段 1：已收尾。
- 阶段 2：已完成 `AgentMessage / AgentTool / AgentToolResult / AgentState / AgentEvent / AgentLoopConfig` 核心类型、`AgentEventStream`、`convertToLlm / transformContext` 两阶段上下文管线、`AssistantMessageEvent -> AgentEvent` 生命周期桥接、顺序工具执行、基础 JSON Schema 参数校验、steering / follow-up 队列、`Agent` facade 与事件/状态订阅、interrupt / lifecycle tests。
- 阶段 3：已完成 `SessionHeader / SessionEntry / SessionTreeNode / SessionContext / SessionDocument`、`Session JSONL parse/write skeleton`、`v1 -> v2 -> v3 migration`、`buildSessionContext()` replay contract tests、`SessionManager` skeleton、session tree / fork / persisted append contract tests、fork / branched session file 提取、`Settings`/`SettingsManager`、global/project deep merge、file lock、settings migration tests、`AGENTS.md` / `CLAUDE.md` / `SYSTEM.md` / `APPEND_SYSTEM.md` 资源装配。
- 阶段 3：已收尾。
- 阶段 4：已完成 truncation / diff / shell / path policy / image resize primitives。
- 阶段 4：已完成 `read` 工具、图片魔数识别、文本 / 图片输出、`offset` / `limit` / 截断提示 contract tests。
- 阶段 4：已完成 `write` 工具、自动建目录 contract tests。
- 阶段 4：已完成 `edit` 工具、fuzzy/exact match、CRLF/BOM 保留、diff details contract tests。
- 阶段 4：已完成 `bash` 工具、streaming output、timeout、abort、tail truncation、full output temp file。
- 阶段 4：已完成 `grep` 工具、context/limit、long-line truncation、match-limit notices contract tests。
- 阶段 4：已完成 `find` 工具、hidden files、root .gitignore、result-limit notices contract tests。
- 阶段 4：已完成 `ls` 工具、dotfiles、directory suffix、entry-limit notices contract tests。
- 阶段 4：已完成内置工具 golden tests、details JSON 兼容（省略 `null` 字段、`truncatedBy` 小写）。
- 阶段 4：已收尾。
- 阶段 5：已完成 `ExtensionApi / ExtensionContext / ExtensionUiContext / ToolDefinition / CommandDefinition / MessageRenderer` 核心类型、`ServiceLoader + isolated ClassLoader` discovery skeleton、最小扩展加载 contract tests、扩展事件总线、typed event contract、handler capture / dispatch failure contract tests、tool / command / shortcut / flag / renderer 注册面收敛、资源发现扩展点、resource path normalization / failure capture contract tests、`ExtensionRuntime` `/reload` 生命周期接线、classloader 回收 contract tests、仓库内最小示例插件、热重载验证。
- 阶段 5：已收尾。
- 阶段 6：已完成 `Terminal / Component / Focusable / Overlay` 核心接口、`OverlayOptions / OverlayMargin / OverlayAnchor` 最小值对象、`InputHandler`、基础 contract tests。
- 阶段 6：已完成 `ProcessTerminal`、`TerminalInputBuffer`、JLine-backed raw mode / resize、title / cursor / clear、bracketed paste、kitty keyboard protocol 基础支持与 contract tests。
- 阶段 6：已完成 `DiffRenderer`、`SynchronizedOutput`、首帧/宽度变化/追加行/viewport 外变更的 differential rendering tests。
- 阶段 6：已完成 `Tui` 主骨架、overlay stack、`CURSOR_MARKER` 提取、hardware cursor positioning、focus restore、overlay lifecycle tests。
- 阶段 6：已完成第一批基础组件：`Container`、`Text`、`TruncatedText`，以及 `TerminalText` wrapping / truncation / background helpers。
- 阶段 6：已完成 `Input` 第一批：single-line editing、bracketed paste、word motion、delete/yank/undo、`EditorKeybindings` / `KeyMatcher` / `KillRing` / `UndoStack`、基础 input tests。
- 阶段 6：已完成 `Editor` 第一批：multiline editing、arrow navigation、newline / submit split、line merge、word delete / yank / undo、border rendering、focused cursor marker、基础 editor tests。
- 阶段 6：已完成 `Markdown` 第一批：`commonmark-java` + GFM tables/autolink/strikethrough、heading / list / blockquote / code block / table / link render、default text style / background、cache、基础 markdown tests。
- 阶段 6：已完成 `Loader` 第一批：spinner frames、`Tui.requestRender()` 驱动刷新、message update、blank spacer line、基础 loader tests。
- 阶段 6：已完成 `SelectList` 第一批：items/filter/selection model、wrap-around up/down、select/cancel callbacks、description single-line normalize、scroll indicator、基础 select-list tests。
- 阶段 6：已完成 `SettingsList` 第一批：label/value render、value cycle、submenu callback、optional search、description/hint render、基础 settings-list tests。
- 阶段 6：已完成 `Image` 第一批：terminal image capability detect、Kitty/iTerm2 sequence encode、base64 dimension detect、fallback placeholder、基础 image tests。
- 阶段 6：已完成 `VirtualTerminal` 第一批：lightweight ANSI emulator、viewport/scroll buffer/input/resize hooks、viewport-level render/key golden tests。
- 阶段 6：已收尾。
- 阶段 7：已开始，已完成 `pi-cli` CLI 参数解析（运行模式、通用启动选项、初始 file/message 输入拆分、未知 flag 保留）、`PiAgentSession` skeleton、最小 `interactive` mode shell（state render、prompt submit、session persistence bridge）、`print` mode（stdout/stderr output、final assistant selection）、`json` mode（JSONL event/state envelope）、`rpc` mode（command/response + notification JSONL）、`pi-sdk` facade（`PiSdk` / `PiSdkSession` / `createAgentSession()` / session helpers）、CLI startup dispatcher（`PiCliApplication`）、`list-models` command、resume/new session resolution primitives（`SessionManager.create/continueRecent`、`PiCliSessionResolver`）、`--resume` picker 首版（当前目录 session list + search + select/cancel）、`--export` HTML transcript 首版、`/copy` 首版（最近 assistant 文本复制到 OSC52 / system clipboard）、`/tree` 首版（session tree overlay + in-place leaf navigation + user message prefill）、`/fork` 首版（user-message selector + 新 session branch extraction + editor prefill）、`/compact` 首版（deterministic local summary + compaction entry append + context replay）、`/reload` 首版（settings reload + instruction resources reload + system prompt rebuild）、`--resume` all-sessions scope 首版（默认跨 project session roots 聚合）、`--resume` richer search 首版（token contains，覆盖 label/path/cwd）、`--resume` delete 首版（confirm + file delete + list refresh）、`--resume` rename 首版（rename mode + `session_info` append + list refresh）、`--export` richer HTML export 首版（metadata/sidebar/tree/full-entry render）、startup/session shell 共享核心收敛首版（`PiAgentSession` 复用 `PiSdkSession` bootstrap/persistence）、真实 `main()` / module wiring 首版（`PiCliModule.application()/run()` + `PiCliMain` + real handlers）、`/reload` extension runtime / startup pipeline 首版（显式 extension runtime reload + warning surface）、instruction-resource-aware system prompt 组合逻辑下沉首版（`CreateAgentSessionOptions.instructionResources` + `PiSdkSession` 统一 compose/update）、real module wiring 的 `@file` / initial prompt 首版（text+image file prompt build、interactive auto-submit、RPC 明确拒绝 `@file`）、real module wiring 的 `help/version` 输出首版（无 session 短路 + stable stdout routing）、interactive exit 语义首版（空输入 `Ctrl+D` / `/exit`、`run()` future 完成、shutdown hook 清理）。
- 阶段 8：已开始，已完成 session selector current/all scope toggle 首版（resolver 提供 `current/all` 双列表、picker 支持 `Tab` 切 scope）、sort toggle 首版（`Ctrl+S` 在 `recent/relevance` 间切换，relevance 基于 label/file/cwd/path token 命中排序）、named-only filter 首版（`Ctrl+N` 在 `all/named` 间切换，blank name 视为 unnamed）、path show/hide toggle 首版（`Ctrl+P` 切 path 描述显示）、threaded sort 首版（默认从 `threaded` 起步，`Ctrl+S` 第三态进入 `threaded`，空 query 时按 parentSessionPath 树状展开）、fuzzy parser/search 首版（quoted phrase、`re:` regex、subsequence fuzzy，搜索文本覆盖 `id/name/allMessagesText/cwd/path`）、keybindings.json loader 首版（CLI 启动时从 `~/.pi/agent/keybindings.json` 读取 session selector 相关键位覆盖）、session selector 单行顶栏宽度感知截断首版（窄终端下保留 scope summary，并对 title/status 做安全截断）、session selector metadata 响应式列宽首版（中等宽度下仍保留 `msg/age` 等描述列，避免过早退化成纯 label）、session selector 顶部 info/hint 行显式换行首版（窄终端下按词换行，不再依赖硬换列打断提示 token）、session selector ANSI 样式层级首版（selected row 用 accent/bold，metadata/scroll/no-match 用 muted）、session selector 顶部 status/error/info ANSI 首版（header title 用 bold、summary/hints 用 muted、delete confirm 用 warning、load error 用 error）、session selector summary 分段着色首版（active scope / loading progress 用 accent，inactive scope 分隔段保持 muted）、tree/fork selector 复用 ANSI 层级首版（overlay title 用 bold，hint 用 muted，list row 复用 session selector theme）、session selector regex parse error 首版（search 区上方显示 `Invalid regex`，并走 error 样式）、session selector empty/no-match/error 状态分层首版（loading/load-error 时不再退化成通用 no-match，blank query/no-result/invalid regex 分别显示 session-specific 状态文案）、session selector empty-state hint parity 首版（current scope empty / named-only empty 时补 `tab` 和 named-toggle 恢复提示，文案对齐 TS 行为）、selector metadata 右侧对齐首版（`SelectList` 会从实际 label 宽度回收空余列给 description，保留右侧 metadata，并留安全边距避免 terminal wrap）、interactive footer token/cost/model 首版（14 行以上终端显示一行 footer，累计 assistant usage 并展示 `↑/↓/R/W/$` 与当前 model / thinking 信息）、interactive footer context window indicator 首版（按最近 assistant usage 的 `totalTokens/contextWindow` 追加 `x%/window` 指标，并在高占用时切到 warning/error 样式）、interactive footer narrow-width truncation 首版（宽度不足时优先保留右侧 model 摘要，并把左侧 usage/context 指标截断到剩余空间）、interactive footer auto-compaction suffix 首版（根据 `/compaction/enabled` 在 context indicator 后追加 `(auto)`，并允许 session 级开关关闭）、interactive footer post-compaction unknown usage 首版（最近一次 compaction 后如果还没有新的 assistant usage，则把 context indicator 切到 `?/%window`，避免继续显示 stale pre-compaction 百分比）、interactive footer idle context baseline 首版（没有 assistant usage 时显示 `0.0%/%window`，并改成紧凑单行布局，避免宽终端 padding + ANSI 把右侧 model 摘要挤爆）、interactive footer provider-count gating 首版（只有多 provider 可用时才显示 `provider/` 前缀，单 provider 场景保持只显示 model id）、interactive footer cwd/session line 首版（15 行以上终端追加第二行 footer，显示 `cwd • session name`，并优先保留第一行 stats，避免关键统计被裁掉）、interactive footer cwd/session middle-truncation 首版（长 `cwd • session name` 现在走中间截断，优先保留路径起止和 session name，贴近 TS footer copy）、interactive footer git-branch 首版（footer 第二行会从 session cwd 解析 `.git/HEAD`，显示 `cwd (branch) • session name`，兼容普通 repo 和 worktree）、interactive footer git-branch watcher 首版（interactive mode 启动时监听 git HEAD 所在目录，branch 切换后自动重绘 footer）、interactive app keybinding `cycleModelBackward` 首版（新增反向 model cycle、`shift+ctrl+p` 默认键位、keybindings loader alias、interactive status 回显与 session 持久化）、interactive app keybinding `newSession` 首版（新增空白新 session 切换、keybindings loader alias、interactive 状态回显与后续 prompt sessionId 接线）。
- 阶段 9：未开始。

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
5. 实现 `SettingsManager`，支持 global/project deep merge 与 lock。（已完成）
6. 实现 `AGENTS.md` / `CLAUDE.md` / `SYSTEM.md` / `APPEND_SYSTEM.md` 资源装配。（已完成）
7. 增加 session tree replay、fork、compact、settings migration 测试。（已完成）

## 阶段 4：`pi-tools`

1. 实现 truncation、diff、shell、path policy、image resize 支撑类。（已完成）
2. 实现 `read` 工具，支持文本、图片、offset/limit、截断提示。（已完成）
3. 实现 `write` 工具，支持自动建目录。（已完成）
4. 实现 `edit` 工具，支持 exact match + diff details。（已完成）
5. 实现 `bash` 工具，支持 streaming output、timeout、abort、tail truncation、full output temp file。（已完成）
6. 实现 `grep`、`find`、`ls`。（已完成）
7. 增加内置工具 golden tests，确认输出格式与 TS 版本一致。（已完成）

## 阶段 5：`pi-extension-spi`

1. 定义 `ExtensionApi`、`ExtensionContext`、`ExtensionUiContext`、`ToolDefinition`、`CommandDefinition`、`MessageRenderer`。（已完成）
2. 定义插件发现协议：`ServiceLoader + isolated ClassLoader`。（已完成 skeleton）
3. 实现扩展事件总线。（已完成）
4. 实现 tool / command / shortcut / flag / renderer 注册。（已完成）
5. 实现资源发现扩展点：extensions 可返回附加资源路径。（已完成）
6. 实现 `/reload` 时 runtime 重建与 classloader 回收。（已完成）
7. 编写一个最小示例插件验证注册与热重载。（已完成）

## 阶段 6：`pi-tui`

1. 定义 `Terminal`、`Component`、`Focusable`、`Overlay` 接口。（已完成）
2. 实现 terminal raw mode、resize、title、cursor、bracketed paste、kitty keyboard protocol 基础支持。（已完成）
3. 实现 diff renderer 与 synchronized output。（已完成）
4. 实现 overlay stack、IME cursor marker、hardware cursor positioning。（已完成）
5. 实现基础组件：`Container`、`Text`、`TruncatedText`、`Input`、`Editor`、`Markdown`、`Loader`、`SelectList`、`SettingsList`、`Image`。（已完成）
6. 构建 `VirtualTerminal` 测试工具，做渲染与键位 golden tests。（已完成首版）

## 阶段 7：`pi-cli` 与 `pi-sdk`

1. 实现 CLI 参数解析。（已完成）
2. 实现 `interactive` mode。（已完成首版）
3. 实现 `print` mode。（已完成首版）
4. 实现 `json` mode。（已完成首版）
5. 实现 `rpc` mode。（已完成首版）
6. 实现 `pi-sdk` facade：`createAgentSession()`、`ModelRegistry`、`SessionManager`。（已完成首版）
7. 实现 CLI main entry / startup pipeline。（已完成 skeleton）
8. 实现 `list-models`。（已完成首版）
9. 实现 resume/new session resolution。（已完成首版：`--session` / `--continue` / `--session-dir`）
10. 实现 `--resume` picker。（已完成首版：当前目录 selector）
11. 实现 `export`。（已完成首版：basic HTML transcript export）
12. 实现 `copy`。（已完成首版：最近 assistant 文本复制）
13. 实现 `tree`。（已完成首版：overlay selector + in-place navigation）
14. 实现 `fork`。（已完成首版：user-message selector + branched session）
15. 实现 `compact`。（已完成首版：deterministic local summary）
16. 实现 `reload`。（已完成首版：settings / instruction resources reload）
17. 补 `--resume` all-sessions scope。（已完成首版：默认跨 project 聚合）
18. 补 `--resume` richer search。（已完成首版：token contains）
19. 补 `--resume` delete。（已完成首版：confirm + file delete）
20. 补 `--resume` rename。（已完成首版：rename mode + session_info append）
21. 补 richer HTML export。（已完成首版：metadata/sidebar/tree/full-entry render）
22. 收敛 startup/session shell 共享核心。（已完成首版：`PiAgentSession` 复用 `PiSdkSession` bootstrap/persistence）
23. 真实 `main()` / module wiring。（已完成首版：`PiCliModule.application()/run()` + `PiCliMain` + real handlers）
24. 把 `reload` 从 settings/resources 首版继续接到 extension runtime / startup pipeline。（已完成首版：显式 extension runtime reload + warning surface）
25. 继续下沉 instruction-resource-aware system prompt 组合逻辑。（已完成首版：`CreateAgentSessionOptions.instructionResources` + `PiSdkSession` 统一 compose/update）
26. 继续补 real module wiring 的 `@file` / initial prompt 语义。（已完成首版：text+image file prompt build、interactive auto-submit、RPC 明确拒绝 `@file`）
27. 继续补 real module wiring 的 `help/version` 输出路径。（已完成首版：无 session 短路 + stable stdout routing）
28. 继续补 real module wiring 的 interactive exit 语义。（已完成首版：空输入 `Ctrl+D` / `/exit`、`run()` future 完成、shutdown hook 清理）

## 阶段 8：行为追平

1. 追平 keybinding 语义与配置格式。（已完成增量：session selector loader/app-editor layering；interactive app actions interrupt/resume/tree/fork/cycleThinkingLevel/cycleModelForward/cycleModelBackward/selectModel/followUp/dequeue/newSession；`shift+tab` matcher；`shift+ctrl+p` matcher；`ctrl+l` matcher；`alt+up` matcher；`--models` scoped cycle first cut）
2. 追平 session selector、tree selector、model selector、settings selector。（已开始：session selector current/all scope toggle、sort toggle、named/path toggle、threaded/fuzzy、keybindings loader、loading/progress header、TS-style fuzzy scoring、search text scope parity、header/status/hint 文案、app/editor keybinding 分层、path/cwd metadata、single-row title/scope header、single-row 顶栏宽度感知截断、metadata 响应式列宽、顶部 info/hint 行显式换行、ANSI 样式层级、顶部 status/error/info ANSI、summary 分段着色、tree/fork selector 复用 ANSI 层级、regex parse error 状态；interactive app keybindings 已扩到 interrupt/resume/tree/fork/cycleThinkingLevel/cycleModelForward/cycleModelBackward/selectModel/followUp/dequeue/newSession；`--models` scope resolve + forward cycle 首版；model selector overlay first cut；pending follow-up queue status first cut）
3. 追平 footer token/cost/model 信息。（已完成前十三刀：single-line usage/cost/model footer；footer ANSI hierarchy + reasoning summary；provider-aware model summary first cut；context window indicator first cut；narrow-width truncation first cut；auto-compaction suffix first cut；post-compaction unknown usage first cut；idle context baseline first cut；provider-count gating first cut；cwd/session line first cut；cwd/session middle-truncation first cut；git-branch first cut；git-branch watcher first cut）
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
