# 阶段 7 交接：`pi-cli` / `pi-sdk`

更新时间：2026-03-10

## 当前状态

- 阶段 7 已开始。
- 已完成前六刀：
  - `pi-cli` CLI 参数解析
  - `PiAgentSession` skeleton + 最小 `interactive` mode
  - `print` mode
  - `json` mode
  - `rpc` mode
  - `pi-sdk` facade
- 已完成第七刀：
  - CLI startup dispatcher skeleton
- 已完成第八刀：
  - `list-models` command
- 已完成第九刀：
  - resume/new session resolution primitives
- 已完成第十刀：
  - `--resume` picker first cut
- 已完成第十一刀：
  - `--export` first cut
- 已完成第十二刀：
  - `/copy` first cut
- 已完成第十三刀：
  - `/tree` first cut
- 已完成第十四刀：
  - `/fork` first cut
- 已完成第十五刀：
  - `/compact` first cut
- 已完成第十六刀：
  - `/reload` first cut

## 已落地内容

新增：

- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiCliMode.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiCliThinkingLevel.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiCliArgs.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiCliParser.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiInteractiveSession.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiAgentSession.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiInteractiveMode.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiMessageRenderer.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiPrintMode.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiJsonMode.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiRpcMode.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiCliApplication.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiListModelsCommand.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiCliSessionResolver.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiSessionPicker.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiExportCommand.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiClipboard.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiCopyCommand.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiTreeSelector.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiForkSelector.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiCompactor.java`
- `pi-java/modules/pi-sdk/src/main/java/dev/pi/sdk/CreateAgentSessionOptions.java`
- `pi-java/modules/pi-sdk/src/main/java/dev/pi/sdk/PiSdk.java`
- `pi-java/modules/pi-sdk/src/main/java/dev/pi/sdk/PiSdkSession.java`
- `pi-java/modules/pi-cli/src/test/java/dev/pi/cli/PiCliParserTest.java`
- `pi-java/modules/pi-cli/src/test/java/dev/pi/cli/PiAgentSessionTest.java`
- `pi-java/modules/pi-cli/src/test/java/dev/pi/cli/PiInteractiveModeTest.java`
- `pi-java/modules/pi-cli/src/test/java/dev/pi/cli/PiPrintModeTest.java`
- `pi-java/modules/pi-cli/src/test/java/dev/pi/cli/PiJsonModeTest.java`
- `pi-java/modules/pi-cli/src/test/java/dev/pi/cli/PiRpcModeTest.java`
- `pi-java/modules/pi-cli/src/test/java/dev/pi/cli/PiCliApplicationTest.java`
- `pi-java/modules/pi-cli/src/test/java/dev/pi/cli/PiListModelsCommandTest.java`
- `pi-java/modules/pi-cli/src/test/java/dev/pi/cli/PiCliSessionResolverTest.java`
- `pi-java/modules/pi-cli/src/test/java/dev/pi/cli/PiSessionPickerTest.java`
- `pi-java/modules/pi-cli/src/test/java/dev/pi/cli/PiExportCommandTest.java`
- `pi-java/modules/pi-cli/src/test/java/dev/pi/cli/PiCopyCommandTest.java`
- `pi-java/modules/pi-sdk/src/test/java/dev/pi/sdk/PiSdkTest.java`

调整：

- `pi-java/modules/pi-agent-runtime/src/main/java/dev/pi/agent/runtime/Agent.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiCliModule.java`
- `pi-java/modules/pi-cli/src/test/java/dev/pi/cli/PiCliModuleTest.java`
- `pi-java/modules/pi-session/src/main/java/dev/pi/session/SessionManager.java`
- `pi-java/modules/pi-session/src/test/java/dev/pi/session/SessionManagerTest.java`

## 已具备的参数能力

- 默认 `interactive` 模式，以及 `--print` / `--mode print|json|rpc|interactive`。
- 兼容 `--mode text`，映射到 `print`。
- 解析通用启动选项：`provider`、`model`、`apiKey`、`systemPrompt`、`appendSystemPrompt`、`thinking`、session 相关选项、tools/resources 相关选项、`--list-models`、`--offline`、`--verbose`。
- 将 positional 参数拆成两类：
  - `@file` -> `fileArgs`
  - 其余 -> `messages`
- 未知 flag 不再报错；当前会保留在 `unmatchedArguments`，以便后续接 extension flag registry。

## 已具备的 interactive 能力

- `PiAgentSession` 已把 `Agent`、`SessionManager`、`SettingsManager`、instruction resources 串成最小可用会话壳。
- startup 时会 replay `SessionContext` 到 `Agent`，并把 context file / system prompt / append prompts 合成首版 system prompt。
- 新消息通过 `AgentEvent.MessageEnd` 回写到 `SessionManager`，形成基础 session persistence bridge。
- `PiInteractiveMode` 已接上 `Tui + Input + Text`，具备：
  - header/status render
  - transcript render
  - prompt submit
  - escape -> abort
  - `/copy` -> 最近 assistant 文本复制到剪贴板
  - `/tree` -> session tree overlay、entry select、in-place leaf navigation
  - `/fork` -> user-message selector、新 session fork、editor prefill
  - `/compact` -> 手动 compaction、summary replay、状态提示
  - `/reload` -> settings reload、instruction resources reload、system prompt 重建
  - fake session / virtual terminal contract tests
- `PiAgentSession` 现在已具备最小 tree navigation 语义：
  - 暴露当前 `leafId()` 与 `tree()`
  - 选择 assistant / compaction / branch summary 等节点时直接切 leaf 并 replay context
  - 选择 user message 时切到其 parent，并把 user 文本预填回 editor，形成原位分支
- `PiAgentSession` 现在也已具备最小 fork 语义：
  - 暴露 `forkMessages()` 用户消息列表
  - 选择 user message 后，基于其 parent path 提取新 session document
  - root user fork 会落到空会话，再由 metadata seed 保持 model/thinking 轨迹
  - fork 后立即更新 `Agent.sessionId`，避免后续 provider request 继续带旧 session id
- `PiAgentSession` 现在已具备最小 manual compaction 语义：
  - `/compact` 当前走本地 deterministic summary，不依赖额外 LLM 调用
  - 默认保留最近一个 user turn，从最新 compaction 边界之后向前折叠
  - 追加 `CompactionEntry` 后，立即用 `SessionManager.buildSessionContext()` replay 到 `Agent`
- `PiAgentSession` 现在也已具备最小 manual reload 语义：
  - `settingsManager.reload()` 会刷新 global/project settings snapshot
  - 若配置了 `InstructionResourceLoader`，`reload()` 会重新读取 `AGENTS.md` / `CLAUDE.md` / `SYSTEM.md` / `APPEND_SYSTEM.md`
  - reload 后会用新的 instruction resources 重新拼接 system prompt，并热更新到 `Agent`
  - 当前返回 settings/resource warning 列表，供上层 UI 做状态提示

## 当前边界

- 这还是首版 interactive shell，不包含完整 coding-agent UI。
- 还没接 model discovery / settings-driven startup pipeline / extension runtime / built-in tools registry。
- transcript renderer 目前是 plain-text flatten，不是 `Markdown` / rich message renderer。
- 还没有把 `PiCliParser`、session/model resolution、`PiInteractiveMode` 串成真正的 CLI main entry。
- `print` mode 目前输出 final assistant text，不做 token-by-token streaming。
- `json` mode 当前输出的是最小归一化 JSONL，不是最终 RPC schema，也不包含完整 tool/state payload。
- `rpc` mode 当前只覆盖最小命令集：`prompt` / `state` / `resume` / `abort`。
- `pi-sdk` facade 当前与 `pi-cli` 的 session shell 逻辑仍有重复，后续可考虑收敛到共享核心。
- `PiCliApplication` 当前只负责 `parse -> create session -> dispatch mode` 的启动编排，尚未接真实 `main()`、DI 装配、settings/model resolution 和 built-in tool/runtime bootstrap。
- `list-models` 当前直接消费 `ModelRegistry` 并输出文本表格，尚未接 settings/auth 过滤，也还没挂到真实 CLI `main()`。
- `PiCliSessionResolver` 当前已覆盖 `--session` / `--continue` / `--session-dir` / `--no-session`，并已接上当前目录范围的 `--resume` picker。
- `PiSessionPicker` 当前只覆盖当前目录 sessions、prefix filter、up/down/enter/esc；all-scope toggle、fuzzy/regex search、rename/delete 仍未落地。
- `PiExportCommand` 当前输出的是 basic standalone HTML transcript；还未追平 TS 版的 tree sidebar、theme colors、tool rich render、JSONL download、branch highlighting。
- `/copy` 当前只复制最近一条 assistant 的 plain-text flatten 文本；未覆盖图片块、富文本选择、历史消息 picker，也还未抽出统一 slash-command registry。
- `/tree` 当前是首版 selector：只覆盖 prefix search、up/down/enter/esc、基础树前缀渲染和当前 leaf 高亮；尚未接 TS 版的 summarize prompt、custom prompt、label edit、user-only/all-entry filter toggle、bookmark 语义。
- `/fork` 当前也是首版 selector：只覆盖 prefix search、up/down/enter/esc 与 flat user-message list；尚未接 extension `session_before_fork` / `session_fork` 生命周期、double-escape action、RPC `fork/get_fork_messages`、cross-project `forkFrom`。
- `/compact` 当前只覆盖手动 compaction；尚未接 TS 版的 LLM summary、`session_before_compact` / `session_compact` 生命周期、auto-compaction threshold、cancel/abort、file-op details、split-turn handling。
- `/reload` 当前只覆盖 settings / instruction resources 首版重载；尚未接 TS 版的 extension runtime rebuild、theme/skills/prompts registry rebuild、loaded-resource diagnostics 面板与完整 startup pipeline。

## 已确认语义

- `--print` 与 `--mode` 冲突时，只有 `print` 可组合，其他模式直接报错。
- `--mode text` 作为 TS 兼容别名保留。
- 未知 flag 当前按 TS 第一遍解析语义处理：flag 自身被保留，但后续普通参数仍按 message 处理。
- `PiCliThinkingLevel` 保留 `off`，后续可在 runtime 层映射为“无 reasoning”。
- `PiCliApplication` 的 `SessionFactory` 与 `ModeHandler` 采用注入式接口，优先保证 main entry 可测试，再在后续切片接真实模块装配。
- `list-models` 优先于 session 创建执行，避免只为列模型启动 session/runtime。
- `list-models` query 当前采用大小写不敏感的 subsequence fuzzy match，覆盖 provider + model id。
- `--session <path>` 现在对齐 TS 语义：文件存在则打开，不存在则用该路径创建新 persistent session。
- `--continue` 现在会在 session 目录内选择最近的有效 JSONL session；若目录为空则创建新 session。
- `SessionManager.list(dir)` 现在会提取 session 元数据（name / firstMessage / modified / messageCount / allMessagesText），供 picker 和后续 selector 复用。
- `--resume` 当前只在 `sessionDirectory` 范围内做选择；未实现 TS 版的 current/all scope 切换。
- `--export <session.jsonl> [output.html]` 现在会在 session/runtime 之外短路执行，默认输出 `pi-java-session-<basename>.html`。
- `/copy` 优先同时写入 OSC52 与 system clipboard；只要任一后端成功即视为成功。
- `/tree` 首版默认隐藏 `label` / `session_info` / `model_change` / `thinking_level_change` / `custom` 等非导航主节点，只显示 message / compaction / branch summary / custom_message。
- `/tree` 目前不做 branch summarization；切 leaf 仅更新内存中的 session leaf，并立即用 `SessionManager.buildSessionContext()` replay 到 `Agent`。
- `/fork` 只允许从 user message 发起；新 session 以该消息的 parent 为 branched path，因此 editor 预填的是被选中的 user 文本，conversation state 恢复的是它之前的上下文。
- `/compact` 当前 summary 文本结构为固定章节模板（Goal / Custom Focus / Previous Summary / Summarized Messages），`tokensBefore` 采用基于序列化文本长度的轻量估算。
- `/reload` 当前在非 streaming 状态下可执行；会刷新 `SettingsManager`、可选 `InstructionResourceLoader`，并把最新 system prompt 热更新到当前 `Agent`，但不会重建扩展 runtime。

## 测试

本轮新增 contract tests 覆盖：

- interactive 默认模式
- `@file` / `message` 拆分
- print 模式启动参数
- `--list-models` 可选 query
- `--mode text` 兼容
- `--print` / `--mode` 冲突
- 未知 extension flags 保留
- session context replay -> agent state
- prompt 后 session persistence bridge
- virtual terminal 下的 interactive header / prompt submit render
- print mode 的 stdout/stderr 选择与 blank prompt 校验
- json mode 的 event/state envelope 与 blank prompt 校验
- rpc mode 的 command/response、状态读取、错误响应
- sdk facade 的 session helpers 与 `createAgentSession()` 集成
- cli startup dispatcher 的 mode dispatch 与参数透传
- list-models 的无 session 分流、表格输出、query filter
- session resolver 的 explicit path open/create、continueRecent、default new-session path 分配
- current-directory `--resume` picker，以及 `SessionInfo` 元数据列表
- basic HTML export command
- copy command 的最近 assistant 选择、空文本/无 assistant 校验、interactive slash-command dispatch
- tree navigation 的 assistant/user 分流语义、interactive overlay 选择、user message prefill 后继续提交流程
- fork root-branch rewrite、new session id propagation、interactive fork selector 与 fork 后继续提交流程
- compact entry append、summary replay、manual `/compact <instructions>` slash-command 行为
- reload 后 settings snapshot / instruction resources / system prompt 更新，以及 interactive `/reload` slash-command 行为

## 验证

最近通过：

```bash
.\gradlew.bat :pi-cli:test --no-daemon
npm.cmd run check
```

## 下一步建议

按依赖顺序，下一刀建议进入 CLI 收口：

1. `--resume` all-sessions scope / richer search / session mutations，以及 richer HTML export。
2. 真实 `main()` / module wiring，把 `PiCliApplication`、`list-models`、session resolver / picker / export 接到启动入口。
3. 把 `reload` 从 settings/resources 首版继续接到 extension runtime / startup pipeline。
