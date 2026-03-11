# 阶段 8 交接：行为追平

更新时间：2026-03-11

## 当前状态

- 阶段 8 已开始。
- 已完成第一刀：
  - session selector current/all scope toggle first cut
- 已完成第二刀：
  - session selector sort toggle first cut
- 已完成第三刀：
  - session selector named-only filter first cut
- 已完成第四刀：
  - session selector path show/hide toggle first cut

## 本轮落地

- `PiCliSessionResolver` 现在在 `--resume` 路径下会同时准备：
  - current scope：当前项目的默认 session 目录
  - all scope：全局 `~/.pi/agent/sessions/*` 聚合结果
- `PiSessionPicker` 现在支持双 scope：
  - 默认从 current scope 启动
  - `Tab` 切到 all scope
  - all scope 下会显示 `cwd`
  - current/all 同源时保持旧的 `cwd` 展示，避免 UI 回退
- `pi-tui` 层新增 `SESSION_SCOPE_TOGGLE` action，默认键位是 `tab`
- `PiSessionPicker` 现在支持 sort toggle：
  - 默认 `recent`
  - `Ctrl+S` 切到 `relevance`
  - relevance 只在有搜索 query 时生效
  - relevance 目前按 `label -> fileName -> cwd -> path` 的 token 命中位置打分
- `pi-tui` 层新增 `SESSION_SORT_TOGGLE` action，默认键位是 `ctrl+s`
- `PiSessionPicker` 现在支持 named-only filter：
  - 默认 `all`
  - `Ctrl+N` 切到 `named`
  - blank / whitespace-only `name` 会被视为 unnamed
- `pi-tui` 层新增 `SESSION_NAMED_FILTER_TOGGLE` action，默认键位是 `ctrl+n`
- `PiSessionPicker` 现在支持 path show/hide：
  - 默认 `off`
  - `Ctrl+P` 切到 `on`
  - path 会追加到 session description，便于区分同名 session 文件
- `pi-tui` 层新增 `SESSION_PATH_TOGGLE` action，默认键位是 `ctrl+p`
- `KeyMatcher` 现在显式支持 `tab`
- `KeyMatcher` 现在显式支持 `ctrl+s`
- `KeyMatcher` 现在显式支持 `ctrl+n`
- `KeyMatcher` 现在显式支持 `ctrl+p`
- `PiSessionPicker.filterAndSortSessions()` 现在抽成包内静态辅助方法，直接用单元测试兜住 `recent/relevance` 排序语义

## 当前边界

- 这还是首版 scope toggle：
  - 还没有 TS 版的 loading/progress header
  - sort toggle 还不是 TS 的完整三态（还没有 `threaded` / `fuzzy`）
  - named-only filter 还没有接到 app-level keybinding/config 层，当前先挂在 `EditorKeybindings`
  - path show/hide 目前只是 description 级别开关，还没有 TS 版右侧布局/缩略路径渲染
- resolver 现在只在 `--session-dir` 未显式指定时提供 current/all 双 scope；显式 `--session-dir` 仍退化成单 scope

## 测试

本轮新增/更新覆盖：

- `PiCliSessionResolverTest`：picker 现在接收 `current/all` 双列表
- `PiSessionPickerTest`：`Tab` scope toggle、current/all 渲染与选择
- `PiSessionPickerTest`：`Ctrl+S` sort toggle、recent/relevance 排序切换
- `PiSessionPickerTest`：`Ctrl+N` named-only filter、blank name 排除
- `PiSessionPickerTest`：`Ctrl+P` path show/hide toggle
- `PiSessionPickerTest`：`filterAndSortSessions()` 直接覆盖 recent/relevance 排序语义

最近通过：

```bash
.\gradlew.bat :pi-tui:test :pi-cli:test --no-daemon
```

## 下一步建议

1. session selector：补 threaded/fuzzy sort mode
2. session selector：评估 app-level keybinding/config 对齐
3. session selector：补 TS 风格 loading/progress header
