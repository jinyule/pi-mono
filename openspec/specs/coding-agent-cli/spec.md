# Coding Agent CLI 规范

> 包名: `@mariozechner/pi-coding-agent`

## 概述

Pi 是一个极简的终端编码工具 (terminal coding harness)，一个在终端中运行的 AI 编码助手。Pi 的设计理念是"可扩展而非预设"，用户可以通过 TypeScript Extensions、Skills、Prompt Templates 和 Themes 来定制工作流。

## 运行模式

| 模式 | 说明 |
|------|------|
| **Interactive** | 默认模式，全功能 TUI 交互界面 |
| **Print** (`-p`) | 打印响应后退出 |
| **JSON** (`--mode json`) | 输出所有事件为 JSON Lines |
| **RPC** (`--mode rpc`) | 进程集成的 RPC 模式 |
| **SDK** | 嵌入到其他应用 |

## 核心功能

### 交互式界面

```
┌─────────────────────────────────────────────────────┐
│ 启动头部 - 快捷键、AGENTS.md、模板、技能、扩展        │
├─────────────────────────────────────────────────────┤
│                                                     │
│ 消息区 - 用户消息、助手响应、工具调用、通知、错误     │
│                                                     │
├─────────────────────────────────────────────────────┤
│ 编辑器 - 输入区，边框颜色表示 thinking level         │
├─────────────────────────────────────────────────────┤
│ 底部栏 - 工作目录、会话名、token/缓存使用、成本、模型 │
└─────────────────────────────────────────────────────┘
```

### 编辑器功能

| 功能 | 操作 |
|------|------|
| 文件引用 | 输入 `@` 模糊搜索项目文件 |
| 路径补全 | `Tab` 补全路径 |
| 多行输入 | `Shift+Enter` (Windows Terminal: `Ctrl+Enter`) |
| 图片粘贴 | `Ctrl+V` (Windows: `Alt+V`)，或拖放到终端 |
| Bash 命令 | `!command` 运行并发送输出，`!!command` 不发送 |

### 命令系统

输入 `/` 触发命令：

| 命令 | 说明 |
|------|------|
| `/login`, `/logout` | OAuth 认证 |
| `/model` | 切换模型 |
| `/scoped-models` | 启用/禁用 Ctrl+P 循环的模型 |
| `/settings` | Thinking level、主题、消息投递、传输方式 |
| `/resume` | 选择历史会话 |
| `/new` | 新建会话 |
| `/name <name>` | 设置会话显示名 |
| `/session` | 显示会话信息 |
| `/tree` | 跳转到会话任意点继续 |
| `/fork` | 从当前分支创建新会话 |
| `/compact [prompt]` | 手动压缩上下文 |
| `/copy` | 复制最后一条助手消息 |
| `/export [file]` | 导出会话到 HTML |
| `/share` | 上传为私有 GitHub gist |
| `/reload` | 重载扩展、技能、模板、上下文文件 |
| `/hotkeys` | 显示所有快捷键 |
| `/changelog` | 显示版本历史 |
| `/quit`, `/exit` | 退出 |

### 快捷键

| 快捷键 | 动作 |
|--------|------|
| `Ctrl+C` | 清空编辑器 |
| `Ctrl+C` 两次 | 退出 |
| `Escape` | 取消/中止 |
| `Escape` 两次 | 打开 `/tree` |
| `Ctrl+L` | 打开模型选择器 |
| `Ctrl+P` / `Shift+Ctrl+P` | 循环 scoped models |
| `Shift+Tab` | 循环 thinking level |
| `Ctrl+O` | 折叠/展开工具输出 |
| `Ctrl+T` | 折叠/展开思考块 |

### 消息队列

在 Agent 工作时提交消息：

- **Enter** - 排队 *steering* 消息，在当前工具执行后投递（中断剩余工具）
- **Alt+Enter** - 排队 *follow-up* 消息，在 Agent 完成所有工作后投递
- **Escape** - 中止并恢复排队消息到编辑器
- **Alt+Up** - 取回排队消息到编辑器

## 会话管理

### 存储格式

会话存储为 JSONL 文件，包含树结构。每个条目有 `id` 和 `parentId`，支持原地分支而不创建新文件。

```bash
~/.pi/agent/sessions/
├── --Users-alice-work-my-project--/
│   ├── session-1.jsonl
│   └── session-2.jsonl
```

### 会话命令

```bash
pi -c                  # 继续最近会话
pi -r                  # 浏览选择历史会话
pi --no-session        # 临时模式（不保存）
pi --session <path>    # 使用指定会话文件或 ID
```

### 分支

**`/tree`** - 原地导航会话树。选择任意历史点，从那里继续，在分支间切换。所有历史保存在单个文件中。

**`/fork`** - 从当前分支创建新会话文件。打开选择器，复制到选择点的历史，将消息放入编辑器供修改。

### 压缩

长会话可能耗尽上下文窗口。压缩总结旧消息，保留最近的。

- **手动**: `/compact` 或 `/compact <自定义指令>`
- **自动**: 默认启用，在上下文溢出或接近限制时触发

压缩是有损的。完整历史保留在 JSONL 文件中，可通过 `/tree` 重访。

## 设置

### 位置

