# 子任务 09：行为追平与分发

## 目标

在主功能可用后，收口 parity、插件生态和交付形式。

## 范围

- keybinding 语义追平
- selector / footer / changelog / share / HTML export
- auto-compaction / retry / branch summary
- 插件打包规范
- `skills / prompts / themes` 搜索路径
- `maven:` / `file:` / `git:` package source
- fat JAR
- `jpackage`

## 依赖

- 依赖 [08-pi-cli-and-sdk.md](08-pi-cli-and-sdk.md)

## 可并行关系

- 可以再拆成 parity 与 distribution 两条线
- 但不建议在主功能未稳定前提前做

## 完成标准

- 行为差异有清单且可验证
- 分发产物与安装路径明确
- 至少有一条可重复的发布路径

