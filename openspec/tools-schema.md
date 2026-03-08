# Pi Coding Agent 工具定义

> 基于 `@mariozechner/pi-coding-agent` 包的工具定义，转换为 OpenAI Function Call Schema 格式。

## 概述

Pi Coding Agent 提供以下内置工具：

| 工具 | 用途 | 默认启用 |
|------|------|----------|
| `read` | 读取文件内容 | ✅ |
| `bash` | 执行 shell 命令 | ✅ |
| `edit` | 精确编辑文件 | ✅ |
| `write` | 写入文件 | ✅ |
| `grep` | 搜索文件内容 | 可选 |
| `find` | 查找文件 | 可选 |
| `ls` | 列出目录 | 可选 |

**默认工具集**: `read`, `bash`, `edit`, `write`

**只读工具集**: `read`, `grep`, `find`, `ls`

---

## 工具 Schema 定义

### 1. read

读取文件内容，支持文本文件和图片。

```json
{
    "type": "function",
    "name": "read",
    "description": "Read the contents of a file. Supports text files and images (jpg, png, gif, webp). Images are sent as attachments. For text files, output is truncated to 2000 lines or 50KB (whichever is hit first). Use offset/limit for large files. When you need the full file, continue with offset until complete.",
    "parameters": {
        "type": "object",
        "properties": {
            "path": {
                "type": "string",
                "description": "Path to the file to read (relative or absolute)"
            },
            "offset": {
                "type": "number",
                "description": "Line number to start reading from (1-indexed)"
            },
            "limit": {
                "type": "number",
                "description": "Maximum number of lines to read"
            }
        },
        "required": ["path"]
    }
}
```

**输出限制**:
- 最大 2000 行
- 最大 50KB

**特性**:
- 自动检测并处理图片文件 (jpg, png, gif, webp)
- 图片自动调整大小到 2000x2000 像素以内
- 支持分页读取大文件

---

### 2. bash

在当前工作目录执行 bash 命令。

```json
{
    "type": "function",
    "name": "bash",
    "description": "Execute a bash command in the current working directory. Returns stdout and stderr. Output is truncated to last 2000 lines or 50KB (whichever is hit first). If truncated, full output is saved to a temp file. Optionally provide a timeout in seconds.",
    "parameters": {
        "type": "object",
        "properties": {
            "command": {
                "type": "string",
                "description": "Bash command to execute"
            },
            "timeout": {
                "type": "number",
                "description": "Timeout in seconds (optional, no default timeout)"
            }
        },
        "required": ["command"]
    }
}
```

**输出限制**:
- 最大 2000 行（保留末尾）
- 最大 50KB
- 超出时完整输出保存到临时文件

**特性**:
- 支持 AbortSignal 取消
- 支持超时设置
- 流式输出支持
- 非 0 退出码会抛出错误

---

### 3. edit

通过精确文本替换编辑文件。

```json
{
    "type": "function",
    "name": "edit",
    "description": "Edit a file by replacing exact text. The oldText must match exactly (including whitespace). Use this for precise, surgical edits.",
    "parameters": {
        "type": "object",
        "properties": {
            "path": {
                "type": "string",
                "description": "Path to the file to edit (relative or absolute)"
            },
            "oldText": {
                "type": "string",
                "description": "Exact text to find and replace (must match exactly)"
            },
            "newText": {
                "type": "string",
                "description": "New text to replace the old text with"
            }
        },
        "required": ["path", "oldText", "newText"]
    }
}
```

**特性**:
- 精确匹配替换（包括空白字符）
- 支持模糊匹配（尝试精确匹配后使用模糊匹配）
- 文本必须唯一，多处匹配会报错
- 自动处理 BOM 和行结束符
- 返回 diff 信息

---

### 4. write

写入文件内容。

```json
{
    "type": "function",
    "name": "write",
    "description": "Write content to a file. Creates the file if it doesn't exist, overwrites if it does. Automatically creates parent directories.",
    "parameters": {
        "type": "object",
        "properties": {
            "path": {
                "type": "string",
                "description": "Path to the file to write (relative or absolute)"
            },
            "content": {
                "type": "string",
                "description": "Content to write to the file"
            }
        },
        "required": ["path", "content"]
    }
}
```

**特性**:
- 文件不存在时自动创建
- 文件存在时覆盖
- 自动创建父目录
- 支持 AbortSignal 取消

---

### 5. grep

搜索文件内容。

