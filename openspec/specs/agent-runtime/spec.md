# Agent 运行时规范

> 包名: `@mariozechner/pi-agent-core`

## 概述

`pi-agent-core` 提供有状态的 Agent 运行时，包含工具执行和事件流。构建于 `@mariozechner/pi-ai` 之上。

## 核心概念

### AgentMessage vs LLM Message

Agent 使用 `AgentMessage` 类型，可以包含：
- 标准 LLM 消息 (`user`, `assistant`, `toolResult`)
- 自定义应用特定消息类型（通过声明合并扩展）

LLM 仅理解 `user`, `assistant`, `toolResult`。`convertToLlm` 函数在每次 LLM 调用前进行过滤和转换。

### 消息流

```
AgentMessage[] → transformContext() → AgentMessage[] → convertToLlm() → Message[] → LLM
                    (可选)                           (必需)
```

1. **transformContext**: 裁剪旧消息，注入外部上下文
2. **convertToLlm**: 过滤 UI-only 消息，转换自定义类型为 LLM 格式

## 状态管理

### AgentState
```typescript
interface AgentState {
  systemPrompt: string;              // 系统提示词
  model: Model<any>;                 // 当前模型
  thinkingLevel: ThinkingLevel;      // 思考级别
  tools: AgentTool<any>[];           // 可用工具
  messages: AgentMessage[];          // 消息历史
  isStreaming: boolean;              // 是否正在流式输出
  streamMessage: AgentMessage | null; // 当前流式消息
  pendingToolCalls: Set<string>;     // 待执行工具调用
  error?: string;                    // 错误信息
}

type ThinkingLevel = 'off' | 'minimal' | 'low' | 'medium' | 'high' | 'xhigh';
```

## 事件系统

### 事件类型

| 事件 | 描述 |
|------|------|
| `agent_start` | Agent 开始处理 |
| `agent_end` | Agent 完成，返回所有新消息 |
| `turn_start` | 新 turn 开始 (一次 LLM 调用 + 工具执行) |
| `turn_end` | Turn 完成，返回 assistant 消息和工具结果 |
| `message_start` | 任何消息开始 (user, assistant, toolResult) |
| `message_update` | **仅 Assistant** 包含增量内容 |
| `message_end` | 消息完成 |
| `tool_execution_start` | 工具开始执行 |
| `tool_execution_update` | 工具流式进度 |
| `tool_execution_end` | 工具执行完成 |

### 事件流示例

```
prompt("Hello")
├─ agent_start
├─ turn_start
├─ message_start   { message: userMessage }
├─ message_end     { message: userMessage }
├─ message_start   { message: assistantMessage }
├─ message_update  { message: partial... }
├─ message_end     { message: assistantMessage }
├─ turn_end        { message, toolResults: [] }
└─ agent_end       { messages: [...] }
```

### 带工具调用的事件流

```
prompt("Read config.json")
├─ agent_start
├─ turn_start
├─ message_start/end  { userMessage }
├─ message_start      { assistantMessage with toolCall }
├─ message_update...
├─ message_end        { assistantMessage }
├─ tool_execution_start  { toolCallId, toolName, args }
├─ tool_execution_update { partialResult }
├─ tool_execution_end    { toolCallId, result }
├─ message_start/end  { toolResultMessage }
├─ turn_end           { message, toolResults: [toolResult] }
│
├─ turn_start                                        // 下一个 turn
├─ message_start      { assistantMessage }           // LLM 响应工具结果
├─ message_update...
├─ message_end
├─ turn_end
└─ agent_end
```

## API 参考

### Agent 构造
```typescript
const agent = new Agent({
  initialState: {
    systemPrompt: 'You are a helpful assistant.',
    model: getModel('anthropic', 'claude-sonnet-4-20250514'),
    thinkingLevel: 'medium',
    tools: [],
    messages: [],
  },
  convertToLlm: (messages) => messages.filter(m =>
    ['user', 'assistant', 'toolResult'].includes(m.role)
  ),
  transformContext: async (messages, signal) => pruneOldMessages(messages),
  steeringMode: 'one-at-a-time',
  followUpMode: 'one-at-a-time',
});
```

### 提示方法
```typescript
// 文本提示
await agent.prompt('Hello');

// 带图片
await agent.prompt('What is this?', [
  { type: 'image', data: base64Data, mimeType: 'image/jpeg' }
]);

// 直接传递 AgentMessage
await agent.prompt({
  role: 'user',
  content: 'Hello',
  timestamp: Date.now()
});

// 从当前上下文继续
await agent.continue();
```