| 位置 | 范围 |
|------|------|
| `~/.pi/agent/settings.json` | 全局（所有项目） |
| `.pi/settings.json` | 项目（覆盖全局） |

### 关键配置

```json
{
  "thinkingLevel": "medium",
  "steeringMode": "one-at-a-time",
  "followUpMode": "one-at-a-time",
  "transport": "auto",
  "theme": "dark"
}
```

## 上下文文件

Pi 启动时加载 `AGENTS.md`（或 `CLAUDE.md`）：
- `~/.pi/agent/AGENTS.md` (全局)
- 父目录（从 cwd 向上遍历）
- 当前目录

所有匹配文件被连接。用于项目指令、约定、常用命令。

### 系统提示词

通过以下文件替换或追加默认系统提示词：
- `.pi/SYSTEM.md` - 项目级替换
- `~/.pi/agent/SYSTEM.md` - 全局替换
- `.pi/APPEND_SYSTEM.md` - 项目级追加
- `~/.pi/agent/APPEND_SYSTEM.md` - 全局追加

## 扩展系统

### Prompt Templates

可复用的提示词模板，Markdown 文件，输入 `/name` 展开。

```markdown
<!-- ~/.pi/agent/prompts/review.md -->
Review this code for bugs, security issues, and performance problems.
Focus on: {{focus}}
```

### Skills

按需加载的能力包，遵循 [Agent Skills 标准](https://agentskills.io)。通过 `/skill:name` 调用或让 Agent 自动加载。

```markdown
<!-- ~/.pi/agent/skills/my-skill/SKILL.md -->
# My Skill
Use this skill when the user asks about X.

## Steps
1. Do this
2. Then that
```

### Extensions

TypeScript 模块，扩展 Pi 的自定义工具、命令、快捷键、事件处理器和 UI 组件。

```typescript
export default function (pi: ExtensionAPI) {
  pi.registerTool({ name: 'deploy', ... });
  pi.registerCommand('stats', { ... });
  pi.on('tool_call', async (event, ctx) => { ... });
}
```

**可实现**:
- 自定义工具（或完全替换内置工具）
- Sub-agents 和 plan mode
- 自定义压缩和摘要
- 权限门控和路径保护
- 自定义编辑器和 UI 组件
- Git 检查点和自动提交
- SSH 和沙箱执行
- MCP 服务器集成
- ...

### Themes

内置: `dark`, `light`。主题热重载：修改活动主题文件，Pi 立即应用更改。

### Pi Packages

通过 npm 或 git 打包和分享扩展、技能、模板、主题。

```bash
pi install npm:@foo/pi-tools
pi install git:github.com/user/repo
pi remove npm:@foo/pi-tools
pi list
pi update
pi config
```

## SDK

```typescript
import { AuthStorage, createAgentSession, ModelRegistry, SessionManager } from '@mariozechner/pi-coding-agent';

const authStorage = AuthStorage.create();
const modelRegistry = new ModelRegistry(authStorage);

const { session } = await createAgentSession({
  sessionManager: SessionManager.inMemory(),
  authStorage,
  modelRegistry,
});

await session.prompt('What files are in the current directory?');
```

## CLI 参考

### 模式选项

| 选项 | 说明 |
|------|------|
| (默认) | 交互模式 |
| `-p`, `--print` | 打印响应后退出 |
| `--mode json` | JSON Lines 输出 |
| `--mode rpc` | RPC 模式 |
| `--export <in> [out]` | 导出会话到 HTML |

### 模型选项

| 选项 | 说明 |
|------|------|
| `--provider <name>` | 提供商 |
| `--model <pattern>` | 模型模式或 ID |
| `--api-key <key>` | API key |
| `--thinking <level>` | 思考级别 |
| `--models <patterns>` | Ctrl+P 循环的模型 |
| `--list-models [search]` | 列出可用模型 |

### 工具选项

| 选项 | 说明 |
|------|------|
| `--tools <list>` | 启用特定内置工具 |
| `--no-tools` | 禁用所有内置工具 |

内置工具: `read`, `bash`, `edit`, `write`, `grep`, `find`, `ls`

### 资源选项

| 选项 | 说明 |
|------|------|
| `-e`, `--extension <source>` | 加载扩展 |
| `--no-extensions` | 禁用扩展发现 |
| `--skill <path>` | 加载技能 |
| `--no-skills` | 禁用技能发现 |
| `--prompt-template <path>` | 加载模板 |
| `--no-prompt-templates` | 禁用模板发现 |
| `--theme <path>` | 加载主题 |
| `--no-themes` | 禁用主题发现 |

### 文件参数

前缀 `@` 包含文件：

```bash
pi @prompt.md "Answer this"
pi @screenshot.png "What's in this image?"
pi @code.ts @test.ts "Review these files"
```

## 设计哲学

- **无 MCP** - 构建 CLI 工具配合 README，或构建扩展添加 MCP 支持
- **无 sub-agents** - 通过 tmux 启动 pi 实例，或构建自己的扩展
- **无权限弹窗** - 在容器中运行，或构建自己的确认流程扩展
- **无 plan mode** - 将计划写入文件，或构建扩展
- **无内置 TODO** - 使用 TODO.md 文件，或构建扩展
- **无后台 bash** - 使用 tmux，完全可观测性，直接交互
