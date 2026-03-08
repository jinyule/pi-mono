# Web UI 组件库规范

> 包名: `@mariozechner/pi-web-ui`

## 概述

`pi-web-ui` 提供可复用的 Web UI 组件，用于构建 AI 聊天界面。基于 [mini-lit](https://github.com/badlogic/mini-lit) Web Components 和 Tailwind CSS v4 构建。

## 核心特性

- **Chat UI**: 完整界面，包含消息历史、流式传输、工具执行
- **工具**: JavaScript REPL、文档提取、Artifacts (HTML, SVG, Markdown 等)
- **附件**: PDF, DOCX, XLSX, PPTX, 图片，支持预览和文本提取
- **Artifacts**: 交互式 HTML, SVG, Markdown，沙箱执行
- **存储**: IndexedDB 支持的会话、API keys、设置存储
- **CORS 代理**: 浏览器环境的自动代理处理
- **自定义提供商**: 支持 Ollama, LM Studio, vLLM, OpenAI 兼容 API

## 架构

```
┌─────────────────────────────────────────────────────┐
│                    ChatPanel                         │
│  ┌─────────────────────┐  ┌─────────────────────┐   │
│  │   AgentInterface    │  │   ArtifactsPanel    │   │
│  │  (消息, 输入)        │  │  (HTML, SVG, MD)    │   │
│  └─────────────────────┘  └─────────────────────┘   │
└─────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────┐
│              Agent (from pi-agent-core)              │
│  - 状态管理 (消息, 模型, 工具)                        │
│  - 事件发射 (agent_start, message_update, ...)       │
│  - 工具执行                                          │
└─────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────┐
│                   AppStorage                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐            │
│  │ Settings │ │ Provider │ │ Sessions │            │
│  │  Store   │ │Keys Store│ │  Store   │            │
│  └──────────┘ └──────────┘ └──────────┘            │
│                     │                               │
│              IndexedDBStorageBackend                │
└─────────────────────────────────────────────────────┘
```

## 快速开始

```typescript
import { Agent } from '@mariozechner/pi-agent-core';
import { getModel } from '@mariozechner/pi-ai';
import {
  ChatPanel, AppStorage, IndexedDBStorageBackend,
  CustomProvidersStore, ProviderKeysStore, SessionsStore, SettingsStore,
  setAppStorage, defaultConvertToLlm, ApiKeyPromptDialog,
} from '@mariozechner/pi-web-ui';
import '@mariozechner/pi-web-ui/app.css';

// 设置存储
const settings = new SettingsStore();
const providerKeys = new ProviderKeysStore();
const sessions = new SessionsStore();
const customProviders = new CustomProvidersStore();

const backend = new IndexedDBStorageBackend({
  dbName: 'my-app',
  version: 1,
  stores: [
    settings.getConfig(),
    providerKeys.getConfig(),
    sessions.getConfig(),
    customProviders.getConfig(),
    SessionsStore.getMetadataConfig(),
  ],
});

settings.setBackend(backend);
providerKeys.setBackend(backend);
sessions.setBackend(backend);
customProviders.setBackend(backend);

const storage = new AppStorage(settings, providerKeys, sessions, customProviders, backend);
setAppStorage(storage);

// 创建 Agent
const agent = new Agent({
  initialState: {
    systemPrompt: 'You are a helpful assistant.',
    model: getModel('anthropic', 'claude-sonnet-4-5-20250929'),
    thinkingLevel: 'off',
    messages: [],
    tools: [],
  },
  convertToLlm: defaultConvertToLlm,
});

// 创建 ChatPanel
const chatPanel = new ChatPanel();
await chatPanel.setAgent(agent, {
  onApiKeyRequired: (provider) => ApiKeyPromptDialog.prompt(provider),
});

document.body.appendChild(chatPanel);
```

## 核心组件

### ChatPanel

高级聊天界面，内置 artifacts 面板：

```typescript
const chatPanel = new ChatPanel();
await chatPanel.setAgent(agent, {
  // 需要时提示 API key
  onApiKeyRequired: async (provider) => ApiKeyPromptDialog.prompt(provider),

  // 发送消息前的钩子
  onBeforeSend: async () => { /* 保存草稿等 */ },

  // 成本显示点击处理
  onCostClick: () => { /* 显示成本明细 */ },

  // 浏览器扩展的自定义沙箱 URL
  sandboxUrlProvider: () => chrome.runtime.getURL('sandbox.html'),

  // 添加自定义工具
  toolsFactory: (agent, agentInterface, artifactsPanel, runtimeProvidersFactory) => {
    const replTool = createJavaScriptReplTool();
    replTool.runtimeProvidersFactory = runtimeProvidersFactory;
    return [replTool];
  },
});
```

### AgentInterface

低级聊天界面，用于自定义布局：

```typescript
const chat = document.createElement('agent-interface') as AgentInterface;
chat.session = agent;
chat.enableAttachments = true;
chat.enableModelSelector = true;
chat.enableThinkingSelector = true;
chat.onApiKeyRequired = async (provider) => { /* ... */ };
```

**属性**:
- `session`: Agent 实例
- `enableAttachments`: 显示附件按钮 (默认: true)
- `enableModelSelector`: 显示模型选择器 (默认: true)
- `enableThinkingSelector`: 显示思考级别选择器 (默认: true)
- `showThemeToggle`: 显示主题切换 (默认: false)

### ArtifactsPanel

Artifacts 显示和管理：

```typescript
const artifactsPanel = new ArtifactsPanel();
artifactsPanel.agent = agent;

// 工具可用作 artifactsPanel.tool
agent.setTools([artifactsPanel.tool]);
```

支持格式: HTML, SVG, Markdown, text, JSON, images, PDF, DOCX, XLSX

## 消息类型

### UserMessageWithAttachments

带文件附件的用户消息：

```typescript
const message: UserMessageWithAttachments = {
  role: 'user-with-attachments',
  content: 'Analyze this document',
  attachments: [pdfAttachment],
  timestamp: Date.now(),
};

// 类型守卫
if (isUserMessageWithAttachments(msg)) {
  console.log(msg.attachments);
}
```

### ArtifactMessage

用于 artifacts 会话持久化：

```typescript
const artifact: ArtifactMessage = {
  role: 'artifact',
  action: 'create', // 或 'update', 'delete'
  filename: 'chart.html',
  content: '<div>...</div>',
  timestamp: new Date().toISOString(),
};
```

### 自定义消息类型

通过声明合并扩展：

```typescript
declare module '@mariozechner/pi-agent-core' {
  interface CustomAgentMessages {
    'system-notification': {
      role: 'system-notification';
      message: string;
      level: 'info' | 'warning' | 'error';
      timestamp: string;
    };
  }
}

// 注册渲染器
registerMessageRenderer('system-notification', {
  render: (msg) => html`<div class="alert">${msg.message}</div>`,
});
```

## 工具系统

### JavaScript REPL

在沙箱浏览器环境中执行 JavaScript：

```typescript
import { createJavaScriptReplTool } from '@mariozechner/pi-web-ui';

const replTool = createJavaScriptReplTool();
replTool.runtimeProvidersFactory = () => [
  new AttachmentsRuntimeProvider(attachments),
  new ArtifactsRuntimeProvider(artifactsPanel, agent, true),
];

agent.setTools([replTool]);
```

### Extract Document

从 URL 提取文档文本：

```typescript
import { createExtractDocumentTool } from '@mariozechner/pi-web-ui';

const extractTool = createExtractDocumentTool();
extractTool.corsProxyUrl = 'https://corsproxy.io/?';

agent.setTools([extractTool]);
```

### 自定义工具渲染器

```typescript
import { registerToolRenderer } from '@mariozechner/pi-web-ui';

registerToolRenderer('my_tool', {
  render(params, result, isStreaming) {
    return {
      content: html`<div>...</div>`,
      isCustom: false, // true = 无卡片包装
    };
  },
});
```

## 存储系统

### 设置

```typescript
const settings = new SettingsStore();
settings.setBackend(backend);

await storage.settings.set('proxy.enabled', true);
await storage.settings.set('proxy.url', 'https://proxy.example.com');
const enabled = await storage.settings.get<boolean>('proxy.enabled');
```

### Provider Keys

```typescript
const providerKeys = new ProviderKeysStore();
providerKeys.setBackend(backend);

await storage.providerKeys.set('anthropic', 'sk-ant-...');
const key = await storage.providerKeys.get('anthropic');
const providers = await storage.providerKeys.list();
```

### Sessions

```typescript
const sessions = new SessionsStore();
sessions.setBackend(backend);

await storage.sessions.save(sessionData, metadata);
const data = await storage.sessions.get(sessionId);
const metadata = await storage.sessions.getMetadata(sessionId);
const allMetadata = await storage.sessions.getAllMetadata();
await storage.sessions.updateTitle(sessionId, 'New Title');
await storage.sessions.delete(sessionId);
```

### Custom Providers

```typescript
const customProviders = new CustomProvidersStore();
customProviders.setBackend(backend);

const provider: CustomProvider = {
  id: crypto.randomUUID(),
  name: 'My Ollama',
  type: 'ollama',
  baseUrl: 'http://localhost:11434',
};

await storage.customProviders.set(provider);
const all = await storage.customProviders.getAll();
```

## 附件处理

```typescript
import { loadAttachment, type Attachment } from '@mariozechner/pi-web-ui';

// 从 File input
const attachment = await loadAttachment(file);

// 从 URL
const attachment = await loadAttachment('https://example.com/doc.pdf');

// 从 ArrayBuffer
const attachment = await loadAttachment(arrayBuffer, 'document.pdf');

// Attachment 结构
interface Attachment {
  id: string;
  type: 'image' | 'document';
  fileName: string;
  mimeType: string;
  size: number;
  content: string;        // base64 编码
  extractedText?: string; // 文档提取的文本
  preview?: string;       // base64 预览图
}
```

支持格式: PDF, DOCX, XLSX, PPTX, images, text files

## CORS 代理

浏览器环境处理 CORS 限制：

```typescript
import { createStreamFn, shouldUseProxyForProvider, isCorsError } from '@mariozechner/pi-web-ui';

// AgentInterface 自动从设置配置代理
// 手动设置：
agent.streamFn = createStreamFn(async () => {
  const enabled = await storage.settings.get<boolean>('proxy.enabled');
  return enabled ? await storage.settings.get<string>('proxy.url') : undefined;
});

// 需要代理的提供商:
// - zai: 总是需要
// - anthropic: 仅 OAuth tokens (sk-ant-oat-*)
```

## 对话框组件

### SettingsDialog

```typescript
import { SettingsDialog, ProvidersModelsTab, ProxyTab, ApiKeysTab } from '@mariozechner/pi-web-ui';

SettingsDialog.open([
  new ProvidersModelsTab(),
  new ProxyTab(),
  new ApiKeysTab(),
]);
```

### SessionListDialog

```typescript
import { SessionListDialog } from '@mariozechner/pi-web-ui';

SessionListDialog.open(
  async (sessionId) => { /* 加载会话 */ },
  (deletedId) => { /* 处理删除 */ },
);
```

### ModelSelector

```typescript
import { ModelSelector } from '@mariozechner/pi-web-ui';

ModelSelector.open(currentModel, (selectedModel) => {
  agent.setModel(selectedModel);
});
```

## 样式

导入预构建 CSS：

```typescript
import '@mariozechner/pi-web-ui/app.css';
```

或使用 Tailwind 自定义配置：

```css
@import '@mariozechner/mini-lit/themes/claude.css';
@tailwind base;
@tailwind components;
@tailwind utilities;
```

## 国际化

```typescript
import { i18n, setLanguage, translations } from '@mariozechner/pi-web-ui';

translations.de = {
  'Loading...': 'Laden...',
  'No sessions yet': 'Noch keine Sitzungen',
};

setLanguage('de');
console.log(i18n('Loading...')); // "Laden..."
```