### 状态管理
```typescript
agent.setSystemPrompt('New prompt');
agent.setModel(getModel('openai', 'gpt-4o'));
agent.setThinkingLevel('high');
agent.setTools([myTool]);
agent.replaceMessages(newMessages);
agent.appendMessage(message);
agent.clearMessages();
agent.reset();  // 清除所有
```

### 控制
```typescript
agent.abort();           // 取消当前操作
await agent.waitForIdle(); // 等待完成
```

### 订阅事件
```typescript
const unsubscribe = agent.subscribe((event) => {
  console.log(event.type);
});
unsubscribe();
```

## Steering 和 Follow-up

### Steering 消息
在工具运行时中断 Agent：

```typescript
// Agent 正在运行工具时
agent.steer({
  role: 'user',
  content: 'Stop! Do this instead.',
  timestamp: Date.now(),
});
```

当检测到 steering 消息时：
1. 剩余工具被跳过（返回错误结果）
2. Steering 消息被注入
3. LLM 响应中断

### Follow-up 消息
在 Agent 完成当前工作后排队：

```typescript
agent.followUp({
  role: 'user',
  content: 'Also summarize the result.',
  timestamp: Date.now(),
});
```

### 模式配置
```typescript
agent.setSteeringMode('one-at-a-time'); // 或 'all'
agent.setFollowUpMode('one-at-a-time');  // 或 'all'

// 清除队列
agent.clearSteeringQueue();
agent.clearFollowUpQueue();
agent.clearAllQueues();
```

## 工具系统

### 定义工具
```typescript
import { Type } from '@sinclair/typebox';

const readFileTool: AgentTool = {
  name: 'read_file',
  label: 'Read File',
  description: 'Read a file contents',
  parameters: Type.Object({
    path: Type.String({ description: 'File path' }),
  }),
  execute: async (toolCallId, params, signal, onUpdate) => {
    const content = await fs.readFile(params.path, 'utf-8');

    // 可选：流式进度
    onUpdate?.({
      content: [{ type: 'text', text: 'Reading...' }],
      details: {}
    });

    return {
      content: [{ type: 'text', text: content }],
      details: { path: params.path, size: content.length },
    };
  },
};
```

### 错误处理
**抛出错误**而不是返回错误消息：

```typescript
execute: async (toolCallId, params, signal, onUpdate) => {
  if (!fs.existsSync(params.path)) {
    throw new Error(`File not found: ${params.path}`);
  }
  return { content: [{ type: 'text', text: '...' }] };
}
```

抛出的错误被 Agent 捕获并作为 `isError: true` 的工具错误报告给 LLM。

## 自定义消息类型

通过声明合并扩展 `AgentMessage`：

```typescript
declare module '@mariozechner/pi-agent-core' {
  interface CustomAgentMessages {
    notification: {
      role: 'notification';
      text: string;
      timestamp: number;
    };
  }
}

// 现在可以使用
const msg: AgentMessage = {
  role: 'notification',
  text: 'Info',
  timestamp: Date.now()
};
```

在 `convertToLlm` 中处理自定义类型：

```typescript
const agent = new Agent({
  convertToLlm: (messages) => messages.flatMap(m => {
    if (m.role === 'notification') return []; // 过滤掉
    return [m];
  }),
});
```

## 代理模式

用于浏览器应用通过后端代理：

```typescript
import { Agent, streamProxy } from '@mariozechner/pi-agent-core';

const agent = new Agent({
  streamFn: (model, context, options) =>
    streamProxy(model, context, {
      ...options,
      authToken: '...',
      proxyUrl: 'https://your-server.com',
    }),
});
```

## 低级 API

直接控制，不使用 Agent 类：

```typescript
import { agentLoop, agentLoopContinue } from '@mariozechner/pi-agent-core';

const context: AgentContext = {
  systemPrompt: 'You are helpful.',
  messages: [],
  tools: [],
};

const config: AgentLoopConfig = {
  model: getModel('openai', 'gpt-4o'),
  convertToLlm: (msgs) => msgs.filter(m =>
    ['user', 'assistant', 'toolResult'].includes(m.role)
  ),
};

const userMessage = {
  role: 'user',
  content: 'Hello',
  timestamp: Date.now()
};

for await (const event of agentLoop([userMessage], context, config)) {
  console.log(event.type);
}
```

## 性能考虑

- 流式处理减少首字延迟
- 事件驱动架构支持增量 UI 更新
- 工具执行过程与 steering / follow-up 队列协同工作
- 自动处理 token 限制和上下文裁剪
