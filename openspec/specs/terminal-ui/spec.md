# Terminal UI 组件库规范

> 包名: `@mariozechner/pi-tui`

## 概述

`pi-tui` 是一个极简的终端 UI 框架，提供差分渲染和同步输出，实现无闪烁的交互式 CLI 应用。

## 核心特性

- **差分渲染**: 三策略渲染系统，仅更新变化的部分
- **同步输出**: 使用 CSI 2026 实现原子屏幕更新（无闪烁）
- **括号粘贴模式**: 正确处理大段粘贴，>10 行粘贴显示标记
- **组件化**: 简单的 Component 接口，包含 `render()` 方法
- **主题支持**: 组件接受主题接口进行自定义样式
- **内联图片**: 支持 Kitty 和 iTerm2 图形协议

## 组件接口

### Component
所有组件实现：

```typescript
interface Component {
  render(width: number): string[];    // 返回每行字符串数组
  handleInput?(data: string): void;   // 键盘输入处理
  invalidate?(): void;                // 清除渲染缓存
}
```

**重要**: `render()` 返回的每行字符串 **不得超过 `width`**，否则 TUI 会报错。

### Focusable (IME 支持)
需要 IME 支持的组件应实现：

```typescript
import { CURSOR_MARKER, type Focusable } from '@mariozechner/pi-tui';

class MyInput implements Component, Focusable {
  focused: boolean = false;  // 由 TUI 设置

  render(width: number): string[] {
    const marker = this.focused ? CURSOR_MARKER : "";
    return [`> ${beforeCursor}${marker}\x1b[7m${atCursor}\x1b[27m${afterCursor}`];
  }
}
```

这使 IME 候选窗口在正确位置显示。

## 核心 API

### TUI
主容器，管理组件和渲染：

```typescript
const tui = new TUI(terminal);
tui.addChild(component);
tui.removeChild(component);
tui.start();
tui.stop();
tui.requestRender();  // 请求重渲染

// 全局调试快捷键 (Shift+Ctrl+D)
tui.onDebug = () => console.log('Debug triggered');
```

### Overlays
在现有内容之上渲染组件，用于对话框、菜单、模态 UI：

```typescript
// 默认居中显示
const handle = tui.showOverlay(component);

// 自定义位置和大小
const handle = tui.showOverlay(component, {
  width: 60,              // 固定宽度
  width: '80%',           // 百分比
  minWidth: 40,           // 最小宽度
  maxHeight: 20,          // 最大高度
  anchor: 'bottom-right', // 锚点位置
  offsetX: 2,             // 偏移量
  offsetY: -1,
  margin: 2,              // 边距
  visible: (w, h) => w >= 100,  // 响应式可见性
});

// OverlayHandle 方法
handle.hide();           // 永久移除
handle.setHidden(true);  // 临时隐藏
handle.setHidden(false); // 再次显示
```

**Anchor 值**: `'center'`, `'top-left'`, `'top-right'`, `'bottom-left'`, `'bottom-right'`, `'top-center'`, `'bottom-center'`, `'left-center'`, `'right-center'`

## 内置组件

### Container
组件组：

```typescript
const container = new Container();
container.addChild(component);
container.removeChild(component);
```

### Box
带内边距和背景色的容器：

```typescript
const box = new Box(
  1,  // paddingX
  1,  // paddingY
  (text) => chalk.bgGray(text)  // 可选背景函数
);
box.addChild(new Text('Content'));
box.setBgFn((text) => chalk.bgBlue(text));
```

### Text
多行文本，支持换行和内边距：

```typescript
const text = new Text('Hello World', 1, 1, (t) => chalk.bgGray(t));
text.setText('Updated text');
text.setCustomBgFn((t) => chalk.bgBlue(t));
```

### TruncatedText
单行文本，自动截断适应宽度：

```typescript
const truncated = new TruncatedText('Long text...', 0, 0);
```

### Input
单行输入框，支持水平滚动：

```typescript
const input = new Input();
input.onSubmit = (value) => console.log(value);
input.setValue('initial');
input.getValue();
```

**快捷键**:
- `Enter` - 提交
- `Ctrl+A/E` - 行首/行尾
- `Ctrl+W` - 删除前一词
- `Ctrl+U` - 删除到行首
- `Ctrl+K` - 删除到行尾
- 方向键、Backspace、Delete

### Editor
多行编辑器，支持自动补全、文件补全、粘贴处理、滚动：

```typescript
const editor = new Editor(tui, theme, { paddingX: 0 });
editor.onSubmit = (text) => console.log(text);
editor.onChange = (text) => console.log('Changed:', text);
editor.setAutocompleteProvider(provider);
editor.borderColor = (s) => chalk.blue(s);
```

**特性**:
- 多行编辑，自动换行
- `/` 触发命令自动补全
- `Tab` 文件路径补全
- 大段粘贴标记 `[paste #1 +50 lines]`

**快捷键**:
- `Enter` - 提交
- `Shift/Ctrl/Alt+Enter` - 换行
- `Tab` - 自动补全
- `Ctrl+K/U/W` - 删除操作
- `Ctrl+A/E` - 行首/行尾
- `Ctrl+]` - 跳转到字符

### Markdown
Markdown 渲染，支持语法高亮：

```typescript
const md = new Markdown('# Hello', 1, 1, theme, defaultStyle);
md.setText('Updated markdown');
```

**支持**: 标题、粗体、斜体、代码块、列表、链接、引用

### Loader / CancellableLoader
加载动画：

```typescript
const loader = new Loader(tui, spinnerColor, msgColor, 'Loading...');
loader.start();
loader.setMessage('Still loading...');
loader.stop();

// 可取消版本
const cancellable = new CancellableLoader(tui, color1, color2, 'Working...');
cancellable.onAbort = () => done(null);
doAsyncWork(cancellable.signal).then(done);
```

### SelectList
交互选择列表：

```typescript
const list = new SelectList(items, maxVisible, theme);
list.onSelect = (item) => console.log('Selected:', item);
list.onCancel = () => console.log('Cancelled');
list.setFilter('opt');  // 过滤
```

### SettingsList
设置面板，支持值切换和子菜单：

```typescript
const settings = new SettingsList(
  [
    { id: 'theme', label: 'Theme', currentValue: 'dark', values: ['dark', 'light'] },
    { id: 'model', label: 'Model', currentValue: 'gpt-4', submenu: selector },
  ],
  10, theme,
  (id, newValue) => console.log(`${id} changed`),
  () => console.log('Cancelled')
);
```

### Image
内联图片渲染，支持 Kitty 和 iTerm2 协议：

```typescript
const image = new Image(base64Data, 'image/png', theme, {
  maxWidthCells: 80,
  maxHeightCells: 24,
  filename: 'screenshot.png',
});
```

支持格式: PNG, JPEG, GIF, WebP

## 自动补全

### CombinedAutocompleteProvider
支持命令和文件路径：

```typescript
import { CombinedAutocompleteProvider } from '@mariozechner/pi-tui';

const provider = new CombinedAutocompleteProvider(
  [
    { name: 'help', description: 'Show help' },
    { name: 'clear', description: 'Clear screen' },
  ],
  process.cwd()
);
editor.setAutocompleteProvider(provider);
```

**触发**:
- `/` - 显示命令
- `Tab` - 文件路径补全
- 支持 `~/`, `./`, `../`, `@` 前缀

## 键盘检测

```typescript
import { matchesKey, Key } from '@mariozechner/pi-tui';

if (matchesKey(data, Key.ctrl('c'))) {
  process.exit(0);
}
if (matchesKey(data, Key.enter)) {
  submit();
}
if (matchesKey(data, Key.shift('tab'))) {
  reverseTab();
}
```

**Key 标识符**:
- 基础: `Key.enter`, `Key.escape`, `Key.tab`, `Key.space`, `Key.backspace`, `Key.delete`, `Key.home`, `Key.end`
- 方向: `Key.up`, `Key.down`, `Key.left`, `Key.right`
- 组合: `Key.ctrl('c')`, `Key.shift('tab')`, `Key.alt('left')`, `Key.ctrlShift('p')`

## 差分渲染

三策略渲染系统：

1. **首次渲染**: 输出所有行，不清除滚动缓冲区
2. **宽度变化或变更在视口上方**: 清屏并完整重渲染
3. **正常更新**: 移动光标到第一个变化行，清除到末尾，渲染变化行

所有更新使用 **同步输出** (`\x1b[?2026h` ... `\x1b[?2026l`) 实现原子、无闪烁渲染。

## Terminal 接口

```typescript
interface Terminal {
  start(onInput: (data: string) => void, onResize: () => void): void;
  stop(): void;
  write(data: string): void;
  get columns(): number;
  get rows(): number;
  moveBy(lines: number): void;
  hideCursor(): void;
  showCursor(): void;
  clearLine(): void;
  clearFromCursor(): void;
  clearScreen(): void;
}
```

**内置实现**:
- `ProcessTerminal` - 使用 `process.stdin/stdout`
- `VirtualTerminal` - 测试用 (`@xterm/headless`)

## 工具函数

```typescript
import { visibleWidth, truncateToWidth, wrapTextWithAnsi } from '@mariozechner/pi-tui';

// 获取可见宽度（忽略 ANSI 码）
const width = visibleWidth('\x1b[31mHello\x1b[0m'); // 5

// 截断到宽度（保留 ANSI 码）
const truncated = truncateToWidth('Hello World', 8); // "Hello..."

// 换行（保留 ANSI 码跨行）
const lines = wrapTextWithAnsi('Long text...', 20);
```

## 创建自定义组件

**关键约束**: 每行不得超过 `width` 参数。

```typescript
import { matchesKey, Key, truncateToWidth } from '@mariozechner/pi-tui';

class MyComponent implements Component {
  private items = ['Option 1', 'Option 2'];
  private selected = 0;

  handleInput(data: string): void {
    if (matchesKey(data, Key.up)) {
      this.selected = Math.max(0, this.selected - 1);
    } else if (matchesKey(data, Key.down)) {
      this.selected = Math.min(this.items.length - 1, this.selected + 1);
    }
  }

  render(width: number): string[] {
    return this.items.map((item, i) => {
      const prefix = i === this.selected ? '> ' : '  ';
      return truncateToWidth(prefix + item, width);
    });
  }
}
```

### 缓存渲染结果

```typescript
class CachedComponent implements Component {
  private cachedWidth?: number;
  private cachedLines?: string[];

  render(width: number): string[] {
    if (this.cachedLines && this.cachedWidth === width) {
      return this.cachedLines;
    }
    const lines = [/* ... */];
    this.cachedWidth = width;
    this.cachedLines = lines;
    return lines;
  }

  invalidate(): void {
    this.cachedWidth = undefined;
    this.cachedLines = undefined;
  }
}
```

## 调试

设置环境变量捕获 ANSI 流：

```bash
PI_TUI_WRITE_LOG=/tmp/tui-ansi.log npx tsx app.ts
```