```json
{
    "type": "function",
    "name": "grep",
    "description": "Search file contents for a pattern. Returns matching lines with file paths and line numbers. Respects .gitignore. Output is truncated to 100 matches or 50KB (whichever is hit first). Long lines are truncated to 500 chars.",
    "parameters": {
        "type": "object",
        "properties": {
            "pattern": {
                "type": "string",
                "description": "Search pattern (regex or literal string)"
            },
            "path": {
                "type": "string",
                "description": "Directory or file to search (default: current directory)"
            },
            "glob": {
                "type": "string",
                "description": "Filter files by glob pattern, e.g. '*.ts' or '**/*.spec.ts'"
            },
            "ignoreCase": {
                "type": "boolean",
                "description": "Case-insensitive search (default: false)"
            },
            "literal": {
                "type": "boolean",
                "description": "Treat pattern as literal string instead of regex (default: false)"
            },
            "context": {
                "type": "number",
                "description": "Number of lines to show before and after each match (default: 0)"
            },
            "limit": {
                "type": "number",
                "description": "Maximum number of matches to return (default: 100)"
            }
        },
        "required": ["pattern"]
    }
}
```

**输出限制**:
- 最大 100 个匹配（默认）
- 最大 50KB
- 单行最大 500 字符

**特性**:
- 使用 ripgrep (rg) 进行搜索
- 自动忽略 .gitignore 中的文件
- 支持正则表达式和字面匹配
- 支持 glob 文件过滤
- 支持上下文行显示

---

### 6. find

查找文件。

```json
{
    "type": "function",
    "name": "find",
    "description": "Search for files by glob pattern. Returns matching file paths relative to the search directory. Respects .gitignore. Output is truncated to 1000 results or 50KB (whichever is hit first).",
    "parameters": {
        "type": "object",
        "properties": {
            "pattern": {
                "type": "string",
                "description": "Glob pattern to match files, e.g. '*.ts', '**/*.json', or 'src/**/*.spec.ts'"
            },
            "path": {
                "type": "string",
                "description": "Directory to search in (default: current directory)"
            },
            "limit": {
                "type": "number",
                "description": "Maximum number of results (default: 1000)"
            }
        },
        "required": ["pattern"]
    }
}
```

**输出限制**:
- 最大 1000 个结果（默认）
- 最大 50KB

**特性**:
- 使用 fd 进行快速搜索
- 支持 glob 模式匹配
- 自动忽略 .gitignore 中的文件
- 包含隐藏文件

---

### 7. ls

列出目录内容。

```json
{
    "type": "function",
    "name": "ls",
    "description": "List directory contents. Returns entries sorted alphabetically, with '/' suffix for directories. Includes dotfiles. Output is truncated to 500 entries or 50KB (whichever is hit first).",
    "parameters": {
        "type": "object",
        "properties": {
            "path": {
                "type": "string",
                "description": "Directory to list (default: current directory)"
            },
            "limit": {
                "type": "number",
                "description": "Maximum number of entries to return (default: 500)"
            }
        },
        "required": []
    }
}
```

**输出限制**:
- 最大 500 个条目（默认）
- 最大 50KB

**特性**:
- 按字母顺序排序（不区分大小写）
- 目录以 `/` 后缀标识
- 包含隐藏文件（dotfiles）

---

## 完整 Schema 数组

