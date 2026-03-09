# 子任务 03：pi-session 与 Settings

## 目标

把会话持久化、回放、迁移和配置装配独立落地，作为 runtime 和 CLI 的稳定底座。

## 范围

- `SessionHeader` / `SessionEntry` / `SessionTreeNode` / `SessionContext`
- `Session JSONL` 读取、解析、回写
- `v1 -> v2 -> v3` migration
- `buildSessionContext()`
- `SettingsManager`
- `AGENTS.md` / `CLAUDE.md` / `SYSTEM.md` / `APPEND_SYSTEM.md` 装配

## 依赖

- 只依赖稳定的 `Message` / `AssistantMessageEvent` 结构
- 不依赖 provider 实现

## 可并行关系

- 可与 [01-pi-ai-control-plane.md](01-pi-ai-control-plane.md) 并行
- 可与 [02-pi-ai-providers.md](02-pi-ai-providers.md) 并行
- 会被 [04-pi-agent-runtime.md](04-pi-agent-runtime.md)、[07-pi-extension-spi.md](07-pi-extension-spi.md)、[08-pi-cli-and-sdk.md](08-pi-cli-and-sdk.md) 消费

## 完成标准

- 真实 TS JSONL fixture 可 replay
- `thinkingLevel` / `model` / `compaction` / `branchSummary` 语义通过测试
- settings merge 与 file lock 行为稳定

