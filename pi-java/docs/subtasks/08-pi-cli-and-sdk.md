# 子任务 08：pi-cli 与 pi-sdk

## 目标

把前面模块接成可运行的应用层和可嵌入的 SDK 层。

## 范围

- CLI 参数解析
- `interactive`
- `print`
- `json`
- `rpc`
- `PiAgentSession`
- `pi-sdk` facade
- `list-models`
- `resume`
- `new`
- `export`
- `copy`
- `tree`
- `fork`
- `compact`
- `reload`

## 依赖

- 依赖 [01-pi-ai-control-plane.md](01-pi-ai-control-plane.md)
- 依赖 [03-pi-session-and-settings.md](03-pi-session-and-settings.md)
- 依赖 [04-pi-agent-runtime.md](04-pi-agent-runtime.md)
- 依赖 [05-pi-tools.md](05-pi-tools.md)
- 依赖 [06-pi-tui.md](06-pi-tui.md)
- 依赖 [07-pi-extension-spi.md](07-pi-extension-spi.md)

## 可并行关系

- 适合在前置模块接口冻结后集中推进
- 不适合太早开工，否则会变成集成层追着底层改接口

## 完成标准

- `print/json/rpc` 可稳定跑集成测试
- `interactive` 可完成完整 coding loop
- SDK 可以创建内存 session 并触发 prompt

