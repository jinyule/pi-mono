# Minimal Extension Example

这个目录提供一个最小可用的 Java 插件示例，目标是作为 `pi-extension-spi` 的真实参考实现。

当前示例覆盖：

- `PiExtension` 入口
- `SessionStartEvent` 订阅
- command / shortcut / flag 注册
- `META-INF/services` service entry

核心源码：

- `src/main/java/dev/pi/examples/MinimalExtension.java`
- `src/main/resources/META-INF/services/dev.pi.extension.spi.PiExtension`

当前仓库里的集成验证会把这个示例源码编译成 jar，并验证：

- loader 可以成功发现并加载示例插件
- 示例插件注册项会进入 `LoadedExtension`
- 修改示例源码后，`ExtensionRuntime.reload()` 会加载新版本并关闭旧 classloader
