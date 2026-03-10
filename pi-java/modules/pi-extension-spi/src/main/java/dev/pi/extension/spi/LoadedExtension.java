package dev.pi.extension.spi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public record LoadedExtension(
    String id,
    String version,
    Path source,
    PiExtension extension,
    ExtensionClassLoader classLoader,
    Map<String, ToolDefinition<?>> toolDefinitions,
    Map<String, CommandDefinition> commandDefinitions,
    Map<String, MessageRenderer<?, ?>> messageRenderers
) implements AutoCloseable {
    public LoadedExtension {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must be a non-empty string");
        }
        version = version == null || version.isBlank() ? "0.0.0" : version;
        source = Objects.requireNonNull(source, "source");
        extension = Objects.requireNonNull(extension, "extension");
        classLoader = Objects.requireNonNull(classLoader, "classLoader");
        toolDefinitions = Map.copyOf(Objects.requireNonNull(toolDefinitions, "toolDefinitions"));
        commandDefinitions = Map.copyOf(Objects.requireNonNull(commandDefinitions, "commandDefinitions"));
        messageRenderers = Map.copyOf(Objects.requireNonNull(messageRenderers, "messageRenderers"));
    }

    @Override
    public void close() throws IOException {
        classLoader.close();
    }
}
