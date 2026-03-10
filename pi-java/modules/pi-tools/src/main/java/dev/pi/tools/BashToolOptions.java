package dev.pi.tools;

public record BashToolOptions(
    ShellExecutor executor,
    ShellConfig shellConfig,
    String commandPrefix,
    BashSpawnHook spawnHook
) {
    public BashToolOptions {
        executor = executor == null ? new DefaultShellExecutor() : executor;
        shellConfig = shellConfig == null ? Shells.resolveShellConfig() : shellConfig;
    }

    public static BashToolOptions defaults() {
        return new BashToolOptions(new DefaultShellExecutor(), Shells.resolveShellConfig(), null, null);
    }
}
