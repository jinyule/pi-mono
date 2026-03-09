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

对应测试文件：

- `modules/pi-ai/src/test/java/dev/pi/ai/model/SimpleStreamOptionsTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/model/AssistantMessageJsonTest.java`
- `modules/pi-ai/src/test/java/dev/pi/ai/stream/AssistantMessageEventStreamTest.java`

## 未完成 / 已知缺口

### `pi-ai`

以下内容还没开始或只完成了骨架：

- API provider registry
- model registry
- `stream()` / `complete()` / `streamSimple()` / `completeSimple()` facade
- credential resolution
- SSE / WebSocket 适配
- provider 实现
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

## 环境阻塞项

按仓库要求尝试过根目录检查：

```bash
npm.cmd run check
```

当前无法真正执行到检查阶段，原因不是这次 Java 改动，而是工作区缺少 `node_modules`，导致脚本第一步就失败：

- `biome is not recognized as an internal or external command`

结论：

- `pi-java` 自己的 Gradle 侧验证是可用的。
- monorepo 根级 `npm run check` 需要先安装 Node 依赖后才能继续判断。

## 建议下一步

建议严格按 `docs/tasks.md` 的顺序继续：

1. 先补 `pi-ai` 的 registry 与 facade，把当前类型接成真正可调用的主干。
2. 再补 `simple options` 映射、message transform、validation，先把“请求前整理”和“结果后归一化”边界做出来。
3. 然后开始 `pi-session` 或 `pi-agent-runtime`，不要同时大面积推进多个模块。

更具体的下一步切片建议：

1. `pi-ai`：`ApiProvider` 接口、provider registry、`stream/complete` facade。
2. `pi-ai`：最小 `AssistantMessage` partial assembler。
3. `pi-session`：先做 JSONL 读取和 replay，不要先做写入。

## 交接注意事项

- 当前所有 `pi-java` 改动都还在工作区，尚未提交。
- `pi-java/README.md` 已补充当前阶段说明，但没有把交接文档入口再反向链接进去。
- `pi-java` 下的 `.gradle/` 和 `build/` 已通过本地 `.gitignore` 忽略。
