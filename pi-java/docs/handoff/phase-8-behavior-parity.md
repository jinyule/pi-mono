# 阶段 8 交接：行为追平

更新时间：2026-03-12

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
- 已完成第十二刀：
  - session selector app/editor keybinding layering first cut
- 已完成第十三刀：
  - session selector path/cwd metadata layout first cut
- 已完成第十四刀：
  - session selector single-row title/scope header first cut
- 已完成第十五刀：
  - interactive app keybindings 扩到 interrupt/tree/fork
- 已完成第十六刀：
  - interactive app keybindings 补 resume
- 已完成第十七刀：
  - session selector 单行顶栏宽度感知截断 first cut
- 已完成第十八刀：
  - session selector metadata 响应式列宽 first cut
- 已完成第十九刀：
  - session selector 顶部 info/hint 行显式换行 first cut
- 已完成第二十刀：
  - session selector ANSI 样式层级 first cut
- 已完成第二十一刀：
  - session selector 顶部 status/error/info ANSI first cut
- 已完成第二十二刀：
  - session selector summary 分段着色 first cut
- 已完成第二十三刀：
  - tree/fork selector 复用 ANSI 层级 first cut
- 已完成第二十四刀：
  - session selector regex parse error first cut
- 已完成第二十五刀：
  - session selector empty/no-match/error state hierarchy first cut
- 已完成第二十六刀：
  - session selector empty-state hint parity first cut
- 已完成第二十七刀：
  - selector metadata right-aligned layout first cut

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
- `PiCliKeybindingsLoader` 现在把 keybindings 分成 editor/app 两层：
  - editor 层继续写入 `EditorKeybindings.global()`
  - app 层新增 `PiAppKeybindings.global()`
  - `toggleSessionNamedFilter` 现在归到 app 层，而不是塞进 editor action map
- `PiSessionPicker` 现在按 TS 的职责分层消费 keybindings：
  - `scope/sort/path/delete/rename` 继续走 editor keybindings
  - `named filter` 改走 app keybindings
  - hint 文案里 `named(...)` 也改从 app keybindings 取按键
- `KeyMatcher` 现在支持 generic `alt+<char>`，例如 `alt+n`
- `PiSessionPicker` 现在把 session metadata description 再往 TS 靠了一步：
  - 默认 metadata 不再显示 session file name
  - 顺序改成 `path · cwd · N msg · age` 的子集
  - `path` 只在 `path(on)` 时显示
  - `cwd` 只在 all-scope / same-scope 兼容场景显示
  - home 目录前缀会折叠成 `~`
- `PiSessionPicker` 现在把 title 和 scope summary 合到同一行：
  - current scope 会显示 `Resume session (Current folder) ... ◉ Current Folder | ○ All`
  - all scope / loading scope 也复用同一行结构
  - 这让顶栏结构更接近 TS 的 left-title / right-status
- `PiAppKeybindings` 现在从 session selector 扩到 interactive mode：
  - 新增 `interrupt`（默认 `escape`）
  - 新增 `tree`（默认无快捷键，可配置）
  - 新增 `fork`（默认无快捷键，可配置）
- `PiAppKeybindings` 现在继续补了 `resume`（默认无快捷键，可配置）
- `PiCliKeybindingsLoader` 现在支持从 `keybindings.json` 读取 `interrupt` / `tree` / `fork`
- `PiCliKeybindingsLoader` 现在也支持读取 `resume`
- `PiInteractiveMode` 现在在 prompt 层先消费 app keybindings：
  - app `interrupt` 会调用 `session.abort()`
  - app `resume` 会调用 `session.resume()`
  - app `tree` 会打开 tree overlay
  - app `fork` 会打开 fork overlay
  - 其余输入再继续落到 `Input`
- `PiSessionPicker` 现在会在窄终端下对单行顶栏做宽度感知截断：
  - 宽度足够时仍保持左 title / 右 scope summary 的单行布局
  - 宽度不足时优先保留右侧 scope summary
  - title 不够放下时会用 `...` 安全截断，避免顶栏溢出 viewport
  - scope summary 自身过宽时也会被安全截断，而不是直接撑破一行
- `SelectList` 现在把 label/description 的列宽分配改成响应式：
  - 不再用固定 `32` 列起始位决定 description 是否出现
  - 中等宽度下会优先为 description 保留最小可读空间
  - label 列会按可用宽度动态收缩，description 列在够用时继续显示
  - 这让 session selector 在 `36~40` 列附近仍能保留 `msg/age` 等 metadata
- `PiSessionPicker` 现在把顶部 info/hint 行改成显式按词换行：
  - `sort/named/path` 状态行会按空白边界折行
  - `scope/regex/phrase/delete/rename` 提示行也会按词折行
  - rename / delete 等顶部说明行同样复用这套换行逻辑
  - 这避免窄终端直接按列数硬截断，把 `sort(Threaded)` 之类 token 切碎
- `PiSessionPicker` 现在补了 session selector 的 ANSI 样式层级：
  - selected prefix 用 accent
  - selected row 文本用 bold
  - metadata / scroll info / no-match 文本用 muted
  - 先把层级落在 session list 本身，后续再往顶部 status/info 区扩
- `PiSessionPicker` 现在把顶部区域也接到 ANSI 层级：
  - header title 用 bold
  - scope summary 用 muted
  - sort/named/path 状态行与搜索 hint 行用 muted
  - delete confirm 用 warning
  - load error 用 error
  - rename title 也改成 bold，rename 提示改成 muted
