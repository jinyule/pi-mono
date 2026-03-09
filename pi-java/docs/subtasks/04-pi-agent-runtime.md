# 子任务 04：pi-agent-runtime

## 目标

实现 Java 版 agent loop、tool orchestration、steering/follow-up 与统一 runtime events。

## 范围

- `AgentState`
- `AgentEvent`
- `AgentLoopConfig`
- `Agent` facade
- `convertToLlm`
- `transformContext`
- streaming assistant response assembler
- 顺序工具执行
- steering / follow-up 队列

## 依赖

- 依赖 [01-pi-ai-control-plane.md](01-pi-ai-control-plane.md)
- 可以先用 fake provider / fake tool 开发

## 可并行关系

- 可与 [02-pi-ai-providers.md](02-pi-ai-providers.md) 并行
- 完成 tool / event contract 后可解锁 [05-pi-tools.md](05-pi-tools.md) 和 [07-pi-extension-spi.md](07-pi-extension-spi.md)
- 与 [03-pi-session-and-settings.md](03-pi-session-and-settings.md) 集成时只需要消费 session 接口，不要反向耦合

## 完成标准

- `agent_start -> turn_start -> message_update -> tool_execution_end -> turn_end -> agent_end` 生命周期可测
- 工具默认顺序执行
- `ToolResultMessage(isError=true)` 语义稳定

