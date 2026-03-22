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
    private Path globalNpmRoot;
    private boolean globalNpmRootResolved;

    public PackageSourceDiscovery(Path cwd) {
        this(cwd, Path.of(System.getProperty("user.home"), ".pi", "agent"));
    }

    public PackageSourceDiscovery(Path cwd, Path agentDir) {
        this(cwd, agentDir, null, false);
    }

    PackageSourceDiscovery(Path cwd, Path agentDir, Path globalNpmRoot) {
        this(cwd, agentDir, globalNpmRoot, true);
    }

    private PackageSourceDiscovery(Path cwd, Path agentDir, Path globalNpmRoot, boolean globalNpmRootResolved) {
        this.cwd = Objects.requireNonNull(cwd, "cwd").toAbsolutePath().normalize();
        this.agentDir = Objects.requireNonNull(agentDir, "agentDir").toAbsolutePath().normalize();
        this.projectBaseDir = this.cwd.resolve(".pi").normalize();
        this.globalNpmRoot = globalNpmRoot == null ? null : globalNpmRoot.toAbsolutePath().normalize();
        this.globalNpmRootResolved = globalNpmRootResolved;
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
        resolveScoped(SourceScope.GLOBAL, globalPackageSources, resourceType, resolvedPaths);
        resolveScoped(SourceScope.PROJECT, projectPackageSources, resourceType, resolvedPaths);
        return List.copyOf(resolvedPaths);
    }

    private void resolveScoped(
        SourceScope scope,
        List<PackageSource> packageSources,
        ResourceType resourceType,
        Set<Path> resolvedPaths
    ) {
        for (var packageSource : packageSources) {
            if (packageSource == null) {
                continue;
            }
            var source = parseSource(packageSource.source());
            if (source == null) {
                continue;
            }
            var packageRoot = resolvePackageRoot(scope, source);
            if (packageRoot == null) {
                continue;
            }
            resolvePackageRoot(packageRoot, packageSource, resourceType, resolvedPaths);
        }
    }

    private void resolvePackageRoot(
        Path packageRoot,
        PackageSource packageSource,
        ResourceType resourceType,
        Set<Path> resolvedPaths
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

    private Path resolvePackageRoot(SourceScope scope, ParsedSource source) {
        return switch (source) {
            case LocalSource localSource -> resolveLocalPath(localSource.path(), scope.baseDir(this.agentDir, this.projectBaseDir));
            case NpmSource npmSource -> resolveNpmInstallPath(scope, npmSource);
            case GitSource gitSource -> resolveGitInstallPath(scope, gitSource);
        };
    }

    private static void addIfExists(Path path, Set<Path> resolvedPaths) {
        if (Files.exists(path)) {
            resolvedPaths.add(path.toAbsolutePath().normalize());
        }
    }

    private static ParsedSource parseSource(String source) {
        var trimmed = source == null ? "" : source.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        if (trimmed.startsWith("npm:")) {
            return parseNpmSource(trimmed);
        }
        if (isLocalPathLike(trimmed)) {
            return new LocalSource(trimmed);
        }
        var gitSource = parseGitSource(trimmed);
        if (gitSource != null) {
            return gitSource;
        }
        return new LocalSource(trimmed);
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

    private Path resolveNpmInstallPath(SourceScope scope, NpmSource source) {
        if (scope == SourceScope.PROJECT) {
            return projectBaseDir.resolve("npm").resolve("node_modules").resolve(source.name()).toAbsolutePath().normalize();
        }
        var resolvedGlobalRoot = globalNpmRoot();
        if (resolvedGlobalRoot == null) {
            return null;
        }
        return resolvedGlobalRoot.resolve(source.name()).toAbsolutePath().normalize();
    }

    private Path resolveGitInstallPath(SourceScope scope, GitSource source) {
        var baseDir = scope == SourceScope.PROJECT ? projectBaseDir.resolve("git") : agentDir.resolve("git");
        return baseDir.resolve(source.host()).resolve(source.path()).toAbsolutePath().normalize();
    }

    private Path globalNpmRoot() {
        if (!globalNpmRootResolved) {
            globalNpmRoot = detectGlobalNpmRoot();
            globalNpmRootResolved = true;
        }
        return globalNpmRoot;
    }

    private static Path detectGlobalNpmRoot() {
        try {
            var command = isWindows() ? List.of("npm.cmd", "root", "-g") : List.of("npm", "root", "-g");
            var process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
            var output = new String(process.getInputStream().readAllBytes()).trim();
            if (process.waitFor() != 0 || output.isBlank()) {
                return null;
            }
            return Path.of(output).toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean isLocalPathLike(String trimmed) {
        return trimmed.startsWith(".")
            || trimmed.startsWith("/")
            || trimmed.equals("~")
            || trimmed.startsWith("~/")
            || trimmed.startsWith("~\\")
            || trimmed.startsWith("\\\\")
            || WINDOWS_ABSOLUTE_PATH.matcher(trimmed).matches();
    }

    private static NpmSource parseNpmSource(String source) {
        var spec = source.substring("npm:".length()).trim();
        var match = Pattern.compile("^(@?[^@]+(?:/[^@]+)?)(?:@(.+))?$").matcher(spec);
        if (!match.matches()) {
            return new NpmSource(spec);
        }
        var name = match.group(1);
        return new NpmSource(name == null ? spec : name);
    }

    private static GitSource parseGitSource(String source) {
        var trimmed = source.trim();
        var hasGitPrefix = trimmed.startsWith("git:");
        var raw = hasGitPrefix ? trimmed.substring(4).trim() : trimmed;
        if (!hasGitPrefix && !hasExplicitGitProtocol(raw)) {
            return null;
        }

        var split = splitGitRef(raw);
        var repo = split.repo();
        String host;
        String path;
        if (repo.startsWith("git@")) {
            var match = Pattern.compile("^git@([^:]+):(.+)$").matcher(repo);
            if (!match.matches()) {
                return null;
            }
            host = match.group(1);
            path = match.group(2);
        } else if (hasExplicitGitProtocol(repo)) {
            try {
                var uri = java.net.URI.create(repo);
                host = uri.getHost();
                path = uri.getPath();
            } catch (IllegalArgumentException exception) {
                return null;
            }
        } else {
            var slashIndex = repo.indexOf('/');
            if (slashIndex < 0) {
                return null;
            }
            host = repo.substring(0, slashIndex);
            path = repo.substring(slashIndex + 1);
            if (!host.contains(".") && !"localhost".equals(host)) {
                return null;
            }
        }

        if (host == null || host.isBlank()) {
            return null;
        }
        var normalizedPath = path == null ? "" : path.replace('\\', '/').replaceAll("^/+", "").replaceAll("\\.git$", "");
        var segments = normalizedPath.split("/");
        if (segments.length < 2) {
            return null;
        }
        return new GitSource(host, normalizedPath);
    }

    private static boolean hasExplicitGitProtocol(String source) {
        return source.startsWith("http://")
            || source.startsWith("https://")
            || source.startsWith("ssh://")
            || source.startsWith("git://");
    }

    private static GitSplit splitGitRef(String source) {
        if (source.startsWith("git@")) {
            var match = Pattern.compile("^git@([^:]+):(.+)$").matcher(source);
            if (!match.matches()) {
                return new GitSplit(source, null);
            }
            var pathWithRef = match.group(2);
            var refSeparator = pathWithRef.indexOf('@');
            if (refSeparator < 0) {
                return new GitSplit(source, null);
            }
            var repoPath = pathWithRef.substring(0, refSeparator);
            var ref = pathWithRef.substring(refSeparator + 1);
            if (repoPath.isBlank() || ref.isBlank()) {
                return new GitSplit(source, null);
            }
            return new GitSplit("git@" + match.group(1) + ":" + repoPath, ref);
        }

        if (source.contains("://")) {
            try {
                var uri = java.net.URI.create(source);
                var pathWithRef = uri.getPath();
                var refSeparator = pathWithRef == null ? -1 : pathWithRef.indexOf('@');
                if (refSeparator < 0) {
                    return new GitSplit(source, null);
                }
                var repoPath = pathWithRef.substring(0, refSeparator);
                var ref = pathWithRef.substring(refSeparator + 1);
                if (repoPath.isBlank() || ref.isBlank()) {
                    return new GitSplit(source, null);
                }
                return new GitSplit(
                    new java.net.URI(uri.getScheme(), uri.getAuthority(), repoPath, uri.getQuery(), uri.getFragment()).toString(),
                    ref
                );
            } catch (Exception ignored) {
                return new GitSplit(source, null);
            }
        }

        var slashIndex = source.indexOf('/');
        if (slashIndex < 0) {
            return new GitSplit(source, null);
        }
        var pathWithRef = source.substring(slashIndex + 1);
        var refSeparator = pathWithRef.indexOf('@');
        if (refSeparator < 0) {
            return new GitSplit(source, null);
        }
        var repoPath = pathWithRef.substring(0, refSeparator);
        var ref = pathWithRef.substring(refSeparator + 1);
        if (repoPath.isBlank() || ref.isBlank()) {
            return new GitSplit(source, null);
        }
        return new GitSplit(source.substring(0, slashIndex + 1) + repoPath, ref);
    }

    private sealed interface ParsedSource permits LocalSource, NpmSource, GitSource {
    }

    private record LocalSource(String path) implements ParsedSource {
    }

    private record NpmSource(String name) implements ParsedSource {
    }

    private record GitSource(String host, String path) implements ParsedSource {
    }

    private record GitSplit(String repo, String ref) {
    }

    private enum SourceScope {
        GLOBAL,
        PROJECT;

        private Path baseDir(Path agentDir, Path projectBaseDir) {
            return this == GLOBAL ? agentDir : projectBaseDir;
        }
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
