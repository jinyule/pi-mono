# Java 版本 Pi 详细设计

## 架构图

```text
                           +----------------------+
                           |      pi-cli          |
                           | modes, args, boot    |
                           +----------+-----------+
                                      |
                     +----------------+----------------+
                     |                                 |
             +-------v--------+                +-------v--------+
             |    pi-tui      |                |    pi-sdk      |
             | render/input   |                | public facade  |
             +-------+--------+                +-------+--------+
                     |                                 |
                     +----------------+----------------+
                                      |
                           +----------v-----------+
                           |  pi-agent-runtime    |
                           | agent loop/events    |
                           +-----+-----------+----+
                                 |           |
                     +-----------v--+     +--v---------------+
                     |   pi-tools   |     | pi-extension-spi |
                     | builtin tools|     | plugins/events   |
                     +-----------+--+     +--+---------------+
                                 |           |
                                 +-----+-----+
                                       |
                           +-----------v-----------+
                           |     pi-session        |
                           | sessions/settings     |
                           +-----------+-----------+
                                       |
                           +-----------v-----------+
                           |       pi-ai           |
                           | providers/models/auth |
                           +-----------------------+
```

## 设计原则

### 1. 语义兼容优先于源码兼容

Java 版不追求一比一复刻 TypeScript 文件结构，但以下语义是硬约束：

- 流式事件名和生命周期
- 工具调用模型
- 会话树与 compaction 语义
- 资源加载顺序
- 用户可感知的命令和交互模式

### 2. 数据优先

如果只能二选一，优先兼容以下数据层：

- `Session JSONL`
- `Message JSON`
- `settings.json`
- skills / prompts / themes 文件格式

原因很直接：UI 可以逐步打磨，数据不兼容会直接破坏迁移路径。

### 3. Java-native，而不是 Node 模拟器

Java 版必须主动拥抱 Java 生态：

- `Java 21`
- `records`
- `sealed interfaces`
- `CompletableFuture`
- `virtual threads`
- `ServiceLoader`
- `HttpClient`

而不是在 JVM 上硬模仿 Node.js 的 async iterator 与 module loader。

### 4. TDD 默认化，而不是测试收尾化

Java 版实现默认走 `red -> green -> refactor`，并把测试看作架构边界的一部分。

这条原则在本项目里不是形式主义，原因很现实：

1. 我们要追平的是行为语义，不是表面 API；没有 golden tests 很容易“看起来能跑，实际不兼容”。
2. `provider stream`、`session replay`、`TUI diff render` 都属于高回归风险区域，晚补测试几乎等于不可维护。
3. Java 版需要和现有 TypeScript 版本做语义对照，最稳的方式就是 fixtures 和 contract tests 先行。

## 技术选型

### 运行时与构建

- JDK: `Java 21`
- Build: `Gradle Kotlin DSL`
- Test: `JUnit 5 + AssertJ`

### 数据与配置

- JSON / JSONL / YAML: `Jackson`
- Markdown: `commonmark-java`
- JSON Schema validation: `networknt/json-schema-validator`

### CLI / TUI

- CLI parsing: `picocli`
- Terminal abstraction: `JLine 3`
- ANSI / diff render: 自研 `pi-tui` 渲染器，建立在 JLine terminal 之上

### HTTP / OAuth / 云 provider

- 通用 HTTP 与 WebSocket: `java.net.http.HttpClient`
- AWS Bedrock: `AWS SDK v2`
- Google Vertex / OAuth: `Google Auth Library`

选择 `HttpClient` 而不是再引入一个通用 HTTP 栈，原因是：

1. Java 21 已经足够稳定。
2. SSE 可以自研轻量 parser。
3. 降低依赖面，方便后续嵌入式和受限环境部署。

## 模块拆分

## `pi-ai`

### 职责

- 注册 API provider
- 模型发现与查询
- 请求鉴权
- provider payload 转换
- SSE / WebSocket / SDK 响应归一化
- 生成统一的 `AssistantMessageEventStream`

### 核心接口

```java
public interface ApiProvider {
    String api();

    AssistantMessageEventStream stream(
        Model model,
        Context context,
        StreamOptions options
    );

    AssistantMessageEventStream streamSimple(
        Model model,
        Context context,
        SimpleStreamOptions options
    );
}
```

```java
public interface AssistantMessageEventStream extends AutoCloseable {
    Subscription subscribe(Consumer<AssistantMessageEvent> listener);
    CompletableFuture<AssistantMessage> result();
    void cancel();
}
```

### 关键子组件

