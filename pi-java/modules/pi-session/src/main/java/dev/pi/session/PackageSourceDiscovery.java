package dev.pi.session;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class PackageSourceDiscovery {
    private final PackageSourceManager packageSourceManager;
    private final boolean installMissing;

    public PackageSourceDiscovery(Path cwd) {
        this(new PackageSourceManager(cwd, SettingsManager.inMemory()), false);
    }

    public PackageSourceDiscovery(Path cwd, Path agentDir) {
        this(new PackageSourceManager(cwd, agentDir, SettingsManager.inMemory(), null), false);
    }

    PackageSourceDiscovery(Path cwd, Path agentDir, Path globalNpmRoot) {
        this(new PackageSourceManager(cwd, agentDir, SettingsManager.inMemory(), globalNpmRoot), false);
    }

    public PackageSourceDiscovery(Path cwd, SettingsManager settingsManager, boolean installMissing) {
        this(new PackageSourceManager(cwd, settingsManager), installMissing);
    }

    PackageSourceDiscovery(PackageSourceManager packageSourceManager, boolean installMissing) {
        this.packageSourceManager = Objects.requireNonNull(packageSourceManager, "packageSourceManager");
        this.installMissing = installMissing;
    }

    public List<Path> resolve(
        List<PackageSource> globalPackageSources,
        List<PackageSource> projectPackageSources,
        ResourceType resourceType
    ) {
        Objects.requireNonNull(globalPackageSources, "globalPackageSources");
        Objects.requireNonNull(projectPackageSources, "projectPackageSources");
        Objects.requireNonNull(resourceType, "resourceType");

        var resolvedPaths = new LinkedHashSet<Path>();
        for (var entry : dedupe(projectPackageSources, globalPackageSources)) {
            var packageRoot = packageSourceManager.resolvePackageRoot(entry.pkg().source(), entry.scope(), installMissing);
            if (packageRoot == null) {
                continue;
            }
            resolvePackageRoot(packageRoot, entry.pkg(), resourceType, resolvedPaths);
        }
        return List.copyOf(resolvedPaths);
    }

    private List<ScopedPackageSource> dedupe(
        List<PackageSource> projectPackageSources,
        List<PackageSource> globalPackageSources
    ) {
        var deduped = new LinkedHashMap<String, ScopedPackageSource>();
        addPackages(deduped, projectPackageSources, PackageSourceManager.Scope.PROJECT);
        addPackages(deduped, globalPackageSources, PackageSourceManager.Scope.GLOBAL);
        return List.copyOf(deduped.values());
    }

    private void addPackages(
        LinkedHashMap<String, ScopedPackageSource> deduped,
        List<PackageSource> packageSources,
        PackageSourceManager.Scope scope
    ) {
        for (var packageSource : packageSources) {
            if (packageSource == null) {
                continue;
            }
            deduped.putIfAbsent(packageSourceManager.sourceIdentity(packageSource, scope), new ScopedPackageSource(packageSource, scope));
        }
    }

    private void resolvePackageRoot(
        Path packageRoot,
        PackageSource packageSource,
        ResourceType resourceType,
        LinkedHashSet<Path> resolvedPaths
    ) {
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

    private static void addIfExists(Path path, LinkedHashSet<Path> resolvedPaths) {
        if (Files.exists(path)) {
            resolvedPaths.add(path.toAbsolutePath().normalize());
        }
    }

    private record ScopedPackageSource(
        PackageSource pkg,
        PackageSourceManager.Scope scope
    ) {
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
