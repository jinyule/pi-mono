package dev.pi.session;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class PackageSourceManager {
    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("^[A-Za-z]:[\\\\/].*");
    private static final Pattern NPM_SPEC = Pattern.compile("^(@?[^@]+(?:/[^@]+)?)(?:@(.+))?$");

    private final Path cwd;
    private final Path agentDir;
    private final Path projectBaseDir;
    private final SettingsManager settingsManager;
    private final CommandRunner commandRunner;
    private Path globalNpmRoot;
    private boolean globalNpmRootResolved;

    public PackageSourceManager(Path cwd, SettingsManager settingsManager) {
        this(
            cwd,
            PiAgentPaths.agentDir(),
            settingsManager,
            new ProcessCommandRunner(),
            null,
            false
        );
    }

    public PackageSourceManager(
        Path cwd,
        Path agentDir,
        SettingsManager settingsManager,
        Path globalNpmRoot
    ) {
        this(cwd, agentDir, settingsManager, new ProcessCommandRunner(), globalNpmRoot, globalNpmRoot != null);
    }

    public PackageSourceManager(
        Path cwd,
        Path agentDir,
        SettingsManager settingsManager,
        CommandRunner commandRunner,
        Path globalNpmRoot
    ) {
        this(cwd, agentDir, settingsManager, commandRunner, globalNpmRoot, globalNpmRoot != null);
    }

    private PackageSourceManager(
        Path cwd,
        Path agentDir,
        SettingsManager settingsManager,
        CommandRunner commandRunner,
        Path globalNpmRoot,
        boolean globalNpmRootResolved
    ) {
        this.cwd = Objects.requireNonNull(cwd, "cwd").toAbsolutePath().normalize();
        this.agentDir = Objects.requireNonNull(agentDir, "agentDir").toAbsolutePath().normalize();
        this.projectBaseDir = this.cwd.resolve(".pi").toAbsolutePath().normalize();
        this.settingsManager = Objects.requireNonNull(settingsManager, "settingsManager");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
        this.globalNpmRoot = globalNpmRoot == null ? null : globalNpmRoot.toAbsolutePath().normalize();
        this.globalNpmRootResolved = globalNpmRootResolved;
    }

    public boolean addSourceToSettings(String source, Scope scope) {
        Objects.requireNonNull(scope, "scope");
        var currentPackages = scope == Scope.PROJECT ? settingsManager.getProjectPackages() : settingsManager.getGlobalPackages();
        for (var existing : currentPackages) {
            if (sourcesMatch(existing, source, scope)) {
                return false;
            }
        }
        var nextPackages = new ArrayList<>(currentPackages);
        nextPackages.add(new PackageSource(normalizeSourceForSettings(source, scope)));
        if (scope == Scope.PROJECT) {
            settingsManager.setProjectPackages(nextPackages);
        } else {
            settingsManager.setPackages(nextPackages);
        }
        return true;
    }

    public boolean removeSourceFromSettings(String source, Scope scope) {
        Objects.requireNonNull(scope, "scope");
        var currentPackages = scope == Scope.PROJECT ? settingsManager.getProjectPackages() : settingsManager.getGlobalPackages();
        var nextPackages = currentPackages.stream()
            .filter(existing -> !sourcesMatch(existing, source, scope))
            .toList();
        if (nextPackages.size() == currentPackages.size()) {
            return false;
        }
        if (scope == Scope.PROJECT) {
            settingsManager.setProjectPackages(nextPackages);
        } else {
            settingsManager.setPackages(nextPackages);
        }
        return true;
    }

    public Path getInstalledPath(String source, Scope scope) {
        return resolvePackageRoot(source, scope, false);
    }

    public Path resolvePackageRoot(String source, Scope scope, boolean installMissing) {
        Objects.requireNonNull(scope, "scope");
        var parsed = parseSource(source);
        Path path = switch (parsed) {
            case NpmSource npmSource -> resolveNpmInstallPath(scope, npmSource);
            case GitSource gitSource -> resolveGitInstallPath(scope, gitSource);
            case LocalSource localSource -> resolvePathFromBase(localSource.path(), scope.baseDir(agentDir, projectBaseDir));
        };
        if (path == null) {
            return null;
        }
        var normalized = path.toAbsolutePath().normalize();
        if (Files.exists(normalized)) {
            return normalized;
        }
        if (!installMissing || parsed instanceof LocalSource) {
            return null;
        }
        install(source, scope);
        return Files.exists(normalized) ? normalized : null;
    }

    public void install(String source, Scope scope) {
        Objects.requireNonNull(scope, "scope");
        var parsed = parseSource(source);
        switch (parsed) {
            case NpmSource npmSource -> installNpm(npmSource, scope);
            case GitSource gitSource -> installGit(gitSource, scope);
            case LocalSource localSource -> {
                var resolved = resolvePath(localSource.path());
                if (!Files.exists(resolved)) {
                    throw new IllegalStateException("Path does not exist: " + resolved);
                }
            }
        }
    }

    public void remove(String source, Scope scope) {
        Objects.requireNonNull(scope, "scope");
        var parsed = parseSource(source);
        switch (parsed) {
            case NpmSource npmSource -> uninstallNpm(npmSource, scope);
            case GitSource gitSource -> removeGit(gitSource, scope);
            case LocalSource ignored -> {
            }
        }
    }

    public void update() {
        update(null);
    }

    public void update(String source) {
        String requestedIdentity = source == null || source.isBlank() ? null : sourceMatchKeyForInput(source);
        for (var existing : settingsManager.getGlobalPackages()) {
            if (requestedIdentity != null && !sourceMatchKeyForSettings(existing, Scope.GLOBAL).equals(requestedIdentity)) {
                continue;
            }
            updateConfiguredSource(existing.source(), Scope.GLOBAL);
        }
        for (var existing : settingsManager.getProjectPackages()) {
            if (requestedIdentity != null && !sourceMatchKeyForSettings(existing, Scope.PROJECT).equals(requestedIdentity)) {
                continue;
            }
            updateConfiguredSource(existing.source(), Scope.PROJECT);
        }
    }

    private void updateConfiguredSource(String source, Scope scope) {
        var parsed = parseSource(source);
        switch (parsed) {
            case NpmSource npmSource -> {
                if (!npmSource.pinned()) {
                    installNpm(npmSource, scope);
                }
            }
            case GitSource gitSource -> {
                if (!gitSource.pinned()) {
                    updateGit(gitSource, scope);
                }
            }
            case LocalSource ignored -> {
            }
        }
    }

    private void installNpm(NpmSource source, Scope scope) {
        if (scope == Scope.GLOBAL) {
            commandRunner.run(List.of(npmCommand(), "install", "-g", source.spec()), null);
            return;
        }
        var installRoot = projectBaseDir.resolve("npm");
        ensureNpmProject(installRoot);
        commandRunner.run(List.of(npmCommand(), "install", source.spec(), "--prefix", installRoot.toString()), cwd);
    }

    private void uninstallNpm(NpmSource source, Scope scope) {
        if (scope == Scope.GLOBAL) {
            commandRunner.run(List.of(npmCommand(), "uninstall", "-g", source.name()), null);
            return;
        }
        var installRoot = projectBaseDir.resolve("npm");
        if (!Files.exists(installRoot)) {
            return;
        }
        commandRunner.run(List.of(npmCommand(), "uninstall", source.name(), "--prefix", installRoot.toString()), cwd);
    }

    private void installGit(GitSource source, Scope scope) {
        var targetDir = resolveGitInstallPath(scope, source);
        if (Files.exists(targetDir)) {
            return;
        }
        try {
            Files.createDirectories(targetDir.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare install directory", exception);
        }
        if (scope == Scope.PROJECT) {
            ensureGitIgnore(projectBaseDir.resolve("git"));
        } else {
            ensureGitIgnore(agentDir.resolve("git"));
        }
        commandRunner.run(List.of("git", "clone", source.repo(), targetDir.toString()), cwd);
        if (source.ref() != null && !source.ref().isBlank()) {
            commandRunner.run(List.of("git", "checkout", source.ref()), targetDir);
        }
        if (Files.exists(targetDir.resolve("package.json"))) {
            commandRunner.run(List.of(npmCommand(), "install"), targetDir);
        }
    }

    private void updateGit(GitSource source, Scope scope) {
        var targetDir = resolveGitInstallPath(scope, source);
        if (!Files.exists(targetDir)) {
            installGit(source, scope);
            return;
        }
        commandRunner.run(List.of("git", "fetch", "--prune", "origin"), targetDir);
        try {
            commandRunner.run(List.of("git", "reset", "--hard", "@{upstream}"), targetDir);
        } catch (RuntimeException exception) {
            commandRunner.run(List.of("git", "remote", "set-head", "origin", "-a"), targetDir);
            commandRunner.run(List.of("git", "reset", "--hard", "origin/HEAD"), targetDir);
        }
        commandRunner.run(List.of("git", "clean", "-fdx"), targetDir);
        if (Files.exists(targetDir.resolve("package.json"))) {
            commandRunner.run(List.of(npmCommand(), "install"), targetDir);
        }
    }

    private void removeGit(GitSource source, Scope scope) {
        var targetDir = resolveGitInstallPath(scope, source);
        try {
            Files.walk(targetDir)
                .sorted((left, right) -> right.getNameCount() - left.getNameCount())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
        pruneEmptyParents(targetDir.getParent(), scope == Scope.PROJECT ? projectBaseDir.resolve("git") : agentDir.resolve("git"));
    }

    private void pruneEmptyParents(Path current, Path installRoot) {
        if (current == null || installRoot == null) {
            return;
        }
        var normalizedRoot = installRoot.toAbsolutePath().normalize();
        var next = current.toAbsolutePath().normalize();
        while (next.startsWith(normalizedRoot) && !next.equals(normalizedRoot)) {
            try {
                if (!Files.exists(next)) {
                    break;
                }
                try (var children = Files.list(next)) {
                    if (children.findAny().isPresent()) {
                        break;
                    }
                }
                Files.deleteIfExists(next);
                next = next.getParent();
                if (next == null) {
                    break;
                }
            } catch (IOException exception) {
                break;
            }
        }
    }

    private void ensureNpmProject(Path installRoot) {
        try {
            Files.createDirectories(installRoot);
            ensureGitIgnore(installRoot);
            var packageJson = installRoot.resolve("package.json");
            if (!Files.exists(packageJson)) {
                var node = JsonNodeFactory.instance.objectNode();
                node.put("name", "pi-extensions");
                node.put("private", true);
                Files.writeString(packageJson, node.toPrettyString());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare npm install root", exception);
        }
    }

    private void ensureGitIgnore(Path dir) {
        try {
            Files.createDirectories(dir);
            var ignorePath = dir.resolve(".gitignore");
            if (!Files.exists(ignorePath)) {
                Files.writeString(ignorePath, "*\n!.gitignore\n");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare package directory", exception);
        }
    }

    private Path resolveNpmInstallPath(Scope scope, NpmSource source) {
        if (scope == Scope.PROJECT) {
            return projectBaseDir.resolve("npm").resolve("node_modules").resolve(source.name()).toAbsolutePath().normalize();
        }
        var npmRoot = globalNpmRoot();
        if (npmRoot == null) {
            return null;
        }
        return npmRoot.resolve(source.name()).toAbsolutePath().normalize();
    }

    private Path resolveGitInstallPath(Scope scope, GitSource source) {
        var baseDir = scope == Scope.PROJECT ? projectBaseDir.resolve("git") : agentDir.resolve("git");
        return baseDir.resolve(source.host()).resolve(source.path()).toAbsolutePath().normalize();
    }

    private Path globalNpmRoot() {
        if (!globalNpmRootResolved) {
            var output = commandRunner.runCapture(List.of(npmCommand(), "root", "-g"), null);
            globalNpmRoot = output == null || output.isBlank() ? null : Path.of(output.trim()).toAbsolutePath().normalize();
            globalNpmRootResolved = true;
        }
        return globalNpmRoot;
    }

    private boolean sourcesMatch(PackageSource existing, String source, Scope scope) {
        return sourceMatchKeyForSettings(existing, scope).equals(sourceMatchKeyForInput(source));
    }

    private String sourceMatchKeyForInput(String source) {
        var parsed = parseSource(source);
        return switch (parsed) {
            case NpmSource npmSource -> "npm:" + npmSource.name();
            case GitSource gitSource -> "git:" + gitSource.host() + "/" + gitSource.path();
            case LocalSource localSource -> "local:" + resolvePath(localSource.path());
        };
    }

    public String sourceIdentity(PackageSource source, Scope scope) {
        return sourceMatchKeyForSettings(source, scope);
    }

    private String sourceMatchKeyForSettings(PackageSource source, Scope scope) {
        var parsed = parseSource(source.source());
        return switch (parsed) {
            case NpmSource npmSource -> "npm:" + npmSource.name();
            case GitSource gitSource -> "git:" + gitSource.host() + "/" + gitSource.path();
            case LocalSource localSource -> "local:" + resolvePathFromBase(localSource.path(), scope.baseDir(agentDir, projectBaseDir));
        };
    }

    private String normalizeSourceForSettings(String source, Scope scope) {
        var parsed = parseSource(source);
        if (parsed instanceof LocalSource localSource) {
            var resolved = resolvePath(localSource.path());
            var baseDir = scope.baseDir(agentDir, projectBaseDir);
            try {
                return baseDir.relativize(resolved).toString();
            } catch (IllegalArgumentException exception) {
                return resolved.toString();
            }
        }
        return source;
    }

    private ParsedSource parseSource(String source) {
        var trimmed = source == null ? "" : source.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Package source must not be blank");
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

    private static boolean isLocalPathLike(String source) {
        return source.startsWith(".")
            || source.startsWith("/")
            || source.equals("~")
            || source.startsWith("~/")
            || source.startsWith("~\\")
            || source.startsWith("\\\\")
            || WINDOWS_ABSOLUTE_PATH.matcher(source).matches();
    }

    private static NpmSource parseNpmSource(String source) {
        var spec = source.substring("npm:".length()).trim();
        var match = NPM_SPEC.matcher(spec);
        if (!match.matches()) {
            return new NpmSource(spec, spec, false);
        }
        var name = match.group(1);
        var version = match.group(2);
        return new NpmSource(spec, name == null ? spec : name, version != null && !version.isBlank());
    }

    private static GitSource parseGitSource(String source) {
        var trimmed = source.trim();
        var hasGitPrefix = trimmed.startsWith("git:");
        var raw = hasGitPrefix ? trimmed.substring(4).trim() : trimmed;
        if (!hasGitPrefix && !hasExplicitGitProtocol(raw)) {
            return null;
        }

        var split = splitGitRef(raw);
        var repoWithoutRef = split.repo();
        String repo = repoWithoutRef;
        String host;
        String path;
        if (repoWithoutRef.startsWith("git@")) {
            var match = Pattern.compile("^git@([^:]+):(.+)$").matcher(repoWithoutRef);
            if (!match.matches()) {
                return null;
            }
            host = match.group(1);
            path = match.group(2);
        } else if (hasExplicitGitProtocol(repoWithoutRef)) {
            try {
                var uri = java.net.URI.create(repoWithoutRef);
                host = uri.getHost();
                path = uri.getPath();
            } catch (IllegalArgumentException exception) {
                return null;
            }
        } else {
            var slashIndex = repoWithoutRef.indexOf('/');
            if (slashIndex < 0) {
                return null;
            }
            host = repoWithoutRef.substring(0, slashIndex);
            path = repoWithoutRef.substring(slashIndex + 1);
            if (!host.contains(".") && !"localhost".equals(host)) {
                return null;
            }
            repo = "https://" + repoWithoutRef;
        }

        if (host == null || host.isBlank()) {
            return null;
        }
        var normalizedPath = path == null ? "" : path.replace('\\', '/').replaceAll("^/+", "").replaceAll("\\.git$", "");
        if (normalizedPath.split("/").length < 2) {
            return null;
        }
        return new GitSource(repo, host, normalizedPath, split.ref(), split.ref() != null && !split.ref().isBlank());
    }

    private static boolean hasExplicitGitProtocol(String source) {
        var normalized = source.toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://")
            || normalized.startsWith("https://")
            || normalized.startsWith("ssh://")
            || normalized.startsWith("git://")
            || normalized.startsWith("git@");
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
            } catch (Exception exception) {
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

    private Path resolvePath(String input) {
        return resolvePathFromBase(input, cwd);
    }

    private static Path resolvePathFromBase(String input, Path baseDir) {
        var trimmed = input.trim();
        if ("~".equals(trimmed)) {
            return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        }
        if (trimmed.startsWith("~/") || trimmed.startsWith("~\\")) {
            return Path.of(System.getProperty("user.home")).resolve(trimmed.substring(2)).toAbsolutePath().normalize();
        }
        var rawPath = Path.of(trimmed);
        return (rawPath.isAbsolute() ? rawPath : baseDir.resolve(rawPath)).toAbsolutePath().normalize();
    }

    private static String npmCommand() {
        return isWindows() ? "npm.cmd" : "npm";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public enum Scope {
        GLOBAL,
        PROJECT;

        private Path baseDir(Path agentDir, Path projectBaseDir) {
            return this == GLOBAL ? agentDir : projectBaseDir;
        }
    }

    public interface CommandRunner {
        void run(List<String> command, Path cwd);

        String runCapture(List<String> command, Path cwd);
    }

    private static final class ProcessCommandRunner implements CommandRunner {
        @Override
        public void run(List<String> command, Path cwd) {
            try {
                var process = new ProcessBuilder(command)
                    .directory(cwd == null ? null : cwd.toFile())
                    .redirectErrorStream(true)
                    .start();
                var output = new String(process.getInputStream().readAllBytes());
                if (process.waitFor() != 0) {
                    throw new IllegalStateException(output.isBlank() ? "Command failed: " + String.join(" ", command) : output.trim());
                }
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException("Failed to run command: " + String.join(" ", command), exception);
            }
        }

        @Override
        public String runCapture(List<String> command, Path cwd) {
            try {
                var process = new ProcessBuilder(command)
                    .directory(cwd == null ? null : cwd.toFile())
                    .redirectErrorStream(true)
                    .start();
                var output = new String(process.getInputStream().readAllBytes());
                if (process.waitFor() != 0) {
                    throw new IllegalStateException(output.isBlank() ? "Command failed: " + String.join(" ", command) : output.trim());
                }
                return output.trim();
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException("Failed to run command: " + String.join(" ", command), exception);
            }
        }
    }

    private sealed interface ParsedSource permits NpmSource, GitSource, LocalSource {
    }

    private record NpmSource(String spec, String name, boolean pinned) implements ParsedSource {
    }

    private record GitSource(String repo, String host, String path, String ref, boolean pinned) implements ParsedSource {
    }

    private record LocalSource(String path) implements ParsedSource {
    }

    private record GitSplit(String repo, String ref) {
    }
}
