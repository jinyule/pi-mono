# pi-java 并行子任务拆分

更新时间：2026-03-09

## 结论

剩余步骤可以并行，但不能“全量同时开工”。

正确方式是按依赖边界拆成几个 workstream，并且在少数关键接口冻结后再放大并行度。最重要的串行边界有三个：

1. `pi-ai` 的 control plane 与 transport contract 需要先稳定。
2. `pi-agent-runtime` 的 tool / event contract 需要先稳定。
3. `pi-cli` / `pi-sdk` 基本属于集成层，适合最后收口。

## 推荐波次

### Wave 1

- [01-pi-ai-control-plane.md](01-pi-ai-control-plane.md)
- [03-pi-session-and-settings.md](03-pi-session-and-settings.md)

### Wave 2

- [02-pi-ai-providers.md](02-pi-ai-providers.md)
- [04-pi-agent-runtime.md](04-pi-agent-runtime.md)
- [06-pi-tui.md](06-pi-tui.md)

### Wave 3

- [05-pi-tools.md](05-pi-tools.md)
- [07-pi-extension-spi.md](07-pi-extension-spi.md)

### Wave 4

- [08-pi-cli-and-sdk.md](08-pi-cli-and-sdk.md)

### Wave 5

- [09-parity-and-distribution.md](09-parity-and-distribution.md)

## 分配规则

- 每个子任务文档都应该有明确文件边界和交付物。
- 任何会影响下游模块的接口，必须先在对应文档里冻结，再让下游开始实现。
- `TDD`、fixture、golden test 规则继续以 `docs/tdd.md` 为准。
- 如果两个子任务需要改同一组核心类型，优先把公共类型抽成前置子任务，而不是让两个任务并改。