- `ApiProviderRegistry`
- `ModelRegistry`
- `CredentialResolver`
- `ModelQueryService`
- `SseEventParser`
- `AssistantMessageAssembler`
- `ProviderCompatibilityMapper`

### 数据模型

保留与现有 `pi-ai` 等价的核心类型：

- `KnownApi`
- `KnownProvider`
- `Model`
- `Context`
- `StreamOptions`
- `SimpleStreamOptions`
- `Message`
- `Tool`
- `AssistantMessageEvent`

### Provider 实现策略

第一阶段优先级：

1. `openai-responses`
2. `openai-completions`
3. `anthropic-messages`
4. `google-generative-ai`
5. `bedrock-converse-stream`

原因：

- 这五条链路已经覆盖现有 Pi 设计中的主路径。
- `openai-completions` 还能承接大量 OpenAI-compatible provider。
- `bedrock` 和 `google` 代表 “官方 SDK + 兼容层” 两种复杂 provider 模式。

### 认证模型

认证抽象分三层：

```text
CLI explicit args
  -> Settings / saved defaults
  -> Env vars
  -> OAuth token store
  -> Provider-specific runtime resolver
```

接口建议：

```java
public interface CredentialSource {
    Optional<Credential> resolve(String provider);
}
```

`Credential` 至少支持：

- `ApiKeyCredential`
- `BearerTokenCredential`
- `AwsCredentialSet`
- `GoogleAdcCredential`

### 推理与 Thinking

统一接口仍保留 `minimal/low/medium/high/xhigh`，但 provider 可以降级映射。

映射规则：

- OpenAI 维持原生 level
- Anthropic / Google / 其他 token-based provider 通过 `thinkingBudgets` 和 provider-specific options 映射
- 不支持 reasoning 的模型忽略该字段，不报错

## `pi-agent-runtime`

### 职责

- 管理 `AgentState`
- 驱动 LLM -> tool -> LLM 的 turn loop
- 管理 streaming lifecycle
- 发出 UI / SDK 事件
- 管理 steering / follow-up 队列

### 核心接口

```java
public final class Agent {
    AgentState state();
    void prompt(String text);
    void prompt(UserMessage message);
    void continueRun();
    void abort();
    void steer(AgentMessage message);
    void followUp(AgentMessage message);
    AutoCloseable subscribe(Consumer<AgentEvent> listener);
}
```

```java
public record AgentLoopConfig(
    Model model,
    Function<List<AgentMessage>, CompletionStage<List<Message>>> convertToLlm,
    ContextTransformer transformContext,
    CredentialResolver getApiKey,
    SteeringSource getSteeringMessages,
    FollowUpSource getFollowUpMessages,
    SimpleStreamOptions options
) {}
```

### 运行时模型

每次 `prompt()` 启动一个 run：

```text
user message
  -> agent_start
  -> turn_start
  -> stream assistant response
  -> message_update*
  -> tool calls?
       yes -> sequential tool execution + steering check
       no  -> follow-up check
  -> turn_end
  -> next turn or agent_end
```

### 关键决策：工具默认顺序执行

Java 版默认仍保持顺序执行，而不是并发 tool calls。

原因：

1. 现有 Pi 语义就是顺序执行。
2. `steering` 依赖在每个工具之后插队。
3. `bash/edit/write` 这类有副作用的工具并发价值有限，风险反而更高。

未来可以增加 `parallelToolExecution=false/true` 的扩展点，但默认值必须是 `false`。

### 自定义消息

Java 版用 sealed hierarchy 代替 TS 的 declaration merging：

```java
public sealed interface AgentMessage
    permits UserMessage, AssistantMessage, ToolResultMessage, CustomAgentMessage {}

public record CustomAgentMessage(
    String role,
    Object payload,
    long timestamp
) implements AgentMessage {}
```

`convertToLlm()` 决定这些 `CustomAgentMessage` 是否进入 LLM 上下文。

## `pi-session`

### 职责

- 管理会话文件
- 恢复上下文
- 维护树结构
- 做 session migration
- 管理 settings layering

### Session JSONL

继续沿用现有格式：

```text
{"type":"session","version":3,...}
{"type":"message","id":"...","parentId":null,...}
{"type":"model_change","id":"...","parentId":"..."}
{"type":"compaction","id":"...","parentId":"..."}
```

### 核心类型

- `SessionHeader`
- `SessionEntry`
- `SessionTreeNode`
- `SessionContext`
- `SessionInfo`

### SessionManager 设计

