# Java 版本 Pi 的 TDD 实施模板

## 目标

这份文档不是重复说明 “要写测试”，而是把 `pi-java` 的 TDD 落成一套统一操作方式。

目标有三条：

1. 把行为兼容变成可验证的 contract，而不是口头约定。
2. 把高回归风险模块提前纳入 fixture 和 golden test 保护。
3. 让不同模块都能按同一节奏推进：`red -> green -> refactor`。

## 统一流程

每个需求、bug fix、重构任务，都按下面 5 步走：

1. 先确定“要守住的行为”。
2. 写一个最小失败测试，或者先补一个 fixture/golden case。
3. 运行测试，确认它真的失败，而且失败原因正确。
4. 写最小实现让测试变绿。
5. 在绿灯状态下重构，并再次跑完整相关测试集。

这里有一个硬规则：

- 没有先失败过的测试，不算 TDD。

## 测试层次

`pi-java` 建议固定为四层测试：

1. `unit test`
2. `contract test`
3. `golden/fixture test`
4. `integration/e2e test`

职责边界如下：

- `unit test`：验证单个类或单个算法。
- `contract test`：验证一个模块对外承诺的行为协议。
- `golden/fixture test`：验证输出格式、事件顺序、会话文件、终端渲染等稳定产物。
- `integration/e2e test`：验证多模块协同。

## 推荐目录

```text
pi-java/
  modules/
    pi-ai/
      src/main/java/...
      src/test/java/...
      src/test/resources/
        fixtures/
        golden/
    pi-agent-runtime/
    pi-session/
    pi-tools/
    pi-extension-spi/
    pi-tui/
    pi-cli/
    pi-sdk/
```

`fixtures` 与 `golden` 的职责分开：

- `fixtures`：输入样本、录制响应、session jsonl、键盘序列、provider payload
- `golden`：期望输出，例如事件流、终端渲染文本、格式化结果、diff 文本

## 命名约定

推荐统一命名，避免测试类型混乱：

- `*Test`：unit test
- `*ContractTest`：module contract test
- `*GoldenTest`：golden/fixture test
- `*IntegrationTest`：integration test
- `*E2ETest`：端到端测试

示例：

- `OpenAiResponsesProviderContractTest`
- `AgentLoopSteeringTest`
- `SessionReplayGoldenTest`
- `BashToolIntegrationTest`
- `InteractiveModeE2ETest`

## Red 阶段模板

开始实现前，先填这 4 行：

```text
行为：
输入：
期望输出：
不能退化的旧行为：
```

然后选测试类型：

- 新算法或数据结构：`unit test`
- 新 provider、新事件语义、新工具语义：`contract test`
- 新文件格式、新渲染、新输出样式：`golden test`
- 新模式或跨模块行为：`integration/e2e test`

## Green 阶段模板

Green 阶段只允许做两类事：

1. 实现测试明确要求的最小路径。
2. 为了让测试跑通而补必要的注入点、fake、adapter。

Green 阶段不做这些事：

- 顺手优化一堆接口
- 顺手改命名风格
- 顺手抽象通用框架
- 顺手“提前设计未来扩展”

## Refactor 阶段模板

进入 Refactor 前，必须满足：

1. 新测试已经变绿。
2. 相关旧测试没有变红。
3. 当前行为已经被 fixture 或 contract 固化。

Refactor 优先级：

1. 去重复
2. 收窄接口
3. 拆大类
4. 提升命名
5. 提高可测试性

## 模块级模板

## `pi-ai`

### 测试重点

- provider 输入输出 contract
- streaming event 顺序
- tool calling block 组装
- thinking/reasoning block 组装
- abort / error / retry 语义
- context serialization

### 必备测试类型

1. `ProviderContractTest`
2. `ProviderGoldenTest`
3. `ModelRegistryTest`
4. `CredentialResolverTest`

### 推荐夹具

