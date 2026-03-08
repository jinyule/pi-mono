# pi-java

这个目录存放 Java 版本 Pi 的设计草案，不是对现有 TypeScript monorepo 的逐文件翻译。

设计目标有三条：

1. 保留 Pi 现有的核心语义：统一 LLM provider 抽象、事件驱动的 agent runtime、树状会话、TUI-first 交互、可扩展的工具与资源系统。
2. 在 Java 生态中用更自然的方式实现这些能力：`Java 21`、`records / sealed interfaces`、`CompletableFuture`、`virtual threads`、`ServiceLoader + isolated ClassLoader`。
3. 尽量保持数据层兼容：`Message`、`AssistantMessageEvent`、`Session JSONL`、`settings.json`、`skills / prompts / themes` 的文件语义尽可能与现有 Pi 对齐。
4. 使用 `TDD` 作为默认开发流程：先写失败测试或 golden fixture，再做最小实现，最后重构。

文档入口：

- [docs/spec.md](docs/spec.md)：Java 版 Pi 的产品规格与兼容目标
- [docs/design.md](docs/design.md)：模块划分、核心接口、数据流、关键技术决策
- [docs/tasks.md](docs/tasks.md)：分阶段实施任务清单
- [docs/tdd.md](docs/tdd.md)：TDD 落地模板、fixture/golden 策略、模块级测试清单

结论先行：

- Java 版不应做成单体 CLI，而应做成多模块架构：`pi-ai`、`pi-agent-runtime`、`pi-session`、`pi-tools`、`pi-extension-spi`、`pi-tui`、`pi-cli`、`pi-sdk`。
- 最重要的兼容层不是 UI 像不像，而是三类语义必须一致：`流式事件`、`会话树`、`工具调用/结果`。
- TypeScript Extensions 不建议直接复用；Java 版应提供 Java-native plugin SPI，并继续保留 file-based 的 `Skills`、`Prompt Templates`、`Themes`。