```java
public interface SessionManager {
    SessionInfo current();
    Optional<Path> currentFile();
    String appendMessage(Message message);
    String appendThinkingLevelChange(String level);
    String appendModelChange(String provider, String modelId);
    String appendCompaction(CompactionEntry entry);
    String appendBranchSummary(BranchSummaryEntry entry);
    SessionContext buildContext(@Nullable String leafId);
    List<SessionTreeNode> tree();
    void navigate(@Nullable String leafId);
    Path newSession(@Nullable Path parentSession);
    void switchSession(Path path);
}
```

### 关键决策：保持现有持久化节奏

现有 TS 版本会在“尚未出现 assistant message”之前延迟 flush，避免只写入半成品会话。Java 版建议保留这个节奏：

- 用户消息先进入内存
- 直到第一次 assistant 出现，再批量刷到 JSONL
- 后续增量 append

这样可减少意外中断产生的“只含 prompt 无响应”的噪声会话文件。

### SettingsManager 设计

继续保留：

- 全局：`~/.pi/agent/settings.json`
- 项目：`.pi/settings.json`
- nested object deep merge
- arrays direct override
- file lock + migration

接口建议：

```java
public interface SettingsManager {
    Settings effective();
    Settings global();
    Settings project();
    void reload();
    void applyOverrides(Settings overrides);
    void updateGlobal(UnaryOperator<Settings> update);
    void updateProject(UnaryOperator<Settings> update);
}
```

## `pi-tools`

### 职责

- 提供内置工具
- 封装 truncation、diff、图片处理、shell execution
- 对本地 / 远程 / sandbox runtime 提供 pluggable operations

### 工具接口

```java
public interface AgentTool<P, D> {
    String name();
    String label();
    String description();
    JsonNode parametersSchema();
    CompletionStage<AgentToolResult<D>> execute(
        String toolCallId,
        P params,
        @Nullable CancellationToken cancellationToken,
        @Nullable Consumer<AgentToolResult<D>> onUpdate
    );
}
```

### 保留的内置工具

- `read`
- `bash`
- `edit`
- `write`
- `grep`
- `find`
- `ls`

### 工具语义映射

#### `read`

必须支持：

- 文本文件读取
- 图片识别与 attachment 输出
- `offset` / `limit`
- 行数 / 字节数双重截断
- 超大单行回退到 shell 方案提示

#### `bash`

必须支持：

- stdout/stderr 合并流式更新
- 可选 timeout
- 输出尾部截断
- 超限时落盘临时文件
- abort 时 kill process tree

#### `edit`

必须支持：

- exact text replace
- fuzzy fallback 仅用于定位，不是自由编辑
- diff 生成
- duplicate match 报错

#### `write`

必须支持：

- 自动创建父目录
- 覆盖写
- 明确 success message

### 关键决策：工具 Schema 使用 JSON Schema，而不是 Java 反射自动推断

原因：

1. 现有 Pi 的工具系统本质是 “给模型看的 schema”，不是给 JVM 自己看的。
2. JSON Schema 更利于 RPC、session replay、plugin 隔离与未来跨语言扩展。
3. Java record 可以做参数映射层，但不应该成为 source of truth。

## `pi-extension-spi`

### 为什么不能照搬 TypeScript Extensions

现有实现是 TypeScript module + runtime loader。Java 版如果照搬，会带来两类长期问题：

1. 运行时复杂度失控：需要 JS/TS 解释环境、类型桥接、线程桥接。
2. 部署模型混乱：Java 版本应交付单一 JVM runtime，而不是再嵌一套 Node。

因此 Java 版改为 Java-native SPI，但继续保留 Pi 的扩展哲学。

### 插件包装形式

首选：

- `JAR` plugin
- `META-INF/services/dev.pi.extension.spi.PiExtensionFactory`
- 每个插件独立 `URLClassLoader`
- `/reload` 时销毁旧 classloader，重建运行时

### ExtensionAPI

Java SPI 保留与现有 TS API 等价的核心能力：

- 注册事件处理器
- 注册工具
- 注册 slash command
- 注册 shortcut
- 注册 CLI flag
- 注册 message renderer
- 发送用户消息 / 自定义消息
- 修改 session metadata
- 访问 UI context

接口建议：

```java
public interface PiExtension {
    void initialize(ExtensionApi pi);
}
```

```java
public interface ExtensionApi {
    <E> void on(String eventType, ExtensionHandler<E> handler);
    void registerTool(ToolDefinition<?, ?> tool);
    void registerCommand(String name, CommandDefinition definition);
    void registerShortcut(String keyId, ShortcutDefinition definition);
    void registerFlag(String name, FlagDefinition definition);
    void registerMessageRenderer(String customType, MessageRenderer renderer);
    void sendUserMessage(UserContent content, DeliveryMode deliveryMode);
    void appendEntry(String customType, Object data);
    void setSessionName(String name);
}
```

