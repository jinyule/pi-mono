package dev.pi.session;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class PackageSourceDiscovery {
    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("^[A-Za-z]:[\\\\/].*");

    private final Path cwd;
    private final Path agentDir;
    private final Path projectBaseDir;

    public PackageSourceDiscovery(Path cwd) {
        this(cwd, Path.of(System.getProperty("user.home"), ".pi", "agent"));
    }

    public PackageSourceDiscovery(Path cwd, Path agentDir) {
        this.cwd = Objects.requireNonNull(cwd, "cwd").toAbsolutePath().normalize();
        this.agentDir = Objects.requireNonNull(agentDir, "agentDir").toAbsolutePath().normalize();
        this.projectBaseDir = this.cwd.resolve(".pi").normalize();
    }

    public List<Path> resolve(
        List<PackageSource> globalPackageSources,
        List<PackageSource> projectPackageSources,
        ResourceType resourceType
    ) {
        Objects.requireNonNull(globalPackageSources, "globalPackageSources");
        Objects.requireNonNull(projectPackageSources, "projectPackageSources");
        Objects.requireNonNull(resourceType, "resourceType");

        Set<Path> resolvedPaths = new LinkedHashSet<>();
        resolveScoped(agentDir, globalPackageSources, resourceType, resolvedPaths);
        resolveScoped(projectBaseDir, projectPackageSources, resourceType, resolvedPaths);
        return List.copyOf(resolvedPaths);
    }

    private void resolveScoped(
        Path baseDir,
        List<PackageSource> packageSources,
        ResourceType resourceType,
        Set<Path> resolvedPaths
    ) {
        for (var packageSource : packageSources) {
            if (packageSource == null || !isLocalSource(packageSource.source())) {
                continue;
            }
            resolveLocalPackageSource(baseDir, packageSource, resourceType, resolvedPaths);
        }
    }

    private void resolveLocalPackageSource(
        Path baseDir,
        PackageSource packageSource,
        ResourceType resourceType,
        Set<Path> resolvedPaths
    ) {
        var packageRoot = resolveLocalPath(packageSource.source(), baseDir);
        if (!Files.exists(packageRoot)) {
            return;
        }

        if (Files.isRegularFile(packageRoot)) {
            if (resourceType == ResourceType.EXTENSIONS) {
                resolvedPaths.add(packageRoot);
            }
            return;
        }

        if (!Files.isDirectory(packageRoot)) {
            return;
        }

        var configuredEntries = resourceType.entries(packageSource);
        if (!configuredEntries.isEmpty()) {
            for (var entry : configuredEntries) {
                addIfExists(packageRoot.resolve(entry).normalize(), resolvedPaths);
            }
            return;
        }

        var conventionPath = packageRoot.resolve(resourceType.directoryName()).normalize();
        if (Files.exists(conventionPath)) {
            resolvedPaths.add(conventionPath.toAbsolutePath().normalize());
            return;
        }

        if (resourceType == ResourceType.EXTENSIONS) {
            resolvedPaths.add(packageRoot);
        }
    }

    private static void addIfExists(Path path, Set<Path> resolvedPaths) {
        if (Files.exists(path)) {
            resolvedPaths.add(path.toAbsolutePath().normalize());
        }
    }

    private static boolean isLocalSource(String source) {
        var trimmed = source == null ? "" : source.trim();
        if (trimmed.isBlank()) {
            return false;
        }
        if (trimmed.startsWith("npm:")) {
            return false;
        }
        if (trimmed.startsWith(".") || trimmed.startsWith("/") || trimmed.equals("~") || trimmed.startsWith("~/") || trimmed.startsWith("~\\")) {
            return true;
        }
        if (trimmed.startsWith("\\\\")) {
            return true;
        }
        if (WINDOWS_ABSOLUTE_PATH.matcher(trimmed).matches()) {
            return true;
        }
        return !trimmed.startsWith("git:")
            && !trimmed.startsWith("http://")
            && !trimmed.startsWith("https://")
            && !trimmed.startsWith("ssh://")
            && !trimmed.startsWith("git@");
    }

    private static Path resolveLocalPath(String source, Path baseDir) {
        var trimmed = source.trim();
        if (trimmed.equals("~")) {
            return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        }
        if (trimmed.startsWith("~/") || trimmed.startsWith("~\\")) {
            return Path.of(System.getProperty("user.home"))
                .resolve(trimmed.substring(2))
                .toAbsolutePath()
                .normalize();
        }

        var rawPath = Path.of(trimmed);
        return (rawPath.isAbsolute() ? rawPath : baseDir.resolve(rawPath))
            .toAbsolutePath()
            .normalize();
    }

    public enum ResourceType {
        EXTENSIONS("extensions") {
            @Override
            List<String> entries(PackageSource packageSource) {
                return packageSource.extensions();
            }
        },
        SKILLS("skills") {
            @Override
            List<String> entries(PackageSource packageSource) {
                return packageSource.skills();
            }
        },
        PROMPTS("prompts") {
            @Override
            List<String> entries(PackageSource packageSource) {
                return packageSource.prompts();
            }
        },
        THEMES("themes") {
            @Override
            List<String> entries(PackageSource packageSource) {
                return packageSource.themes();
            }
        };

        private final String directoryName;

        ResourceType(String directoryName) {
            this.directoryName = directoryName;
        }

        String directoryName() {
            return directoryName;
        }

        abstract List<String> entries(PackageSource packageSource);
    }
}
