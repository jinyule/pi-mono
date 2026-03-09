# 子任务 06：pi-tui

## 目标

独立完成 Java 版终端抽象、差分渲染和虚拟终端测试基座。

## 范围

- `Terminal`
- `Component`
- `Focusable`
- `Overlay`
- raw mode / resize / title / cursor
- bracketed paste
- kitty keyboard protocol
- diff renderer
- overlay stack
- IME cursor marker
- `VirtualTerminal`
- 基础组件第一批

## 依赖

- 依赖 runtime event shape 大致稳定
- 不依赖 provider 实现

## 可并行关系

- 可与 [01-pi-ai-control-plane.md](01-pi-ai-control-plane.md) 并行推进底层 terminal / render harness
- 可与 [05-pi-tools.md](05-pi-tools.md) 并行
- 最终集成会在 [08-pi-cli-and-sdk.md](08-pi-cli-and-sdk.md) 收口

## 完成标准

- `VirtualTerminal` golden tests 可跑
- IME / diff render / input parser 有可复放 fixture
- 不需要等 CLI 完成才验证核心渲染逻辑

