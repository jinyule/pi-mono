package dev.pi.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pi.session.PackageSource;
import dev.pi.session.PackageSourceManager;
import dev.pi.session.SettingsManager;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class PiConfigResolver {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SettingsManager settingsManager;
    private final PackageSourceManager packageSourceManager;

    PiConfigResolver(SettingsManager settingsManager, PackageSourceManager packageSourceManager) {
        this.settingsManager = Objects.requireNonNull(settingsManager, "settingsManager");
        this.packageSourceManager = Objects.requireNonNull(packageSourceManager, "packageSourceManager");
    }

    List<ResourceGroup> resolve() {
        var groups = new ArrayList<ResourceGroup>();
        for (var entry : dedupePackages()) {
            resolveScope(entry.packageSource(), entry.scope(), entry.managerScope(), groups);
        }
        return List.copyOf(groups);
    }

    void toggle(ResourceItem item, boolean enabled) {
        Objects.requireNonNull(item, "item");
        var currentPackages = item.scope() == Scope.PROJECT
            ? new ArrayList<>(settingsManager.getProjectPackages())
            : new ArrayList<>(settingsManager.getGlobalPackages());
        for (var index = 0; index < currentPackages.size(); index += 1) {
            var existing = currentPackages.get(index);
            if (!existing.source().equals(item.source())) {
                continue;
            }

            currentPackages.set(index, updatePackage(existing, item, enabled));
            if (item.scope() == Scope.PROJECT) {
                settingsManager.setProjectPackages(currentPackages);
            } else {
                settingsManager.setPackages(currentPackages);
            }
            return;
        }
    }

    private List<ScopedPackageSource> dedupePackages() {
        var deduped = new java.util.LinkedHashMap<String, ScopedPackageSource>();
        addPackages(deduped, settingsManager.getProjectPackages(), Scope.PROJECT, PackageSourceManager.Scope.PROJECT);
        addPackages(deduped, settingsManager.getGlobalPackages(), Scope.USER, PackageSourceManager.Scope.GLOBAL);
        return List.copyOf(deduped.values());
    }

    private void addPackages(
        java.util.LinkedHashMap<String, ScopedPackageSource> deduped,
        List<PackageSource> packageSources,
        Scope scope,
        PackageSourceManager.Scope managerScope
    ) {
        for (var packageSource : packageSources) {
            if (packageSource == null) {
                continue;
            }
            deduped.putIfAbsent(
                packageSourceManager.sourceIdentity(packageSource, managerScope),
                new ScopedPackageSource(packageSource, scope, managerScope)
            );
        }
    }

    private void resolveScope(
        PackageSource packageSource,
        Scope scope,
        PackageSourceManager.Scope managerScope,
        List<ResourceGroup> groups
    ) {
        var packageRoot = packageSourceManager.resolvePackageRoot(packageSource.source(), managerScope, true);
        if (packageRoot == null || !Files.exists(packageRoot)) {
            return;
        }

        var sections = new ArrayList<ResourceSection>();
        for (var type : ResourceType.values()) {
            var items = resolveItems(packageRoot, packageSource, scope, type);
            if (!items.isEmpty()) {
                sections.add(new ResourceSection(type, items));
            }
        }
        if (!sections.isEmpty()) {
            groups.add(new ResourceGroup(packageSource.source(), scope, sections));
        }
    }

    private List<ResourceItem> resolveItems(
        Path packageRoot,
        PackageSource packageSource,
        Scope scope,
        ResourceType type
    ) {
        var candidates = collectCandidates(packageRoot, type);
        if (candidates.isEmpty()) {
            return List.of();
        }
        var filters = new ArrayList<String>();
        var manifestEntries = readManifestEntries(packageRoot, type);
        if (manifestEntries != null) {
            filters.addAll(manifestEntries);
        }
        filters.addAll(type.filters(packageSource));
        var enabled = filters.isEmpty() ? new LinkedHashSet<>(candidates) : applyPatterns(candidates, filters, packageRoot);
        return candidates.stream()
            .map(path -> new ResourceItem(path, enabled.contains(path), type, scope, packageSource.source(), packageRoot))
            .toList();
    }

    private static List<Path> collectCandidates(Path packageRoot, ResourceType type) {
        if (Files.isRegularFile(packageRoot)) {
            return type == ResourceType.EXTENSIONS ? List.of(packageRoot.toAbsolutePath().normalize()) : List.of();
        }
        if (!Files.isDirectory(packageRoot)) {
            return List.of();
        }

        var manifestEntries = readManifestEntries(packageRoot, type);
        if (manifestEntries != null) {
            return collectManifestCandidates(packageRoot, manifestEntries, type);
        }

        return defaultCandidates(packageRoot, type);
    }

    private static List<Path> defaultCandidates(Path packageRoot, ResourceType type) {
        var conventionDir = packageRoot.resolve(type.directoryName());
        if (Files.isDirectory(conventionDir)) {
            return collectFilesFromPath(conventionDir, type);
        }

        if (type == ResourceType.EXTENSIONS) {
            var topLevelEntries = new ArrayList<Path>();
            addIfRegularFile(packageRoot.resolve("index.js"), topLevelEntries);
            addIfRegularFile(packageRoot.resolve("index.ts"), topLevelEntries);
            addIfRegularFile(packageRoot.resolve("index.jar"), topLevelEntries);
            return List.copyOf(topLevelEntries);
        }
        return List.of();
    }

    private static List<String> readManifestEntries(Path packageRoot, ResourceType type) {
        var packageJson = packageRoot.resolve("package.json");
        if (!Files.isRegularFile(packageJson)) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(Files.readString(packageJson));
            var node = root == null ? null : root.path("pi").path(type.directoryName());
            if (node == null || node.isMissingNode()) {
                return null;
            }
            if (!node.isArray()) {
                return List.of();
            }
            var values = new ArrayList<String>();
            for (var entry : node) {
                if (!entry.isTextual()) {
                    continue;
                }
                var text = entry.asText().trim();
                if (!text.isBlank()) {
                    values.add(text);
                }
            }
            return List.copyOf(values);
        } catch (IOException exception) {
            return null;
        }
    }

    private static List<Path> collectManifestCandidates(Path packageRoot, List<String> entries, ResourceType type) {
        if (entries.isEmpty()) {
            return List.of();
        }
        var plainEntries = entries.stream().filter(entry -> !isPattern(entry)).toList();
        var candidates = new LinkedHashSet<Path>();
        if (!plainEntries.isEmpty()) {
            candidates.addAll(collectFilesFromPaths(plainEntries.stream().map(packageRoot::resolve).toList(), type));
        }
        if (candidates.isEmpty()) {
            candidates.addAll(defaultCandidates(packageRoot, type));
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
            .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private static List<Path> collectFilesFromPaths(List<Path> paths, ResourceType type) {
        var collected = new LinkedHashSet<Path>();
        for (var path : paths) {
            collected.addAll(collectFilesFromPath(path, type));
        }
        return collected.stream().sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static List<Path> collectFilesFromPath(Path path, ResourceType type) {
        if (path == null || !Files.exists(path)) {
            return List.of();
        }
        if (Files.isRegularFile(path)) {
            return type.matches(path) ? List.of(path.toAbsolutePath().normalize()) : List.of();
        }
        if (!Files.isDirectory(path)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.walk(path, FileVisitOption.FOLLOW_LINKS)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(type::matches)
                .map(candidate -> candidate.toAbsolutePath().normalize())
                .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
                .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private static void addIfRegularFile(Path path, List<Path> target) {
        if (Files.isRegularFile(path)) {
            target.add(path.toAbsolutePath().normalize());
        }
    }

    private static boolean isPattern(String entry) {
        return entry.startsWith("!")
            || entry.startsWith("+")
            || entry.startsWith("-")
            || entry.indexOf('*') >= 0
            || entry.indexOf('?') >= 0;
    }

    private static LinkedHashSet<Path> applyPatterns(List<Path> allFiles, List<String> patterns, Path baseDir) {
        var includes = new ArrayList<String>();
        var excludes = new ArrayList<String>();
        var forceIncludes = new ArrayList<String>();
        var forceExcludes = new ArrayList<String>();

        for (var pattern : patterns) {
            if (pattern.startsWith("+")) {
                forceIncludes.add(pattern.substring(1));
            } else if (pattern.startsWith("-")) {
                forceExcludes.add(pattern.substring(1));
            } else if (pattern.startsWith("!")) {
                excludes.add(pattern.substring(1));
            } else {
                includes.add(pattern);
            }
        }

        var result = new LinkedHashSet<Path>();
        if (includes.isEmpty()) {
            result.addAll(allFiles);
        } else {
            for (var file : allFiles) {
                if (matchesAnyPattern(file, includes, baseDir)) {
                    result.add(file);
                }
            }
        }

        if (!excludes.isEmpty()) {
            result.removeIf(file -> matchesAnyPattern(file, excludes, baseDir));
        }
        if (!forceIncludes.isEmpty()) {
            for (var file : allFiles) {
                if (matchesAnyExactPattern(file, forceIncludes, baseDir)) {
                    result.add(file);
                }
            }
        }
        if (!forceExcludes.isEmpty()) {
            result.removeIf(file -> matchesAnyExactPattern(file, forceExcludes, baseDir));
        }
        return result;
    }

    private static boolean matchesAnyPattern(Path file, List<String> patterns, Path baseDir) {
        var relative = normalize(baseDir.relativize(file));
        for (var pattern : patterns) {
            if (globMatches(relative, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnyExactPattern(Path file, List<String> patterns, Path baseDir) {
        var relative = normalize(baseDir.relativize(file));
        for (var pattern : patterns) {
            if (normalizeExactPattern(pattern).equals(relative)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeExactPattern(String value) {
        var normalized = normalize(value);
        return normalized.startsWith("./") ? normalized.substring(2) : normalized;
    }

    private static String normalize(Path path) {
        return normalize(path.toString());
    }

    private static String normalize(String value) {
        return value.replace('\\', '/');
    }

    private static boolean globMatches(String value, String pattern) {
        var regex = new StringBuilder("^");
        var normalized = normalize(pattern);
        for (var index = 0; index < normalized.length(); index += 1) {
            var ch = normalized.charAt(index);
            switch (ch) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '(', ')', '+', '{', '}', '^', '$', '|', '\\' -> regex.append('\\').append(ch);
                default -> regex.append(ch);
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE).matcher(value).matches();
    }

    private static PackageSource updatePackage(PackageSource source, ResourceItem item, boolean enabled) {
        var extensions = new ArrayList<>(source.extensions());
        var skills = new ArrayList<>(source.skills());
        var prompts = new ArrayList<>(source.prompts());
        var themes = new ArrayList<>(source.themes());
        var target = switch (item.resourceType()) {
            case EXTENSIONS -> extensions;
            case SKILLS -> skills;
            case PROMPTS -> prompts;
            case THEMES -> themes;
        };

        var pattern = normalize(item.packageRoot().relativize(item.path()));
        target.removeIf(entry -> normalizeExactPattern(stripModifier(entry)).equals(pattern));
        target.add((enabled ? "+" : "-") + pattern);

        if (extensions.isEmpty() && skills.isEmpty() && prompts.isEmpty() && themes.isEmpty()) {
            return new PackageSource(source.source());
        }
        return new PackageSource(source.source(), extensions, skills, prompts, themes);
    }

    private static String stripModifier(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.startsWith("!") || value.startsWith("+") || value.startsWith("-")) {
            return value.substring(1);
        }
        return value;
    }

    enum Scope {
        USER("user"),
        PROJECT("project");

        private final String label;

        Scope(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    enum ResourceType {
        EXTENSIONS("extensions", "Extensions") {
            @Override
            boolean matches(Path path) {
                var name = lowerFileName(path);
                return name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".jar");
            }

            @Override
            List<String> filters(PackageSource source) {
                return source.extensions();
            }
        },
        SKILLS("skills", "Skills") {
            @Override
            boolean matches(Path path) {
                return lowerFileName(path).endsWith(".md");
            }

            @Override
            List<String> filters(PackageSource source) {
                return source.skills();
            }
        },
        PROMPTS("prompts", "Prompts") {
            @Override
            boolean matches(Path path) {
                return lowerFileName(path).endsWith(".md");
            }

            @Override
            List<String> filters(PackageSource source) {
                return source.prompts();
            }
        },
        THEMES("themes", "Themes") {
            @Override
            boolean matches(Path path) {
                return lowerFileName(path).endsWith(".json");
            }

            @Override
            List<String> filters(PackageSource source) {
                return source.themes();
            }
        };

        private final String directoryName;
        private final String label;

        ResourceType(String directoryName, String label) {
            this.directoryName = directoryName;
            this.label = label;
        }

        String directoryName() {
            return directoryName;
        }

        String label() {
            return label;
        }

        abstract boolean matches(Path path);

        abstract List<String> filters(PackageSource source);
    }

    record ResourceGroup(String source, Scope scope, List<ResourceSection> sections) {
        ResourceGroup {
            sections = List.copyOf(sections);
        }

        String label() {
            return source + " (" + scope.label() + ")";
        }
    }

    record ResourceSection(ResourceType type, List<ResourceItem> items) {
        ResourceSection {
            items = List.copyOf(items);
        }
    }

    record ResourceItem(
        Path path,
        boolean enabled,
        ResourceType resourceType,
        Scope scope,
        String source,
        Path packageRoot
    ) {
        ResourceItem {
            path = path.toAbsolutePath().normalize();
            packageRoot = packageRoot.toAbsolutePath().normalize();
        }

        String displayName() {
            var relative = normalize(packageRoot.relativize(path));
            if ("SKILL.md".equals(path.getFileName().toString())) {
                var parent = path.getParent();
                if (parent != null && !parent.equals(packageRoot)) {
                    return normalize(packageRoot.relativize(parent));
                }
            }
            return relative;
        }
    }

    private static String lowerFileName(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT);
    }

    private record ScopedPackageSource(
        PackageSource packageSource,
        Scope scope,
        PackageSourceManager.Scope managerScope
    ) {
    }
}
