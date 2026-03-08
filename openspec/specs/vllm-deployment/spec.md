# vLLM 部署管理规范

> 包名: `@mariozechner/pi`

## 概述

`pi` 简化了在远程 GPU pods 上运行大型语言模型。它自动：
- 在全新 Ubuntu pods 上设置 vLLM
- 为 agentic 模型配置工具调用（Qwen, GPT-OSS, GLM 等）
- 在同一 pod 上管理多个模型，"智能" GPU 分配
- 为每个模型提供 OpenAI 兼容的 API 端点
- 包含带文件系统工具的交互式 agent 用于测试和代码分析

## 支持的提供商

### 主要支持

| 提供商 | 特点 |
|--------|------|
| **DataCrunch** | 最佳共享模型存储，NFS volumes 可跨同区域多个 pods 共享 |
| **RunPod** | 良好持久存储，网络卷独立持久 |

### 也支持

- Vast.ai (卷锁定特定机器)
- Prime Intellect (无持久存储)
- AWS EC2 (需 EFS 设置)
- 任何有 NVIDIA GPUs、CUDA driver、SSH 的 Ubuntu 机器

## 命令参考

### Pod 管理

```bash
pi pods setup <name> "<ssh>" [options]
  --mount "<mount_command>"     # 设置时运行挂载命令
  --models-path <path>          # 覆盖模型路径
  --vllm release|nightly|gpt-oss # vLLM 版本

pi pods                         # 列出所有配置的 pods
pi pods active <name>           # 切换活跃 pod
pi pods remove <name>           # 从本地配置移除 pod
pi shell [<name>]               # SSH 进入 pod
pi ssh [<name>] "<command>"     # 在 pod 上运行命令
```

### vLLM 版本选项

| 选项 | 说明 |
|------|------|
| `release` (默认) | 稳定 vLLM 发布，推荐大多数用户 |
| `nightly` | 最新 vLLM 功能，最新模型如 GLM-4.5 需要 |
| `gpt-oss` | 仅用于 OpenAI GPT-OSS 模型的特殊构建 |

### 模型管理

```bash
pi start <model> --name <name> [options]
  --memory <percent>    # GPU 内存: 30%, 50%, 90% (默认: 90%)
  --context <size>      # 上下文窗口: 4k, 8k, 16k, 32k, 64k, 128k
  --gpus <count>        # GPU 数量（仅预定义模型）
  --pod <name>          # 目标特定 pod
  --vllm <args...>      # 直接传递参数给 vLLM

pi stop [<name>]        # 停止模型（无名称则停止所有）
pi list                 # 列出运行中的模型
pi logs <name>          # 流式模型日志
```

### Agent & Chat

```bash
pi agent <name> "<message>"           # 单条消息
pi agent <name> "<msg1>" "<msg2>"     # 多条消息
pi agent <name> -i                    # 交互聊天模式
pi agent <name> -i -c                 # 继续上次会话

# 独立 OpenAI 兼容 agent
pi-agent --base-url http://localhost:8000/v1 --model llama-3.1 "Hello"
pi-agent --api-key sk-... "What is 2+2?"
pi-agent --json "What is 2+2?"        # JSONL 输出
pi-agent -i                           # 交互模式
```

## 预定义模型配置

### Qwen 系列

```bash
# Qwen2.5-Coder-32B - 优秀编码模型，单卡 H100/H200
pi start Qwen/Qwen2.5-Coder-32B-Instruct --name qwen

# Qwen3-Coder-30B - 高级推理和工具使用
pi start Qwen/Qwen3-Coder-30B-A3B-Instruct --name qwen3

# Qwen3-Coder-480B - SOTA，8xH200 (数据并行模式)
pi start Qwen/Qwen3-Coder-480B-A35B-Instruct-FP8 --name qwen-480b
```

### GPT-OSS 系列

```bash
# 需要特殊 vLLM 构建
pi pods setup gpt-pod "ssh root@1.2.3.4" --models-path /workspace --vllm gpt-oss

# GPT-OSS-20B - 16GB+ VRAM
pi start openai/gpt-oss-20b --name gpt20

# GPT-OSS-120B - 60GB+ VRAM
pi start openai/gpt-oss-120b --name gpt120
```

### GLM 系列