- `PiSessionPicker` 现在把 header summary 再细分成分段着色：
  - active scope 标记（`◉ Current Folder` / `◉ All`）用 accent
  - all-scope loading 的 `Loading x/y` 也用 accent
  - inactive scope 与分隔符保持 muted
  - current 初次 loading 也用 accent，而不是整段 muted
- `PiTreeSelector` / `PiForkSelector` 现在复用同一套 selector theme：
  - overlay title 用 bold
  - overlay hint 用 muted
  - list selected row / metadata / no-match / scroll info 直接复用 session selector 的 ANSI 层级
  - 避免 tree/fork overlay 在视觉上回退成纯文本列表
- `PiSessionPicker` 现在会把 regex parse error 明确显示在 search 区上方：
  - query 解析失败时显示 `Invalid regex: ...`
  - 该状态行走 error 样式
  - 这样不会再和普通 `no match` 空结果混在一起
- `PiSessionPicker` 现在把 empty/no-match/error 三种列表状态拆开了：
  - current/all scope 为空且 query 为空时，显示 `No sessions in ...`
  - named-only 打开且可见结果为空时，显示 `No named sessions in ...`
  - query 非空但无匹配时，显示 `No matches for "..."`
  - regex 非法时，列表区显示 `Invalid regex query`
  - current/all scope 正在 loading 或 load error 时，列表区不再退回 `No matching commands`
- `PiSessionPicker` 现在继续把 empty-state hint 往 TS 靠：
  - current scope 为空时，显示 `No sessions in current folder. Press tab to view all.`
  - current scope 且 named-only 为空时，显示 `Press ctrl+n to show all, or tab to view all.`
  - all scope 且 named-only 为空时，显示 `No named sessions found. Press ctrl+n to show all.`
  - `current/all` 同源时不会误报不可用的 scope-toggle 提示
- `SelectList` 现在把 description 布局继续往 TS selector 靠：
  - 不再给 label 预留固定空列
  - 会从实际 label 宽度回收空余列给右侧 description
  - 短 label + 长 metadata 场景下，会优先保住 `cwd/path/msg/age` 尾部信息
  - 行尾额外保留安全边距，避免 item 正好打满 terminal 宽度时被终端自动换行
- `KeyMatcher` 现在显式支持 `tab`
- `KeyMatcher` 现在显式支持 `ctrl+s`
- `KeyMatcher` 现在显式支持 `ctrl+n`
- `KeyMatcher` 现在显式支持 `ctrl+p`
- `PiSessionPicker.filterAndSortSessions()` 现在抽成包内静态辅助方法，直接用单元测试兜住 `recent/relevance` 排序语义

## 当前边界

- 这还是首版 scope toggle：
  - cycle/default 现在已基本对齐 TS，但 loading/progress header 还只是首版
  - path show/hide 目前还是 description 级别开关，还没有 TS 版右侧布局/列宽截断渲染
  - 顶栏现在虽然合成单行了，但仍然没有 TS 那种宽度感知截断/对齐和颜色层级
  - app 层还没扩到 TS 里的更多 action，例如 model/thinking/follow-up/dequeue/new-session
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
- `PiSessionPickerTest`：named filter 改走 app keybindings
- `KeyMatcherTest`：generic `alt+<char>` sequence
- `PiSessionPickerTest`：默认 metadata 不显示 session file name
- `PiSessionPickerTest`：title 和 scope summary 共用单行顶栏
- `PiCliModuleTest`：从临时 agent dir 加载 `keybindings.json` 覆盖
- `PiInteractiveModeTest`：app keybindings 驱动 tree / fork / interrupt
- `PiInteractiveModeTest`：app keybindings 驱动 resume
- `PiSessionPickerTest`：窄终端下单行顶栏不会超宽，并保留 scope summary
- `SelectListTest`：中等宽度下仍保留 description 列
- `PiSessionPickerTest`：中等宽度下 session metadata 不会过早消失
- `PiSessionPickerTest`：窄终端下 info/hint token 不会被硬换列切碎
- `PiSessionPickerTest`：selected row / metadata 的 ANSI 序列层级
- `PiSessionPickerTest`：header/hint 的 ANSI 序列层级
- `PiSessionPickerTest`：delete confirm / load error 的 ANSI 序列层级
- `PiSessionPickerTest`：all-scope loading summary 的分段 ANSI 序列
- `PiSelectorThemeTest`：tree selector 的共享 ANSI 层级
- `PiSelectorThemeTest`：fork selector 的共享 ANSI 层级
- `PiSessionPickerTest`：regex parse error 的 error 样式
- `PiSessionPickerTest`：current scope empty 状态不再回退到 `No matching commands`
- `PiSessionPickerTest`：search no-match 状态显示 `No matches for "..."`
- `PiSessionPickerTest`：regex error 状态和 no-match 状态分离
- `PiSessionPickerTest`：loading / load error 状态不再渲染通用 no-match 文案
- `PiSessionPickerTest`：current scope empty 状态显示 `tab` 恢复提示
- `PiSessionPickerTest`：named-only 为空时显示 named filter / scope 恢复提示
- `SelectListTest`：宽终端下 description 右侧对齐
- `SelectListTest`：短 label 时不再白白吞掉 metadata 空间
- `SelectListTest`：长 label 截断时仍优先保住右侧 metadata

最近通过：

```bash
.\gradlew.bat :pi-cli:test --no-daemon
```

## 下一步建议

1. selector parity：继续评估 model/settings selector 是否复用同一套层级
2. session selector：继续评估 path/cwd metadata 的颜色层级与 current/named 状态强调
3. keybindings：继续评估 app 层是否要补 model/thinking 相关动作
