package dev.pi.extension.spi;

import java.nio.file.Path;
import java.util.Objects;

public record ExtensionResourcePath(
    String declaredPath,
    Path resolvedPath,
    String extensionId,
    Path extensionSource,
    Path baseDir
) {
    public ExtensionResourcePath {
        if (declaredPath == null || declaredPath.isBlank()) {
            throw new IllegalArgumentException("declaredPath must be a non-empty string");
        }
        resolvedPath = Objects.requireNonNull(resolvedPath, "resolvedPath").toAbsolutePath().normalize();
        if (extensionId == null || extensionId.isBlank()) {
            throw new IllegalArgumentException("extensionId must be a non-empty string");
        }
        extensionSource = Objects.requireNonNull(extensionSource, "extensionSource").toAbsolutePath().normalize();
        baseDir = Objects.requireNonNull(baseDir, "baseDir").toAbsolutePath().normalize();
    }
}