```text
src/test/resources/fixtures/providers/openai-responses/*.json
src/test/resources/fixtures/providers/anthropic/*.json
src/test/resources/golden/providers/openai-responses/*.jsonl
```

### 合同断言模板

每个 provider 至少验证这些事件：

1. `start`
2. `text_start/text_delta/text_end`
3. `toolcall_start/toolcall_delta/toolcall_end`
4. `thinking_*`，若该 provider 支持
5. `done` 或 `error`

每个 provider 至少验证这些最终字段：

- `AssistantMessage.role == assistant`
- `provider`
- `model`
- `stopReason`
- `usage`
- `content[]` 的 block 顺序

### 建议 fake

- `StubSseServer`
- `StubWebSocketServer`
- `FakeCredentialSource`
- `FakeClock`

## `pi-agent-runtime`

### 测试重点

- agent loop 生命周期
- `convertToLlm` / `transformContext` 调用顺序
- streaming partial message 更新
- sequential tool execution
- steering / follow-up 队列
- tool error 转 `ToolResultMessage(isError=true)`

### 必备测试类型

1. `AgentLoopContractTest`
2. `AgentStateTransitionTest`
3. `SteeringAndFollowUpTest`
4. `ToolExecutionIntegrationTest`

### 合同断言模板

至少覆盖以下事件序列：

```text
agent_start
turn_start
message_start(user)
message_end(user)
message_start(assistant)
message_update*
message_end(assistant)
turn_end
agent_end
```

带工具调用时至少覆盖：

```text
tool_execution_start
tool_execution_update*
tool_execution_end
message_start(toolResult)
message_end(toolResult)
```

### 推荐 fake

- `FakeAiClient`
- `FakeAssistantMessageEventStream`
- `FakeTool`
- `CapturingAgentEventListener`

## `pi-session`

### 测试重点

- session JSONL 读写
- migration
- tree replay
- compaction summary 注入
- branch summary 注入
- settings deep merge

### 必备测试类型

1. `SessionJsonlGoldenTest`
2. `SessionMigrationTest`
3. `SessionReplayContractTest`
4. `SettingsManagerTest`

### Golden 资产

- 从现有 TypeScript 版本导出真实 session 文件
- 包含这些情况：
  - 线性会话
  - fork 后多分支
  - compaction
  - branch summary
  - custom entry
  - label / session_info

### 合同断言模板

`buildSessionContext()` 至少验证：

1. 指定 `leafId` 时只回放从 root 到 leaf 的路径。
2. 遇到 compaction 时先产出 compaction summary，再产出 kept messages。
3. `thinkingLevel` 和 `model` 以路径上最近一次变更为准。

### 推荐 fake

- `InMemorySessionStore`
- `FakeFileLock`
- `FixedUuidGenerator`
- `FixedClock`

## `pi-tools`

### 测试重点

- tool schema
- tool 文本输出
- details 字段
- timeout/abort
- truncation 提示文本
- diff 输出稳定性

### 必备测试类型

1. `ReadToolGoldenTest`
2. `BashToolIntegrationTest`
3. `EditToolGoldenTest`
4. `WriteToolTest`
5. `TruncationTest`

### 推荐 fake

- `FakeFileSystem`
- `FakeShellExecutor`
- `FakeImageResizer`
- `FakeMimeDetector`

### 工具级断言模板

#### `read`

- offset/limit 正确
- 截断提示正确
- 图片返回 `text + image`
- 超大单行提示正确

#### `bash`

- stdout/stderr 都被采集
- partial update 可观察
- timeout 语义正确
- abort 语义正确
- full output file 路径正确暴露

#### `edit`

- exact match 替换成功
- duplicate match 报错
- not found 报错
- diff 文本稳定

## `pi-extension-spi`

### 测试重点

- 插件发现
- tool/command/shortcut 注册
- event handler 调用顺序
- reload 后 runtime 重建
- classloader 隔离

### 必备测试类型

1. `ExtensionLoaderIntegrationTest`
2. `ExtensionApiContractTest`
3. `ExtensionReloadTest`
4. `ExtensionIsolationTest`

