# 子任务 07：pi-extension-spi

## 目标

把 Java-native plugin SPI、事件总线和 `/reload` 机制做成独立模块。

## 范围

- `ExtensionApi`
- `ExtensionContext`
- `ExtensionUiContext`
- `ToolDefinition`
- `CommandDefinition`
- `MessageRenderer`
- `ServiceLoader + isolated ClassLoader`
- runtime reload / classloader 回收
- 附加资源路径扩展点
- 最小示例插件

## 依赖

- 依赖 [04-pi-agent-runtime.md](04-pi-agent-runtime.md) 的 tool / command / event contract
- 依赖 [03-pi-session-and-settings.md](03-pi-session-and-settings.md) 的 session metadata / resources 装配接口

## 可并行关系

- 可与 [05-pi-tools.md](05-pi-tools.md) 并行
- 可与 [06-pi-tui.md](06-pi-tui.md) 并行
- 最终 UI mode-aware 集成在 [08-pi-cli-and-sdk.md](08-pi-cli-and-sdk.md)

## 完成标准

- 插件注册项可见
- `/reload` 后旧实例不再接收事件
- 至少一个示例插件通过集成测试