### UI Context

保留 mode-aware 设计：

- interactive mode: 全功能 UI
- print/json/rpc: no-op 或 restricted UI context

这与现有 `ExtensionUIContext` 一致，只是 Java 版通过接口而不是 TS 类型系统表达。

### Skills / Prompts / Themes

这三类资源继续保持 file-based：

- `Skills`: Markdown `SKILL.md`
- `Prompt Templates`: Markdown + frontmatter / placeholder
- `Themes`: JSON

这样 Java 版依然能复用大量非代码资源。

## `pi-tui`

### 职责

- raw terminal input
- diff render
- overlay system
- component tree
- focus / IME cursor placement
- inline images

### 为什么选 JLine + 自研 renderer

不建议直接用 Lanterna / Text-UI 框架替代，原因：

1. Pi 依赖非常细的终端控制：Kitty keyboard protocol、bracketed paste、IME cursor、同步输出、inline image。
2. 通用 TUI 框架往往会屏蔽这些细节，导致后续反向打洞更痛苦。
3. 现有 `pi-tui` 的价值不是组件数量，而是差分渲染策略。

因此：

- JLine 负责跨平台 terminal 创建、raw mode、signals、size
- `pi-tui` 自己负责 input parsing、render scheduling、overlay compositing、hardware cursor

### 核心接口

```java
public interface Component {
    List<String> render(int width);
    default void handleInput(KeyEvent event) {}
    default void invalidate() {}
}
```

```java
public interface Focusable {
    boolean focused();
    void setFocused(boolean focused);
}
```

```java
public interface Terminal {
    void start(InputHandler onInput, Runnable onResize);
    void stop();
    void write(String data);
    int columns();
    int rows();
    void hideCursor();
    void showCursor();
    void clearScreen();
    void setTitle(String title);
}
```

### 差分渲染策略

直接沿用现有三段式策略：

1. 首帧全量输出
2. 宽度变化或可视区上方有改动时整屏重绘
3. 普通更新只重绘变更区

并保留 synchronized output 语义，避免闪烁。

### IME 支持

这部分必须保留现有方案：

- Focused component 在 render 中插入零宽 cursor marker
- TUI 扫描 marker，计算硬件 cursor 位置
- terminal 显示真实 cursor

原因是 CJK IME 候选窗定位依赖真实 cursor，而不是 fake cursor。

### 组件优先级

第一阶段先做这些：

- `Container`
- `Text`
- `TruncatedText`
- `Input`
- `Editor`
- `Markdown`
- `Loader`
- `SelectList`
- `SettingsList`
- `Spacer`
- `Image`

## `pi-cli`

### 运行模式

- `interactive`
- `print`
- `json`
- `rpc`
- `sdk`

### 启动流程

```text
parse args
  -> load settings
  -> discover resources
  -> load models and credentials
  -> create SessionManager
  -> create AgentSession
  -> choose mode
  -> enter loop
```

### AgentSession 角色

Java 版需要一个与现有 `AgentSession` 同级的 orchestration object。它不是 UI，也不是裸 agent，而是“把 agent、session、settings、resource loader、extensions、tools 绑在一起”的应用核心。

建议类名：

- `PiAgentSession`

职责：

- 订阅 AgentEvent 并同步 session
- 构建 runtime tool registry
- 管理 active tools 与 system prompt
- 触发 compaction / retry / model cycling
- 把 extension runtime 与 mode 绑定

## 关键数据流

### 1. Prompt -> LLM -> Tool -> LLM

```text
editor input
  -> PiAgentSession.prompt()
  -> Agent.prompt()
  -> transformContext()
  -> convertToLlm()
  -> pi-ai.streamSimple()
  -> AssistantMessageEvent*
  -> tool execution
  -> ToolResultMessage
  -> next turn
```

### 2. Session Replay

```text
SessionManager.load()
  -> migrate entries
  -> build tree index
  -> buildSessionContext(leafId)
  -> restore messages / thinking level / model
```

### 3. Extension Reload

```text
/reload
  -> session_shutdown
  -> reload settings
  -> reload resources
  -> rebuild tool registry
  -> rebuild system prompt
  -> session_start
```

## 线程与并发模型

### 总原则

- TUI 主循环单线程
- 网络请求与工具执行使用 virtual threads
- 事件派发有序，默认串行

### 推荐实现

