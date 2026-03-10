package dev.pi.extension.spi;

import java.nio.file.Path;
import java.util.Objects;

public record ExtensionLoadFailure(
    Path source,
    String message,
    Throwable cause
) {
    public ExtensionLoadFailure {
        source = Objects.requireNonNull(source, "source");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must be a non-empty string");
        }
    }
}