```bash
# GLM-4.5 - 需要 8-16 GPUs，包含思考模式
pi start zai-org/GLM-4.5 --name glm

# GLM-4.5-Air - 较小版本，1-2 GPUs
pi start zai-org/GLM-4.5-Air --name glm-air
```

### 自定义模型

```bash
# DeepSeek 自定义设置
pi start deepseek-ai/DeepSeek-V3 --name deepseek --vllm \
  --tensor-parallel-size 4 --trust-remote-code

# 任意模型指定 tool parser
pi start some/model --name mymodel --vllm \
  --tool-call-parser hermes --enable-auto-tool-choice
```

## 工具调用支持

自动为已知模型配置适当的工具调用解析器：

| 模型 | 解析器 |
|------|--------|
| Qwen 系列 | `hermes` (Qwen3-Coder: `qwen3_coder`) |
| GLM 系列 | `glm4_moe` 支持推理 |
| GPT-OSS | 使用 `/v1/responses` 端点 |
| 自定义 | 指定 `--tool-call-parser` |

禁用工具调用：
```bash
pi start model --name mymodel --vllm --disable-tool-call-parser
```

## 内存和上下文管理

### GPU 内存分配

| 设置 | 说明 |
|------|------|
| `--memory 30%` | 高并发，有限上下文 |
| `--memory 50%` | 平衡 |
| `--memory 90%` | 最大上下文，低并发 |

### 上下文窗口

| 设置 | 总 tokens |
|------|-----------|
| `--context 4k` | 4,096 |
| `--context 32k` | 32,768 |
| `--context 128k` | 131,072 |

## 多 GPU 支持

### 自动 GPU 分配

```bash
pi start model1 --name m1  # 自动分配 GPU 0
pi start model2 --name m2  # 自动分配 GPU 1
pi start model3 --name m3  # 自动分配 GPU 2
```

### 指定 GPU 数量

```bash
# Qwen 使用 1 GPU 而非所有可用
pi start Qwen/Qwen2.5-Coder-32B-Instruct --name qwen --gpus 1
```

### Tensor Parallelism

```bash
# 使用所有可用 GPUs
pi start meta-llama/Llama-3.1-70B-Instruct --name llama70b --vllm \
  --tensor-parallel-size 4
```

## API 集成

所有模型暴露 OpenAI 兼容端点：

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://your-pod-ip:8001/v1",
    api_key="your-pi-api-key"
)

response = client.chat.completions.create(
    model="Qwen/Qwen2.5-Coder-32B-Instruct",
    messages=[{"role": "user", "content": "Hello"}],
    tools=[...]  # 工具调用支持
)
```

## 环境变量

| 变量 | 说明 |
|------|------|
| `HF_TOKEN` | HuggingFace token 用于模型下载 |
| `PI_API_KEY` | vLLM 端点的 API key |
| `PI_CONFIG_DIR` | 配置目录 (默认: `~/.pi`) |
| `OPENAI_API_KEY` | `pi-agent` 无 `--api-key` 时使用 |

## 设置指南

### DataCrunch

最佳体验，共享 NFS 存储：

```bash
# 1. 创建 SFS (Dashboard → Storage → Create SFS)
# 2. 创建同数据中心 GPU 实例
# 3. 设置
pi pods setup dc1 "ssh root@instance.datacrunch.io" \
  --mount "sudo mount -t nfs -o nconnect=16 nfs.fin-02.datacrunch.io:/your-pseudo /mnt/hf-models"
```

**优势**:
- 模型在实例重启后持久
- 同数据中心多实例共享模型
- 下载一次，到处使用

### RunPod

良好持久存储：

```bash
# 有网络卷
pi pods setup runpod "ssh root@pod.runpod.io" --models-path /runpod-volume

# 或使用 workspace
pi pods setup runpod "ssh root@pod.runpod.io" --models-path /workspace
```

## 故障排除

### OOM 错误
- 降低 `--memory` 百分比
- 使用更小模型或量化版本
- 降低 `--context`

### 模型无法启动
```bash
pi ssh "nvidia-smi"   # 检查 GPU
pi list               # 检查端口使用
pi stop               # 强制停止所有
```

### 工具调用问题
- 并非所有模型可靠支持工具调用
- 尝试不同解析器: `--vllm --tool-call-parser mistral`
- 或禁用: `--vllm --disable-tool-call-parser`
