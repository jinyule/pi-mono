package dev.pi.extension.spi;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record ExtensionLoadResult(
    List<LoadedExtension> extensions,
    List<ExtensionLoadFailure> failures
) implements AutoCloseable {
    public ExtensionLoadResult {
        extensions = List.copyOf(Objects.requireNonNull(extensions, "extensions"));
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (var classLoader : new LinkedHashSet<>(extensions.stream().map(LoadedExtension::classLoader).toList())) {
            try {
                classLoader.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
