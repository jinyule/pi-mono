# 子任务 05：pi-tools

## 目标

把内置工具和工具侧支撑能力独立做完，让 runtime 只关心工具契约，不关心具体实现。

## 范围

- truncation
- diff
- shell executor
- path policy
- image resize
- `read`
- `write`
- `edit`
- `bash`
- `grep`
- `find`
- `ls`

## 依赖

- 依赖 [04-pi-agent-runtime.md](04-pi-agent-runtime.md) 中 `AgentTool` / tool result contract 稳定

## 可并行关系

- 可与 [06-pi-tui.md](06-pi-tui.md) 并行
- 可与 [07-pi-extension-spi.md](07-pi-extension-spi.md) 并行
- 与 [03-pi-session-and-settings.md](03-pi-session-and-settings.md) 没有强耦合

## 完成标准

- 所有内置工具有 golden tests
- 输出格式与现有 TS 版对齐
- timeout / abort / truncation / diff 都能被 fixture 固化

