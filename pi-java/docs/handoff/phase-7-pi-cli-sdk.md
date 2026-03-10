# 阶段 7 交接：`pi-cli` / `pi-sdk`

更新时间：2026-03-10

## 当前状态

- 阶段 7 已开始。
- 本轮已完成第一刀：`pi-cli` CLI 参数解析。
- `pi-sdk` 仍未开始实现。

## 本轮落地内容

新增：

- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiCliMode.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiCliThinkingLevel.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiCliArgs.java`
- `pi-java/modules/pi-cli/src/main/java/dev/pi/cli/PiCliParser.java`
- `pi-java/modules/pi-cli/src/test/java/dev/pi/cli/PiCliParserTest.java`

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

## 已确认语义

- `--print` 与 `--mode` 冲突时，只有 `print` 可组合，其他模式直接报错。
- `--mode text` 作为 TS 兼容别名保留。
- 未知 flag 当前按 TS 第一遍解析语义处理：flag 自身被保留，但后续普通参数仍按 message 处理。
- `PiCliThinkingLevel` 保留 `off`，后续可在 runtime 层映射为“无 reasoning”。

## 测试

本轮新增 contract tests 覆盖：

- interactive 默认模式
- `@file` / `message` 拆分
- print 模式启动参数
- `--list-models` 可选 query
- `--mode text` 兼容
- `--print` / `--mode` 冲突
- 未知 extension flags 保留

## 验证

最近通过：

```bash
.\gradlew.bat :pi-cli:test --no-daemon
npm.cmd run check
```

## 下一步建议

按依赖顺序，下一刀直接进入 `pi-cli` `interactive` mode：

1. 定义 `PiAgentSession` skeleton，把 `Agent` / `SessionManager` / settings / resources 串起来。
2. 建立 CLI startup pipeline：parse args -> load settings/resources -> create session -> choose mode。
3. 先接最小 interactive shell，再把 `pi-tui` 容器接进去。
