# pi-java 交接文档

更新时间：2026-03-09

## 当前状态

`pi-java` 已从“纯设计文档目录”推进到“可运行的阶段 0 工程骨架 + 阶段 1 的首批 `pi-ai` 核心类型”。

本次工作只改动了 `pi-java/`，没有改动现有 TypeScript 包的实现逻辑。

## 已完成内容

### 1. 阶段 0：Gradle 多模块工程骨架

已新增并打通以下入口：

- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle.properties`
- `gradlew`
- `gradlew.bat`

已建立的模块目录：

- `modules/pi-ai`
- `modules/pi-agent-runtime`
- `modules/pi-session`
- `modules/pi-tools`
- `modules/pi-extension-spi`
- `modules/pi-tui`
- `modules/pi-cli`
- `modules/pi-sdk`

这些模块目前都能参与 Gradle 构建和测试，其中大部分仍是最小骨架/marker 状态。

### 2. 已导入的种子 fixture

已从现有 TypeScript 仓库复制两份最早可复用的 fixture，作为 Java 侧测试起点：

- `modules/pi-ai/src/test/resources/fixtures/ts/assistant-message-with-thinking-code.json`
- `modules/pi-session/src/test/resources/fixtures/ts/large-session.jsonl`

### 3. 阶段 1：`pi-ai` 核心类型首版

已在 `modules/pi-ai/src/main/java/dev/pi/ai/model/` 下补上第一批核心模型：

- `StreamOptions`
- `SimpleStreamOptions`
- `ThinkingLevel`
- `ThinkingBudgets`
- `CacheRetention`
- `Transport`
- `StopReason`
- `UserContent` / `AssistantContent`
- `TextContent`
- `ThinkingContent`
- `ImageContent`
- `ToolCall`
- `Usage`
- `Tool`
- `Message`
- `Context`
- `Model`
- `AssistantMessageEvent`

当前这批类型的实现特点：

- 使用 Java sealed interface / record 表达消息和事件层次。
- `api` / `provider` 当前用 `String` 保持开放性，避免过早锁死到 enum。
- 消息内容块和事件块保留了 TS 版的 `type` / `role` / `stopReason` 语义。
- `Tool.parametersSchema` 和 `ToolCall.arguments` 当前使用 Jackson `JsonNode`。

### 4. `pi-ai` 事件流骨架

已在 `modules/pi-ai/src/main/java/dev/pi/ai/stream/` 下新增：

- `Subscription`
- `EventStream`
- `AssistantMessageEventStream`

当前行为：

- 支持订阅事件。
- `done` / `error` 事件都会完成 `result()`。
- `error` 不会把 `result()` 变成异常，而是返回错误态的 `AssistantMessage`，与现有 TS 语义一致。

### 5. 阶段 1：`pi-ai` control plane 首版

已在 `modules/pi-ai/src/main/java/dev/pi/ai/` 下补上第一批主干组件：

- `provider/ApiProvider`
- `registry/ApiProviderRegistry`
- `registry/ModelRegistry`
- `auth/CredentialResolver`
- `auth/CredentialSource`
- `auth/EnvironmentCredentialSource`
- `PiAiClient`

当前这批主干的实现特点：

- `PiAiClient` 已提供 `stream()` / `complete()` / `streamSimple()` / `completeSimple()` facade。
- provider 分发按 `model.api` 进行，缺失 provider 时会明确报错。
- 凭证解析按 `model.provider` 进行，显式 `apiKey` 优先，环境变量 resolver 作为 fallback。
- `ApiProviderRegistry` 支持按 `sourceId` 卸载，便于后续插件/扩展场景接入。
- `ModelRegistry` 已支持按 provider 维度注册、查询和列举模型。

## 已完成的验证

已通过的命令：

```bash
.\gradlew.bat :pi-ai:test --no-daemon
.\gradlew.bat test --no-daemon
```

其中 `pi-ai` 新增测试覆盖了：

- `SimpleStreamOptions` builder 行为
- `AssistantMessage` 的 JSON round-trip
- `AssistantMessageEventStream` 对 `done` / `error` 的终结语义
- `ApiProviderRegistry` 的注册 / 查询 / source 卸载
- `ModelRegistry` 的注册 / 查询
- `CredentialResolver` 的 source 顺序、环境变量映射、显式 `apiKey` 优先级
- `PiAiClient` 的 facade 分发与凭证注入

对应测试文件：

- `modules/pi-ai/src/test/java/dev/pi/ai/model/SimpleStreamOptionsTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/model/AssistantMessageJsonTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/stream/AssistantMessageEventStreamTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/registry/ApiProviderRegistryTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/registry/ModelRegistryTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/auth/CredentialResolverTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/PiAiClientTest.java`

## 未完成 / 已知缺口

### `pi-ai`

以下内容还没开始或只完成了骨架：

- SSE / WebSocket 适配
- provider 实现
- `AssistantMessage` partial assembler
- message transform / validation / compat 层

### 其他模块

以下模块目前还只是最小工程骨架，尚未开始功能实现：

- `pi-agent-runtime`
- `pi-session`
- `pi-tools`
- `pi-extension-spi`
- `pi-tui`
- `pi-cli`
- `pi-sdk`

## 当前环境状态

当前仓库检查已经可以通过，但前置条件需要写清楚。

已验证通过：

```bash
npm.cmd ci --ignore-scripts
npx.cmd tsgo -p tsconfig.build.json   # packages/ai
npx.cmd tsgo -p tsconfig.build.json   # packages/agent
npx.cmd tsgo -p tsconfig.build.json   # packages/web-ui
npm.cmd run check
```

说明：

- `npm.cmd ci` 直接执行会在当前 Windows 环境卡在 `canvas` 原生编译。
- 使用 `npm.cmd ci --ignore-scripts` 可以先把静态检查需要的 Node 依赖装齐。
- 由于根 `check` 会进入 `packages/web-ui` 及其 example，需要先为 `packages/ai`、`packages/agent`、`packages/web-ui` 产出声明文件。
- 在当前环境里，上述前置步骤完成后，`npm.cmd run check` 已通过。

## 建议下一步

建议严格按 `docs/tasks.md` 的顺序继续：

1. 先补 `pi-ai` 的 registry 与 facade，把当前类型接成真正可调用的主干。
2. 再补 `simple options` 映射、message transform、validation，先把“请求前整理”和“结果后归一化”边界做出来。
3. 然后开始 `pi-session` 或 `pi-agent-runtime`，不要同时大面积推进多个模块。

更具体的下一步切片建议：

1. `pi-ai`：最小 `AssistantMessage` partial assembler。
2. `pi-ai`：通用 `SSE parser` 与 `WebSocket adapter`。
3. `pi-ai`：第一个 `openai-responses` provider。
4. `pi-session`：先做 JSONL 读取和 replay，不要先做写入。

并行拆分文档入口：

- `docs/subtasks/README.md`

## 交接注意事项

- `pi-java` 当前进度已提交并推送到 GitHub。
- `pi-java/README.md` 已链接 `handoff.md` 和 `docs/subtasks/README.md`。
- `pi-java` 下的 `.gradle/` 和 `build/` 已通过本地 `.gitignore` 忽略。
