# pi-java 交接文档

更新时间：2026-03-10

## 当前状态

`pi-java` 当前状态：

- 阶段 0 到阶段 5：已收尾。
- 阶段 6 `pi-tui`：已收尾，已完成 core contracts、terminal base support、diff renderer、overlay/cursor、`Container`/`Text`/`TruncatedText`、`Input`、`Editor`、`Markdown`、`Loader`、`SelectList`、`SettingsList`、`Image`、`VirtualTerminal`。
- 阶段 7 `pi-cli` / `pi-sdk`：已开始，已完成 `pi-cli` CLI 参数解析首版。
- 阶段 8 到阶段 9：未开始。

## 文档结构

后续交接改为按阶段增量维护：

- `pi-java/docs/handoff/README.md`
- `pi-java/docs/handoff/phase-6-pi-tui.md`
- `pi-java/docs/handoff/phase-7-pi-cli-sdk.md`
- `pi-java/docs/handoff/archive-2026-03-10.md`

其中：

- `handoff.md` 只保留当前总览和入口。
- `docs/handoff/phase-6-pi-tui.md` 维护 `pi-tui` 的持续交接。
- `docs/handoff/archive-2026-03-10.md` 保留拆分前的历史完整交接。

## 当前推荐阅读顺序

1. `pi-java/docs/tasks.md`
2. `pi-java/docs/handoff/phase-7-pi-cli-sdk.md`
3. `pi-java/docs/handoff/phase-6-pi-tui.md`
4. `pi-java/docs/handoff/archive-2026-03-10.md`

## 当前验证

最近持续通过的命令：

```bash
.\gradlew.bat :pi-cli:test --no-daemon
.\gradlew.bat :pi-tui:test --no-daemon
npm.cmd run check
```

更早阶段的完整历史验证记录已归档到：

- `pi-java/docs/handoff/archive-2026-03-10.md`

## 建议下一步

按 `docs/tasks.md` 当前顺序，下一刀建议：

1. `pi-cli`：`interactive` mode
2. `pi-cli`：`print` / `json` / `rpc`
3. `pi-sdk` facade
