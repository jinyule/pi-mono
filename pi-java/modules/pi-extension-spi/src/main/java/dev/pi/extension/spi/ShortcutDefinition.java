package dev.pi.extension.spi;

import java.util.Objects;

public record ShortcutDefinition(
    String keyId,
    String description,
    ShortcutHandler handler
) {
    public ShortcutDefinition {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId must be a non-empty string");
        }
        handler = Objects.requireNonNull(handler, "handler");
    }
}
