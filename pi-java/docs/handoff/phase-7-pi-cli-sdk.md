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
- `pi-java/modules/pi-sdk/src/test/java/dev/pi/sdk/PiSdkTest.java`

调整：

- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiCliModule.java`
- `pi-java/modules/pi-cli/src/test/java/dev/pi/cli/PiCliModuleTest.java`

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
  - fake session / virtual terminal contract tests

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

## 已确认语义

- `--print` 与 `--mode` 冲突时，只有 `print` 可组合，其他模式直接报错。
- `--mode text` 作为 TS 兼容别名保留。
- 未知 flag 当前按 TS 第一遍解析语义处理：flag 自身被保留，但后续普通参数仍按 message 处理。
- `PiCliThinkingLevel` 保留 `off`，后续可在 runtime 层映射为“无 reasoning”。
- `PiCliApplication` 的 `SessionFactory` 与 `ModeHandler` 采用注入式接口，优先保证 main entry 可测试，再在后续切片接真实模块装配。

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

## 验证

最近通过：

```bash
.\gradlew.bat :pi-cli:test --no-daemon
npm.cmd run check
```

## 下一步建议

按依赖顺序，下一刀建议进入 CLI 收口：

1. `list-models` / `resume` / `new` 等 CLI command。
2. `export` / `copy` / `tree` / `fork` / `compact` / `reload`。
3. 真实 `main()` / module wiring，把 `PiCliApplication` 接到启动入口。
