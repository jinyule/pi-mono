package dev.pi.tools;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ShellConfig(
    Path shell,
    List<String> args
) {
    public ShellConfig {
        Objects.requireNonNull(shell, "shell");
        args = List.copyOf(Objects.requireNonNull(args, "args"));
    }
}
