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
- 已完成第五刀：
  - session selector threaded sort first cut
- 已完成第六刀：
  - session selector fuzzy parser/search first cut
- 已完成第七刀：
  - keybindings.json loader first cut

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
- `PiSessionPicker` 现在支持 threaded sort：
  - 当前默认从 `threaded` 起步
  - 当前 cycle 顺序是 `threaded -> recent -> fuzzy -> threaded`
  - `threaded` 只在空 query 时启用树状布局
  - 非空 query 时，`threaded` 会先退化到当前的 `relevance` 首版
  - 树状展开按 `parentSessionPath` 建树，根/子节点按 `modified desc` 排序
- `PiSessionPicker` 现在支持 fuzzy parser/search：
  - `recent` / `fuzzy` 都会走 picker 自己的 parser，不再依赖 `SelectList` 的 contains-only 过滤
  - 支持 quoted phrase（带空白归一化）
  - 支持 `re:` case-insensitive regex
  - 支持简单 subsequence fuzzy
  - 搜索文本覆盖 `id/name/allMessagesText/cwd/path`
- `PiCliModule` 现在会在启动前加载 `~/.pi/agent/keybindings.json`
  - 当前首版支持 session selector 相关 alias：`toggleSessionSort`、`toggleSessionNamedFilter`、`toggleSessionPath`、`renameSession`、`deleteSession`、`tab`
  - 也接受 `EditorAction` 枚举名直接覆盖
  - 加载失败会静默回退到默认键位
- `KeyMatcher` 现在显式支持 `tab`
- `KeyMatcher` 现在显式支持 `ctrl+s`
- `KeyMatcher` 现在显式支持 `ctrl+n`
- `KeyMatcher` 现在显式支持 `ctrl+p`
- `PiSessionPicker.filterAndSortSessions()` 现在抽成包内静态辅助方法，直接用单元测试兜住 `recent/relevance` 排序语义

## 当前边界

- 这还是首版 scope toggle：
  - 还没有 TS 版的 loading/progress header
  - fuzzy 现在已经支持 quoted phrase / regex / subsequence，但 scoring 还不是 TS `@mariozechner/pi-tui` 的同款实现
  - cycle/default 现在已基本对齐 TS，但 loading/progress header 仍缺失
  - keybindings 现在有文件加载首版，但还没有完整的 app/editor 分层 manager
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
- `PiSessionPickerTest`：`Ctrl+S` 第三态进入 threaded tree 渲染
- `PiSessionPickerTest`：quoted phrase / `re:` regex / subsequence fuzzy 搜索
- `PiCliModuleTest`：从临时 agent dir 加载 `keybindings.json` 覆盖

最近通过：

```bash
.\gradlew.bat :pi-tui:test :pi-cli:test --no-daemon
```

## 下一步建议

1. session selector：补 TS 风格 loading/progress header
2. session selector：进一步对齐 fuzzy scoring
3. session selector：继续细化 app/editor keybinding 分层
