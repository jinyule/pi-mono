# 阶段 8 交接：行为追平

更新时间：2026-03-11

## 当前状态

- 阶段 8 已开始。
- 已完成第一刀：
  - session selector current/all scope toggle first cut

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
- `KeyMatcher` 现在显式支持 `tab`

## 当前边界

- 这还是首版 scope toggle：
  - 还没有 TS 版的 loading/progress header
  - 还没有 sort mode toggle
  - 还没有 named-only filter toggle
  - 还没有 path show/hide toggle
- resolver 现在只在 `--session-dir` 未显式指定时提供 current/all 双 scope；显式 `--session-dir` 仍退化成单 scope

## 测试

本轮新增/更新覆盖：

- `PiCliSessionResolverTest`：picker 现在接收 `current/all` 双列表
- `PiSessionPickerTest`：`Tab` scope toggle、current/all 渲染与选择
- `KeyMatcherTest`：`tab` key sequence

最近通过：

```bash
.\gradlew.bat :pi-tui:test :pi-cli:test --no-daemon
```

## 下一步建议

1. session selector：补 sort toggle
2. session selector：补 named-only filter
3. session selector：补 path show/hide toggle
