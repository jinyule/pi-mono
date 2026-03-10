# 阶段 6：pi-tui 交接

更新时间：2026-03-10

## 当前状态

阶段 6 当前已完成：

1. core contracts：`Terminal`、`Component`、`Focusable`、`Overlay`
2. terminal base support：`ProcessTerminal`、`TerminalInputBuffer`
3. differential rendering：`DiffRenderer`、`SynchronizedOutput`
4. overlay / IME / hardware cursor：`Tui`、`OverlayHandle`、`CursorPosition`
5. 基础文本组件第一批：`Container`、`Text`、`TruncatedText`
6. 输入组件第一批：`Input`、`EditorKeybindings`、`KeyMatcher`、`KillRing`、`UndoStack`
7. 多行编辑组件第一批：`Editor`
8. `Markdown` 第一批
9. `Loader` 第一批
10. `SelectList` 第一批

## 当前关键入口

- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/ProcessTerminal.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/TerminalInputBuffer.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/DiffRenderer.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/SynchronizedOutput.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/Tui.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/TerminalText.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/Container.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/Text.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/TruncatedText.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/DefaultTextStyle.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/Input.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/InputSupport.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/EditorAction.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/EditorKeybindings.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/Editor.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/MarkdownTheme.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/Markdown.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/Loader.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/SelectItem.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/SelectListTheme.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/SelectList.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/KeyMatcher.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/KillRing.java`
- `pi-java/modules/pi-tui/src/main/java/dev/pi/tui/UndoStack.java`

## 已完成切片

### 1. Core contracts

- `Terminal`、`Component`、`Focusable`、`Overlay`
- `OverlayOptions`、`OverlayMargin`、`OverlayAnchor`
- `InputHandler`

### 2. Terminal base support

- `ProcessTerminal` 已接 JLine system terminal
- raw mode / resize / title / cursor / clear
- bracketed paste
- kitty keyboard protocol query/enable
- `TerminalInputBuffer` 已支持 escape sequence buffering 和 bracketed paste 聚合

### 3. Diff renderer

- `SynchronizedOutput` 封装 `CSI ? 2026 h/l`
- `DiffRenderer` 已支持：
  - 首帧全量 render
  - 宽度变化 full redraw
  - shrink full redraw
  - append-only tail render
  - viewport 外变更 fallback

### 4. Overlay / cursor

- `Tui.CURSOR_MARKER` 对齐 TS APC marker
- `Tui` 已支持：
  - child list
  - focus 切换
  - `showOverlay()` / `hideOverlay()` / `hasOverlay()`
  - overlay hidden/show/hide 生命周期
  - cursor marker 提取
  - hardware cursor reposition
- `TerminalText` 已提供当前需要的 ANSI 感知宽度/切片能力

### 5. 基础文本组件第一批

- `Container`
- `Text`
- `TruncatedText`

本次新增/扩展的文本能力：

- `TerminalText.wrapText()`
- `TerminalText.truncateToWidth()`
- `TerminalText.applyBackgroundToLine()`

### 6. 输入组件第一批

- `Input` 已支持：
  - 单行编辑
  - horizontal scrolling
  - fake cursor render
  - `CURSOR_MARKER` 注入
  - submit / escape callback
  - bracketed paste
  - 左右 / 行首 / 行尾移动
  - 按词移动
  - backspace / delete
  - delete word backward / forward
  - delete to line start / end
  - yank / yank-pop
  - undo
- 新增最小可配置键位层：
  - `EditorAction`
  - `EditorKeybindings`
  - `KeyMatcher`
- 新增最小编辑支撑：
  - `KillRing`
  - `UndoStack`

### 7. 多行编辑组件第一批

- `Editor` 已支持：
  - 多行文本模型
  - `alt+enter` 换行、`enter` submit 分离
  - 左右 / 上下 / 行首 / 行尾移动
  - 跨行 backspace / delete merge
  - 按词移动、按词删除、kill / yank / yank-pop、undo
  - bracketed paste
  - focused cursor marker
  - 顶/底 border render 与 `paddingX`
- `InputSupport` 已抽出共享字符分类，供 `Input` / `Editor` 复用
- `EditorAction` / `EditorKeybindings` / `KeyMatcher` 已补 `CURSOR_UP`、`CURSOR_DOWN`、`NEW_LINE` 及默认键位

### 8. `Markdown` 第一批

- 已接 `commonmark-java`
- 已接 `GFM tables`、`autolink`、`strikethrough` 扩展
- `Markdown` 已支持：
  - heading / paragraph / hr
  - nested list
  - blockquote
  - fenced / indented code block
  - inline code / bold / italic / strikethrough / link
  - width-aware table render
  - `paddingX` / `paddingY`
  - default text style 与整行 background
  - render cache
- `TerminalText.dropVisibleColumns()` 已补 ANSI style carry，用于长 token/table cell 的换行保样式
- 当前 nested list contract 先固定到 `commonmark-java` 可稳定解析的四空格嵌套

### 9. `Loader` 第一批

- `Loader` 已支持：
  - braille spinner frames
  - `Tui.requestRender()` 驱动逐帧刷新
  - `start()` / `stop()` / `close()`
  - message 动态更新
  - 顶部 blank spacer line
- 当前实现采用 `Text` 组合而不是继承，避免放宽 `Text` 的 `final` 边界
- 测试通过 package-private `advanceFrame()` 做可控 tick，避免时间抖动

### 10. `SelectList` 第一批

- `SelectList` 已支持：
  - `items` / `filteredItems` / `selectedIndex`
  - prefix filter（`value` startsWith）
  - wrap-around up/down navigation
  - select / cancel / selection-change callbacks
  - multiline description normalize 为单行
  - selected / unselected 行渲染
  - scroll indicator
- 当前实现直接复用 `EditorKeybindings.global()` 的 `CURSOR_UP` / `CURSOR_DOWN` / `SUBMIT` / `SELECT_CANCEL`

## 当前测试

已覆盖的测试入口：

- `pi-java/modules/pi-tui/src/test/java/dev/pi/tui/PiTuiContractsTest.java`
- `pi-java/modules/pi-tui/src/test/java/dev/pi/tui/ProcessTerminalTest.java`
- `pi-java/modules/pi-tui/src/test/java/dev/pi/tui/TerminalInputBufferTest.java`
- `pi-java/modules/pi-tui/src/test/java/dev/pi/tui/DiffRendererTest.java`
- `pi-java/modules/pi-tui/src/test/java/dev/pi/tui/TuiTest.java`
- `pi-java/modules/pi-tui/src/test/java/dev/pi/tui/BasicComponentsTest.java`
- `pi-java/modules/pi-tui/src/test/java/dev/pi/tui/InputTest.java`
- `pi-java/modules/pi-tui/src/test/java/dev/pi/tui/EditorTest.java`
- `pi-java/modules/pi-tui/src/test/java/dev/pi/tui/MarkdownTest.java`
- `pi-java/modules/pi-tui/src/test/java/dev/pi/tui/LoaderTest.java`
- `pi-java/modules/pi-tui/src/test/java/dev/pi/tui/SelectListTest.java`

最近验证通过：

```bash
.\gradlew.bat :pi-tui:test --no-daemon
npm.cmd run check
```

## 当前边界

还没有完成：

- `SettingsList`
- `Image`
- `VirtualTerminal`
- render / key golden tests

## 下一步建议

建议继续按这个顺序推进：

1. `SettingsList`
2. `Image`
3. `VirtualTerminal` 与 golden tests
