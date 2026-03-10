# pi-java 交接文档

更新时间：2026-03-10

## 当前状态

`pi-java` 当前状态：

- 阶段 0 到阶段 5：已收尾。
- 阶段 6 `pi-tui`：进行中，已完成 core contracts、terminal base support、diff renderer、overlay/cursor、`Container`/`Text`/`TruncatedText`、`Input`、`Editor`。
- 阶段 7 到阶段 9：未开始。

## 文档结构

后续交接改为按阶段增量维护：

- `pi-java/docs/handoff/README.md`
- `pi-java/docs/handoff/phase-6-pi-tui.md`
- `pi-java/docs/handoff/archive-2026-03-10.md`

其中：

- `handoff.md` 只保留当前总览和入口。
- `docs/handoff/phase-6-pi-tui.md` 维护 `pi-tui` 的持续交接。
- `docs/handoff/archive-2026-03-10.md` 保留拆分前的历史完整交接。

## 当前推荐阅读顺序

1. `pi-java/docs/tasks.md`
2. `pi-java/docs/handoff/phase-6-pi-tui.md`
3. `pi-java/docs/handoff/archive-2026-03-10.md`

## 当前验证

最近持续通过的命令：

```bash
.\gradlew.bat :pi-tui:test --no-daemon
npm.cmd run check
```

更早阶段的完整历史验证记录已归档到：

- `pi-java/docs/handoff/archive-2026-03-10.md`

## 建议下一步

按 `docs/tasks.md` 当前顺序，下一刀建议：

1. `pi-tui`：`Markdown`
2. `pi-tui`：`Loader` / `SelectList` / `SettingsList`
3. `pi-tui`：`Image` / `VirtualTerminal` / golden tests
