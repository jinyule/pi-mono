package dev.pi.session;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final AuthStorage authStorage;
    private Path globalNpmRoot;
    private boolean globalNpmRootResolved;

    public PackageSourceManager(Path cwd, SettingsManager settingsManager) {
        this(
            cwd,
            PiAgentPaths.agentDir(),
            settingsManager,
            new ProcessCommandRunner(),
            null,
            AuthStorage.inMemory(),
            false
        );
    }

    public PackageSourceManager(Path cwd, SettingsManager settingsManager, AuthStorage authStorage) {
        this(
            cwd,
            PiAgentPaths.agentDir(),
            settingsManager,
            new ProcessCommandRunner(),
            null,
            authStorage,
            false
        );
    }

    public PackageSourceManager(
        Path cwd,
        Path agentDir,
        SettingsManager settingsManager,
        Path globalNpmRoot
    ) {
        this(cwd, agentDir, settingsManager, new ProcessCommandRunner(), globalNpmRoot, AuthStorage.inMemory(), globalNpmRoot != null);
    }

    public PackageSourceManager(
        Path cwd,
        Path agentDir,
        SettingsManager settingsManager,
        Path globalNpmRoot,
        AuthStorage authStorage
    ) {
        this(cwd, agentDir, settingsManager, new ProcessCommandRunner(), globalNpmRoot, authStorage, globalNpmRoot != null);
    }

    public PackageSourceManager(
        Path cwd,
        Path agentDir,
        SettingsManager settingsManager,
        CommandRunner commandRunner,
        Path globalNpmRoot
    ) {
        this(cwd, agentDir, settingsManager, commandRunner, globalNpmRoot, AuthStorage.inMemory(), globalNpmRoot != null);
    }

    public PackageSourceManager(
        Path cwd,
        Path agentDir,
        SettingsManager settingsManager,
        CommandRunner commandRunner,
        Path globalNpmRoot,
        AuthStorage authStorage
    ) {
        this(cwd, agentDir, settingsManager, commandRunner, globalNpmRoot, authStorage, globalNpmRoot != null);
    }

    private PackageSourceManager(
        Path cwd,
        Path agentDir,
        SettingsManager settingsManager,
        CommandRunner commandRunner,
        Path globalNpmRoot,
        AuthStorage authStorage,
        boolean globalNpmRootResolved
    ) {
        this.cwd = Objects.requireNonNull(cwd, "cwd").toAbsolutePath().normalize();
        this.agentDir = Objects.requireNonNull(agentDir, "agentDir").toAbsolutePath().normalize();
        this.projectBaseDir = this.cwd.resolve(".pi").toAbsolutePath().normalize();
        this.settingsManager = Objects.requireNonNull(settingsManager, "settingsManager");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
        this.authStorage = authStorage == null ? AuthStorage.inMemory() : authStorage;
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
        var npmAuthConfig = npmAuthConfig(source);
        if (scope == Scope.GLOBAL) {
            runNpmCommand(List.of(npmCommand(), "install", "-g", source.spec()), null, npmAuthConfig);
            return;
        }
        var installRoot = projectBaseDir.resolve("npm");
        ensureNpmProject(installRoot);
        runNpmCommand(List.of(npmCommand(), "install", source.spec(), "--prefix", installRoot.toString()), cwd, npmAuthConfig);
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
        var auth = gitAuthConfig(source);
        commandRunner.run(List.of("git", "clone", auth.remoteUrl(), targetDir.toString()), cwd, auth.environment());
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
        var auth = gitAuthConfig(source);
        if (!auth.remoteUrl().equals(source.repo())) {
            commandRunner.run(List.of("git", "remote", "set-url", "origin", auth.remoteUrl()), targetDir, auth.environment());
        }
        commandRunner.run(List.of("git", "fetch", "--prune", "origin"), targetDir, auth.environment());
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

    private void runNpmCommand(List<String> command, Path cwd, NpmAuthConfig authConfig) {
        if (authConfig == null) {
            commandRunner.run(command, cwd);
            return;
        }

        var tempConfig = writeTempNpmUserConfig(authConfig);
        try {
            commandRunner.run(command, cwd, Map.of("NPM_CONFIG_USERCONFIG", tempConfig.toString()));
        } finally {
            try {
                Files.deleteIfExists(tempConfig);
            } catch (IOException ignored) {
            }
        }
    }

    private NpmAuthConfig npmAuthConfig(NpmSource source) {
        var npmrc = loadNpmrcConfig();
        var registry = npmRegistryFor(source, npmrc);
        if (registry == null) {
            return null;
        }

        var lines = new ArrayList<>(npmrc.rawLines());
        var normalizedRegistry = normalizeRegistryUrl(registry.registryUrl());
        if (normalizedRegistry == null) {
            return lines.isEmpty() ? null : new NpmAuthConfig(List.copyOf(lines));
        }
        if (registry.scope() == null) {
            lines.add("registry=" + normalizedRegistry);
        } else {
            lines.add(registry.scope() + ":registry=" + normalizedRegistry);
        }

        if (!npmrc.hasAuthFor(registry.registryUrl())) {
            var token = npmRegistryToken(registry.registryUrl());
            if (token != null && !token.isBlank()) {
                lines.add(authTokenConfigKey(registry.registryUrl()) + "=" + token);
                if (!npmrc.hasAlwaysAuth()) {
                    lines.add("always-auth=true");
                }
            }
        }

        if (lines.isEmpty()) {
            return null;
        }
        return new NpmAuthConfig(List.copyOf(lines));
    }

    private Path writeTempNpmUserConfig(NpmAuthConfig authConfig) {
        try {
            var tempConfig = Files.createTempFile("pi-npm-auth-", ".npmrc");
            Files.writeString(tempConfig, String.join(System.lineSeparator(), authConfig.lines()) + System.lineSeparator(), StandardCharsets.UTF_8);
            return tempConfig;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare npm auth config", exception);
        }
    }

    private NpmRegistryConfig npmRegistryFor(NpmSource source, NpmrcConfig npmrc) {
        var scope = packageScope(source.name());
        if (scope != null) {
            var scopedRegistry = npmrc.scopedRegistries().get(scope);
            if (scopedRegistry != null) {
                return new NpmRegistryConfig(scope, scopedRegistry);
            }
        }
        if (npmrc.defaultRegistry() != null) {
            return new NpmRegistryConfig(null, npmrc.defaultRegistry());
        }
        return null;
    }

    private NpmrcConfig loadNpmrcConfig() {
        String defaultRegistry = null;
        var scopedRegistries = new LinkedHashMap<String, String>();
        var rawLines = new ArrayList<String>();
        var entries = new LinkedHashMap<String, String>();
        boolean alwaysAuth = false;
        var configPaths = List.of(
            Path.of(System.getProperty("user.home")).resolve(".npmrc"),
            cwd.resolve(".npmrc")
        );
        for (var configPath : configPaths) {
            if (!Files.exists(configPath)) {
                continue;
            }
            try {
                for (var rawLine : Files.readAllLines(configPath, StandardCharsets.UTF_8)) {
                    rawLines.add(rawLine);
                    var line = rawLine.trim();
                    if (line.isBlank() || line.startsWith("#") || line.startsWith(";")) {
                        continue;
                    }
                    var separatorIndex = line.indexOf('=');
                    if (separatorIndex < 0) {
                        continue;
                    }
                    var key = line.substring(0, separatorIndex).trim();
                    var value = line.substring(separatorIndex + 1).trim();
                    entries.put(key, value);
                    if ("always-auth".equals(key) && "true".equalsIgnoreCase(value)) {
                        alwaysAuth = true;
                    }
                    if ("registry".equals(key)) {
                        defaultRegistry = normalizeRegistryUrl(value);
                        continue;
                    }
                    if (key.startsWith("@") && key.endsWith(":registry")) {
                        var scope = key.substring(0, key.length() - ":registry".length());
                        var registryUrl = normalizeRegistryUrl(value);
                        if (registryUrl != null) {
                            scopedRegistries.put(scope, registryUrl);
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return new NpmrcConfig(defaultRegistry, Map.copyOf(scopedRegistries), Map.copyOf(entries), List.copyOf(rawLines), alwaysAuth);
    }

    private String npmRegistryToken(String registryUrl) {
        var normalizedRegistry = normalizeRegistryUrl(registryUrl);
        if (normalizedRegistry == null) {
            return null;
        }
        var candidates = new LinkedHashSet<String>();
        candidates.add(normalizedRegistry);
        try {
            var uri = java.net.URI.create(normalizedRegistry);
            var host = uri.getHost();
            var path = normalizedRegistryPath(uri.getPath());
            if (host != null && !host.isBlank()) {
                candidates.add(host + path);
                candidates.add(host);
                var providerId = authProviderForNpmRegistryHost(host);
                if (providerId != null) {
                    candidates.add(providerId);
                }
            }
        } catch (IllegalArgumentException ignored) {
        }
        for (var candidate : candidates) {
            var token = authStorage.getApiKey(candidate);
            if (token != null && !token.isBlank()) {
                return token;
            }
        }
        return null;
    }

    private static String authTokenConfigKey(String registryUrl) {
        var normalizedRegistry = normalizeRegistryUrl(registryUrl);
        if (normalizedRegistry == null) {
            throw new IllegalArgumentException("Registry URL is required");
        }
        try {
            var uri = java.net.URI.create(normalizedRegistry);
            var host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("Registry host is required");
            }
            return "//" + host + normalizedRegistryPath(uri.getPath()) + ":_authToken";
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid registry URL: " + registryUrl, exception);
        }
    }

    private static String normalizeRegistryUrl(String registryUrl) {
        if (registryUrl == null || registryUrl.isBlank()) {
            return null;
        }
        var trimmed = registryUrl.trim();
        if (!trimmed.endsWith("/")) {
            trimmed += "/";
        }
        return trimmed;
    }

    private static String normalizedRegistryPath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "/";
        }
        var normalized = path.startsWith("/") ? path : "/" + path;
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private static String packageScope(String packageName) {
        if (packageName == null || !packageName.startsWith("@")) {
            return null;
        }
        var slashIndex = packageName.indexOf('/');
        if (slashIndex < 0) {
            return null;
        }
        return packageName.substring(0, slashIndex);
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

    private GitAuthConfig gitAuthConfig(GitSource source) {
        var providerId = authProviderForHost(source.host());
        var token = providerId == null ? null : authStorage.getApiKey(providerId);
        if (token == null || token.isBlank()) {
            token = authStorage.getApiKey(source.host());
        }
        if (token == null || token.isBlank()) {
            return new GitAuthConfig(source.repo(), Map.of());
        }
        return new GitAuthConfig(
            "https://" + source.host() + "/" + source.path() + ".git",
            Map.of(
                "GIT_CONFIG_COUNT", "1",
                "GIT_CONFIG_KEY_0", "http.https://" + source.host() + "/.extraHeader",
                "GIT_CONFIG_VALUE_0", "Authorization: Bearer " + token
            )
        );
    }

    private static String authProviderForHost(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        return switch (host.toLowerCase(Locale.ROOT)) {
            case "github.com", "www.github.com" -> "github";
            case "gitlab.com", "www.gitlab.com" -> "gitlab";
            default -> null;
        };
    }

    private static String authProviderForNpmRegistryHost(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        return switch (host.toLowerCase(Locale.ROOT)) {
            case "npm.pkg.github.com" -> "github";
            default -> authProviderForHost(host);
        };
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

        default void run(List<String> command, Path cwd, Map<String, String> environment) {
            run(command, cwd);
        }

        default String runCapture(List<String> command, Path cwd, Map<String, String> environment) {
            return runCapture(command, cwd);
        }
    }

    private static final class ProcessCommandRunner implements CommandRunner {
        @Override
        public void run(List<String> command, Path cwd) {
            run(command, cwd, Map.of());
        }

        @Override
        public void run(List<String> command, Path cwd, Map<String, String> environment) {
            try {
                var processBuilder = new ProcessBuilder(command)
                    .directory(cwd == null ? null : cwd.toFile())
                    .redirectErrorStream(true);
                if (environment != null && !environment.isEmpty()) {
                    processBuilder.environment().putAll(environment);
                }
                var process = processBuilder.start();
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
            return runCapture(command, cwd, Map.of());
        }

        @Override
        public String runCapture(List<String> command, Path cwd, Map<String, String> environment) {
            try {
                var processBuilder = new ProcessBuilder(command)
                    .directory(cwd == null ? null : cwd.toFile())
                    .redirectErrorStream(true);
                if (environment != null && !environment.isEmpty()) {
                    processBuilder.environment().putAll(environment);
                }
                var process = processBuilder.start();
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

    private record GitAuthConfig(
        String remoteUrl,
        Map<String, String> environment
    ) {
        private GitAuthConfig {
            environment = Map.copyOf(environment);
        }
    }

    private sealed interface ParsedSource permits NpmSource, GitSource, LocalSource {
    }

    private record NpmSource(String spec, String name, boolean pinned) implements ParsedSource {
    }

    private record NpmAuthConfig(List<String> lines) {
        private NpmAuthConfig {
            lines = List.copyOf(lines);
        }
    }

    private record NpmRegistryConfig(String scope, String registryUrl) {
    }

    private record NpmrcConfig(
        String defaultRegistry,
        Map<String, String> scopedRegistries,
        Map<String, String> entries,
        List<String> rawLines,
        boolean hasAlwaysAuth
    ) {
        private NpmrcConfig {
            scopedRegistries = Map.copyOf(scopedRegistries);
            entries = Map.copyOf(entries);
            rawLines = List.copyOf(rawLines);
        }

        private boolean hasAuthFor(String registryUrl) {
            var normalizedRegistry = normalizeRegistryUrl(registryUrl);
            if (normalizedRegistry == null) {
                return false;
            }
            var tokenKey = authTokenConfigKey(normalizedRegistry);
            var prefix = tokenKey.substring(0, tokenKey.length() - ":_authToken".length());
            return entries.containsKey(tokenKey)
                || entries.containsKey(prefix + ":_auth")
                || entries.containsKey(prefix + ":username")
                || entries.containsKey(prefix + ":_password")
                || entries.containsKey(prefix + ":password")
                || entries.containsKey("_authToken")
                || entries.containsKey("_auth");
        }
    }

    private record GitSource(String repo, String host, String path, String ref, boolean pinned) implements ParsedSource {
    }

    private record LocalSource(String path) implements ParsedSource {
    }

    private record GitSplit(String repo, String ref) {
    }
}
