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
- 已完成第八刀：
  - session selector loading/progress header first cut
- 已完成第九刀：
  - session selector TS-style fuzzy scoring first cut
- 已完成第十刀：
  - session selector search text scope parity first cut
- 已完成第十一刀：
  - session selector header/status/hint copy first cut

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
- `PiCliSessionResolver` 现在把 `--resume` 的 current/all scope 改成 loader：
  - current scope 启动时立即加载
  - all scope 只在切 scope 时触发加载
  - all scope 聚合时会按 project 目录回报 `loaded/total` 进度
- `PiSessionPicker` 现在支持 TS 风格 loading/progress header 首版：
  - 初次进入时，current scope 未返回前会显示 `Loading current ...`
  - 切到 all scope 且仍在加载时，会显示 `Loading x/y`
  - loader 异常会显示 `Load error: ...`
  - direct `pick(List, List)` 调用会走预加载路径，保持原有同步渲染和选择语义，不把旧调用点意外变成异步
- `PiSessionPicker` 现在把 fuzzy scoring 对齐到 TS `packages/tui/src/fuzzy.ts` 首版：
  - 连续命中会得到更好的分数
  - 单词边界命中会得到更好的分数
  - gap 会按距离加罚分
  - 晚出现的命中会按位置轻微加罚分
  - `letters+digits` / `digits+letters` query 现在支持一次 alpha-numeric swapped fallback，例如 `codex52 -> 52codex`
- `PiSessionPicker` 现在把 session selector search text 范围收敛到和 TS 一致的字段：
  - 搜索只覆盖 `id/name/allMessagesText/cwd`
  - `path` 仍可显示在 UI description 里，但不再参与匹配/排序
- `PiSessionPicker` 现在把 header/status/hint 文案继续往 TS 靠：
  - delete confirm 文案改成 `Delete session? [Enter] confirm · [Esc] cancel`
  - load error 文案改成 `Failed to load sessions: ...`
  - 新增搜索提示行：`tab scope · re:<pattern> regex · "phrase" exact · ...`
  - 保留现有 `sort(named/path)` 状态行，避免 Java 版当前 selector 布局回退
- `KeyMatcher` 现在显式支持 `tab`
- `KeyMatcher` 现在显式支持 `ctrl+s`
- `KeyMatcher` 现在显式支持 `ctrl+n`
- `KeyMatcher` 现在显式支持 `ctrl+p`
- `PiSessionPicker.filterAndSortSessions()` 现在抽成包内静态辅助方法，直接用单元测试兜住 `recent/relevance` 排序语义

## 当前边界

- 这还是首版 scope toggle：
  - cycle/default 现在已基本对齐 TS，但 loading/progress header 还只是首版
  - keybindings 现在有文件加载首版，但还没有完整的 app/editor 分层 manager
  - path show/hide 目前只是 description 级别开关，还没有 TS 版右侧布局/缩略路径渲染
  - header 布局还是 Java 当前的纵向 3-4 行，不是 TS 那种 title-left / status-right 的单行顶栏
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
- `PiSessionPickerTest`：initial current loading header、all-scope progress header
- `PiCliSessionResolverTest`：loader-style picker stub 覆盖 current/all lazy load
- `PiSessionPickerTest`：连续命中、word boundary、alpha-numeric swapped fuzzy scoring/匹配
- `PiSessionPickerTest`：`path` 只显示不参与搜索
- `PiSessionPickerTest`：TS 风格搜索/动作提示文案渲染
- `PiCliModuleTest`：从临时 agent dir 加载 `keybindings.json` 覆盖

最近通过：

```bash
.\gradlew.bat :pi-cli:test --no-daemon
```

## 下一步建议

1. session selector：继续细化 app/editor keybinding 分层
2. session selector：补更接近 TS 的 path/cwd 布局细节
3. session selector：如果要继续追平，再考虑 title-left / status-right 顶栏布局