### 推荐 fake

- `FakeExtensionJarBuilder`
- `CapturingExtensionRuntime`
- `FakeUiContext`

### 合同断言模板

- 插件初始化后能看到注册项
- `/reload` 后旧插件实例不再接收事件
- 同名工具冲突时行为明确且可测

## `pi-tui`

### 测试重点

- diff render
- overlay compositing
- 输入事件分发
- cursor positioning
- bracketed paste
- kitty keyboard protocol
- IME marker 定位

### 必备测试类型

1. `TuiRenderGoldenTest`
2. `EditorInputTest`
3. `OverlayLayoutTest`
4. `ImeCursorPositionTest`
5. `TerminalProtocolTest`

### 推荐 fake

- `VirtualTerminal`
- `RecordedInputStream`
- `FakeTerminalSizeSource`

### Golden 资产

- 每个渲染场景保存：
  - 输入状态
  - 终端宽高
  - 渲染输出
  - 硬件 cursor 位置

## `pi-cli`

### 测试重点

- args parsing
- mode bootstrap
- session resume
- model selection
- slash commands
- export/json/rpc output

### 必备测试类型

1. `CliArgsTest`
2. `PrintModeIntegrationTest`
3. `JsonModeIntegrationTest`
4. `RpcModeIntegrationTest`
5. `InteractiveModeE2ETest`

### 推荐 fake

- `FakeAgentSession`
- `FakeModelRegistry`
- `VirtualTerminal`
- `FakeClipboard`

## `pi-sdk`

### 测试重点

- public facade 的稳定性
- in-memory session
- 外部嵌入调用

### 必备测试类型

1. `SdkSmokeTest`
2. `CreateAgentSessionContractTest`

## Golden / Fixture 策略

## 什么时候用 golden

这些场景优先用 golden：

- provider 事件流
- session JSONL
- tool 输出文本
- diff
- TUI 渲染
- JSON mode 输出

## 什么时候不用 golden

这些场景更适合普通断言：

- 简单 getter/setter
- 小型纯算法
- 局部状态转换

## Golden 更新规则

只有两种情况允许更新 golden：

1. 需求明确改变了行为。
2. 旧 golden 本身就是错的，并且有规格依据证明它错。

更新 golden 时，必须同时写清楚：

- 为什么要改
- 改了哪些行为
- 哪些旧行为不再兼容

## “先写什么测试” 决策表

| 任务类型 | 第一优先测试 |
|---|---|
| 新 provider | contract + golden |
| 新工具 | golden + integration |
| session/migration | golden + contract |
| TUI 交互 | virtual terminal golden |
| CLI 新模式 | integration/e2e |
| bug fix | 最小失败回归测试 |
| 纯算法优化 | unit test |

## Definition of Done

一个任务只有在满足以下条件后才算完成：

1. 有明确测试覆盖新增或修复的行为。
2. 新测试在改动前失败、改动后变绿。
3. 相关 golden 已更新或确认无需更新。
4. 旧 contract tests 没有退化。
5. 如果改动影响兼容性，文档里明确写出。

## 最小工作示例

### 场景：给 `read` 工具增加图片尺寸提示

#### Red

写 `ReadToolGoldenTest`：

- 输入：一张超过阈值的 PNG
- 期望：输出 `text` block 中包含尺寸说明，且后面有 `image` block

#### Green

- 给 `read` 工具接入 `ImageResizer`
- 在文本说明里拼接 dimension note
- 只改最小路径

#### Refactor

- 提取 `formatDimensionNote()`
- 清理重复字符串拼接
- 保持测试全绿

## 结论

`pi-java` 的 TDD 不应只是“写点单元测试”，而应围绕三类高价值资产展开：

1. `contract`
2. `fixture/golden`
3. `virtualized integration environment`

如果这三层先建立起来，Java 版 Pi 才能在追平现有 TypeScript 语义时保持可控。
