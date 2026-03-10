package dev.pi.extension.spi;

import java.util.List;

public record ResourcesDiscoverResult(
    List<String> skillPaths,
    List<String> promptPaths,
    List<String> themePaths
) {
    public ResourcesDiscoverResult {
        skillPaths = skillPaths == null ? List.of() : List.copyOf(skillPaths);
        promptPaths = promptPaths == null ? List.of() : List.copyOf(promptPaths);
        themePaths = themePaths == null ? List.of() : List.copyOf(themePaths);
    }
}
