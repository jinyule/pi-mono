package dev.pi.extension.spi;

import java.util.List;
import java.util.Objects;

public record ExtensionResourceDiscoveryResult(
    List<ExtensionResourcePath> skillPaths,
    List<ExtensionResourcePath> promptPaths,
    List<ExtensionResourcePath> themePaths,
    List<ExtensionEventFailure> failures
) {
    public ExtensionResourceDiscoveryResult {
        skillPaths = List.copyOf(Objects.requireNonNull(skillPaths, "skillPaths"));
        promptPaths = List.copyOf(Objects.requireNonNull(promptPaths, "promptPaths"));
        themePaths = List.copyOf(Objects.requireNonNull(themePaths, "themePaths"));
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
    }

    public boolean isEmpty() {
        return skillPaths.isEmpty() && promptPaths.isEmpty() && themePaths.isEmpty();
    }
}
