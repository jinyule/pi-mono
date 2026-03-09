# 子任务 01：pi-ai Control Plane

## 目标

完成 `pi-ai` 的非 provider 专属主干，让后续 provider、runtime、CLI 都能基于统一入口开发。

## 范围

- `ApiProvider` 接口
- `ApiProviderRegistry`
- `ModelRegistry`
- `CredentialResolver`
- `stream()` / `complete()` / `streamSimple()` / `completeSimple()` facade
- 通用 `SSE parser`
- 通用 `WebSocket adapter`
- 最小 `AssistantMessage` partial assembler

## 依赖

- 依赖当前已完成的 `pi-ai` core model / event types
- 不依赖具体 provider 实现

## 可并行关系

- 可以与 [03-pi-session-and-settings.md](03-pi-session-and-settings.md) 并行
- 可以与 [06-pi-tui.md](06-pi-tui.md) 的 terminal / render harness 基础工作并行
- 完成后会解锁 [02-pi-ai-providers.md](02-pi-ai-providers.md) 和 [04-pi-agent-runtime.md](04-pi-agent-runtime.md)

## 交付物

- 稳定的 provider 注册与分发入口
- 可复用的 transport 层
- 可被 provider 测试直接复用的 assembler / parser fixture
- 对 `streamSimple` reasoning 映射的最小公共入口

## 完成标准

- 至少有 fake provider contract tests
- `done` / `error` / `text_*` / `toolcall_*` 流程可通过测试复放
- 下游模块不需要知道 provider 内部细节