1. `InteractiveMode` 使用单线程 event loop 维护 UI state。
2. 每次 agent run 由一个 virtual thread 协调。
3. provider streaming 在专用 virtual thread 中读取网络流并 push event。
4. tool execution 使用单独 virtual thread，但在 agent 级别保持顺序 await。
5. extension handlers 默认串行执行，避免顺序不稳定。

### 为什么不直接上 Reactor

Reactor 当然能做，但这里不值得：

1. Pi 的业务是 event lifecycle，不是复杂 backpressure pipeline。
2. `CompletableFuture + listener` 足够表达需求。
3. 对插件作者和调试都更简单。

## 测试架构

### 测试分层

```text
contract / golden tests
    -> module integration tests
        -> end-to-end CLI/TUI tests
```

### 建议测试策略

#### `pi-ai`

- 每个 provider 都要有统一 contract suite
- 用录制响应或 stub server 验证事件顺序
- 对 `abort`、`toolcall_delta`、`thinking_delta` 做单独测试

#### `pi-agent-runtime`

- 以事件序列断言 agent 行为，而不是只断言最终字符串
- 把 `turn_start -> message_update -> tool_execution_end -> turn_end` 当作稳定 contract

#### `pi-session`

- 使用真实 JSONL fixture 验证 migration 与 replay
- 必须覆盖 compaction、branch summary、custom entry、tree navigation

#### `pi-tui`

- 用 `VirtualTerminal` 做 golden render tests
- 对键位、bracketed paste、kitty protocol、IME cursor marker 建 fixture

#### `pi-cli`

- `print/json/rpc` 优先做集成测试
- `interactive` 通过虚拟终端脚本测试关键路径，而不是依赖人工回归

## 包结构建议

```text
dev.pi.ai
dev.pi.ai.model
dev.pi.ai.provider
dev.pi.ai.stream
dev.pi.ai.auth

dev.pi.agent.runtime
dev.pi.agent.runtime.loop
dev.pi.agent.runtime.tool
dev.pi.agent.runtime.event

dev.pi.session
dev.pi.session.model
dev.pi.session.migration
dev.pi.session.settings

dev.pi.tools
dev.pi.tools.builtin
dev.pi.tools.support

dev.pi.extension.spi
dev.pi.extension.runtime

dev.pi.tui
dev.pi.tui.component
dev.pi.tui.input
dev.pi.tui.render

dev.pi.cli
dev.pi.cli.mode
dev.pi.cli.command

dev.pi.sdk
```

## 与现有 TypeScript 实现的映射

| TypeScript 现有边界 | Java 目标边界 |
|---|---|
| `packages/ai` | `pi-ai` |
| `packages/agent` | `pi-agent-runtime` |
| `packages/coding-agent/src/core/session-manager.ts` | `pi-session` |
| `packages/coding-agent/src/core/settings-manager.ts` | `pi-session` |
| `packages/coding-agent/src/core/tools/*` | `pi-tools` |
| `packages/coding-agent/src/core/extensions/*` | `pi-extension-spi` |
| `packages/tui` | `pi-tui` |
| `packages/coding-agent/src/modes/*` | `pi-cli` |

## 关键风险与缓解

### 风险 1：TUI 跨平台键盘输入不一致

缓解：

- 保留 raw-sequence parser
- 建 `VirtualTerminal` 测试夹具
- 单独做 Windows Terminal / Kitty / WezTerm / tmux 兼容矩阵

### 风险 2：插件热重载导致类加载器泄漏

缓解：

- 每次 reload 严格销毁旧 runtime
- 插件只通过 SPI 暴露对象，禁止回传自定义 class 到核心层
- 统一用 Jackson `JsonNode` / DTO 边界做隔离

### 风险 3：Session 格式偏差导致与 TS 互不兼容

缓解：

- 用真实 TS 生成的 JSONL 作为 golden fixtures
- Java 版实现读取旧版本与回写测试
- 先做 “可读可回放”，再做“可写”

### 风险 4：Provider 适配面过大

缓解：

- 第一阶段集中做 4 到 5 个 provider
- `openai-completions` 作为兼容层吸收一批 provider
- 模型发现脚本与 provider runtime 分离

## 关键决策总结

1. 保留 Pi 的语义层，不做 Node.js 运行时复刻。
2. 用 `Java 21 + records + virtual threads + CompletableFuture` 建立核心运行时。
3. 用 `JLine + 自研 diff renderer` 实现 TUI，而不是依赖通用 TUI 框架。
4. 用 `JAR plugin + ServiceLoader + isolated ClassLoader` 替代 TypeScript extensions。
5. 优先兼容 `session/settings/message/tool` 数据层，再逐步追平所有 provider 与 UI 细节。