```json
[
    {
        "type": "function",
        "name": "read",
        "description": "Read the contents of a file. Supports text files and images (jpg, png, gif, webp). Images are sent as attachments. For text files, output is truncated to 2000 lines or 50KB (whichever is hit first). Use offset/limit for large files. When you need the full file, continue with offset until complete.",
        "parameters": {
            "type": "object",
            "properties": {
                "path": {
                    "type": "string",
                    "description": "Path to the file to read (relative or absolute)"
                },
                "offset": {
                    "type": "number",
                    "description": "Line number to start reading from (1-indexed)"
                },
                "limit": {
                    "type": "number",
                    "description": "Maximum number of lines to read"
                }
            },
            "required": ["path"]
        }
    },
    {
        "type": "function",
        "name": "bash",
        "description": "Execute a bash command in the current working directory. Returns stdout and stderr. Output is truncated to last 2000 lines or 50KB (whichever is hit first). If truncated, full output is saved to a temp file. Optionally provide a timeout in seconds.",
        "parameters": {
            "type": "object",
            "properties": {
                "command": {
                    "type": "string",
                    "description": "Bash command to execute"
                },
                "timeout": {
                    "type": "number",
                    "description": "Timeout in seconds (optional, no default timeout)"
                }
            },
            "required": ["command"]
        }
    },
    {
        "type": "function",
        "name": "edit",
        "description": "Edit a file by replacing exact text. The oldText must match exactly (including whitespace). Use this for precise, surgical edits.",
        "parameters": {
            "type": "object",
            "properties": {
                "path": {
                    "type": "string",
                    "description": "Path to the file to edit (relative or absolute)"
                },
                "oldText": {
                    "type": "string",
                    "description": "Exact text to find and replace (must match exactly)"
                },
                "newText": {
                    "type": "string",
                    "description": "New text to replace the old text with"
                }
            },
            "required": ["path", "oldText", "newText"]
        }
    },
    {
        "type": "function",
        "name": "write",
        "description": "Write content to a file. Creates the file if it doesn't exist, overwrites if it does. Automatically creates parent directories.",
        "parameters": {
            "type": "object",
            "properties": {
                "path": {
                    "type": "string",
                    "description": "Path to the file to write (relative or absolute)"
                },
                "content": {
                    "type": "string",
                    "description": "Content to write to the file"
                }
            },
            "required": ["path", "content"]
        }
    },
    {
        "type": "function",
        "name": "grep",
        "description": "Search file contents for a pattern. Returns matching lines with file paths and line numbers. Respects .gitignore. Output is truncated to 100 matches or 50KB (whichever is hit first). Long lines are truncated to 500 chars.",
        "parameters": {
            "type": "object",
            "properties": {
                "pattern": {
                    "type": "string",
                    "description": "Search pattern (regex or literal string)"
                },
                "path": {
                    "type": "string",
                    "description": "Directory or file to search (default: current directory)"
                },
                "glob": {
                    "type": "string",
                    "description": "Filter files by glob pattern, e.g. '*.ts' or '**/*.spec.ts'"
                },
                "ignoreCase": {
                    "type": "boolean",
                    "description": "Case-insensitive search (default: false)"
                },
                "literal": {
                    "type": "boolean",
                    "description": "Treat pattern as literal string instead of regex (default: false)"
                },
                "context": {
                    "type": "number",
                    "description": "Number of lines to show before and after each match (default: 0)"
                },
                "limit": {
                    "type": "number",
                    "description": "Maximum number of matches to return (default: 100)"
                }
            },
            "required": ["pattern"]
        }
    },
    {
        "type": "function",
        "name": "find",
        "description": "Search for files by glob pattern. Returns matching file paths relative to the search directory. Respects .gitignore. Output is truncated to 1000 results or 50KB (whichever is hit first).",
        "parameters": {
            "type": "object",
            "properties": {
                "pattern": {
                    "type": "string",
                    "description": "Glob pattern to match files, e.g. '*.ts', '**/*.json', or 'src/**/*.spec.ts'"
                },
                "path": {
                    "type": "string",
                    "description": "Directory to search in (default: current directory)"
                },
                "limit": {
                    "type": "number",
                    "description": "Maximum number of results (default: 1000)"
                }
            },
            "required": ["pattern"]
        }
    },
    {
        "type": "function",
        "name": "ls",
        "description": "List directory contents. Returns entries sorted alphabetically, with '/' suffix for directories. Includes dotfiles. Output is truncated to 500 entries or 50KB (whichever is hit first).",
        "parameters": {
            "type": "object",
            "properties": {
                "path": {
                    "type": "string",
                    "description": "Directory to list (default: current directory)"
                },
                "limit": {
                    "type": "number",
                    "description": "Maximum number of entries to return (default: 500)"
                }
            },
            "required": []
        }
    }
]
```

---

## 输出限制汇总

| 工具 | 行数限制 | 大小限制 | 其他限制 |
|------|----------|----------|----------|
| read | 2000 行 | 50KB | - |
| bash | 2000 行 | 50KB | - |
| edit | - | - | - |
| write | - | - | - |
| grep | - | 50KB | 100 匹配, 单行 500 字符 |
| find | - | 50KB | 1000 结果 |
| ls | - | 50KB | 500 条目 |

---

## 使用示例

### Python (OpenAI SDK)

```python
from openai import OpenAI

client = OpenAI()

# 使用默认工具集
response = client.chat.completions.create(
    model="gpt-4o",
    messages=[{"role": "user", "content": "Read the package.json file"}],
    tools=tools_schema,  # 上面的完整 schema 数组
    tool_choice="auto"
)

# 处理工具调用
if response.choices[0].message.tool_calls:
    for tool_call in response.choices[0].message.tool_calls:
        if tool_call.function.name == "read":
            # 执行 read 工具
            args = json.loads(tool_call.function.arguments)
            result = read_file(args["path"], args.get("offset"), args.get("limit"))
```

### TypeScript

```typescript
import { createCodingTools } from '@mariozechner/pi-coding-agent';

// 创建工具实例
const tools = createCodingTools(process.cwd());

// 工具已配置好，可直接用于 Agent
agent.setTools(tools);
```