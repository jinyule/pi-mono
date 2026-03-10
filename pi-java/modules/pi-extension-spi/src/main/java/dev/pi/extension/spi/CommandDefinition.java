package dev.pi.extension.spi;

import java.util.Objects;

public record CommandDefinition(
    String name,
    String description,
    CommandHandler handler
) {
    public CommandDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must be a non-empty string");
        }
        handler = Objects.requireNonNull(handler, "handler");
    }
}
