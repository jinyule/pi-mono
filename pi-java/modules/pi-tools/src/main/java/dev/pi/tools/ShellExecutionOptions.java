package dev.pi.tools;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public record ShellExecutionOptions(
    Path cwd,
    Duration timeout,
    Map<String, String> environment,
    Consumer<String> onChunk,
    BooleanSupplier cancelled,
    Integer maxBytes
) {
    public ShellExecutionOptions {
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        cancelled = cancelled == null ? () -> false : cancelled;
    }

    public static ShellExecutionOptions defaults() {
        return new ShellExecutionOptions(null, null, Map.of(), null, () -> false, null);
    }
}
