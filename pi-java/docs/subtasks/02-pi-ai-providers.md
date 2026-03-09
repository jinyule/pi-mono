# 子任务 02：pi-ai Providers

## 目标

在 `pi-ai` control plane 稳定后，并行落 provider 实现。

## 范围

- `openai-responses`
- `openai-completions`
- `anthropic-messages`
- `google-generative-ai`
- `bedrock-converse-stream`
- 相关 contract / golden / abort / reasoning / image input tests

## 依赖

- 依赖 [01-pi-ai-control-plane.md](01-pi-ai-control-plane.md)

## 可并行关系

- provider 之间可以继续拆成更小任务并行
- 可以与 [04-pi-agent-runtime.md](04-pi-agent-runtime.md) 并行，只要 runtime 使用 fake provider
- 可以与 [03-pi-session-and-settings.md](03-pi-session-and-settings.md) 并行

## 推荐再拆分

- OpenAI 线：`openai-responses` + `openai-completions`
- Anthropic 线：`anthropic-messages`
- Google / Bedrock 线：`google-generative-ai` + `bedrock-converse-stream`

## 完成标准

- 每个 provider 都有统一 contract suite
- 事件顺序、usage、tool calls、abort 行为可验证
- provider 不在 runtime / CLI 层泄漏专属协议细节

