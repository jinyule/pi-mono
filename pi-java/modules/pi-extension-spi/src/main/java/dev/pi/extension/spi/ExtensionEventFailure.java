package dev.pi.extension.spi;

import java.nio.file.Path;
import java.util.Objects;

public record ExtensionEventFailure(
    String extensionId,
    Path source,
    String eventType,
    Throwable cause
) {
    public ExtensionEventFailure {
        if (extensionId == null || extensionId.isBlank()) {
            throw new IllegalArgumentException("extensionId must be a non-empty string");
        }
        source = Objects.requireNonNull(source, "source");
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must be a non-empty string");
        }
        cause = Objects.requireNonNull(cause, "cause");
    }
}
