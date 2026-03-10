package dev.pi.extension.spi;

import java.util.List;
import java.util.Objects;

public record ExtensionRuntimeSnapshot(
    List<LoadedExtension> extensions,
    List<ExtensionLoadFailure> failures,
    ExtensionEventBus eventBus,
    ExtensionResourceDiscovery resourceDiscovery
) {
    public ExtensionRuntimeSnapshot {
        extensions = List.copyOf(Objects.requireNonNull(extensions, "extensions"));
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        eventBus = Objects.requireNonNull(eventBus, "eventBus");
        resourceDiscovery = Objects.requireNonNull(resourceDiscovery, "resourceDiscovery");
    }

    static ExtensionRuntimeSnapshot empty() {
        return new ExtensionRuntimeSnapshot(
            List.of(),
            List.of(),
            new ExtensionEventBus(List.of()),
            new ExtensionResourceDiscovery(List.of())
        );
    }
}
