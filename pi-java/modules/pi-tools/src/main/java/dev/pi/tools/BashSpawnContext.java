package dev.pi.tools;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public record BashSpawnContext(
    String command,
    Path cwd,
    Map<String, String> environment
) {
    public BashSpawnContext {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(cwd, "cwd");
        environment = environment == null ? Map.of() : Map.copyOf(environment);
    }
}
