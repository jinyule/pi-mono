package dev.pi.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

public final class SettingsManager {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Storage storage;
    private Settings globalSettings;
    private Settings projectSettings;
    private Settings effectiveSettings;
    private final List<SettingsError> errors = new ArrayList<>();

    private SettingsManager(Storage storage, Settings globalSettings, Settings projectSettings, List<SettingsError> initialErrors) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.globalSettings = Objects.requireNonNull(globalSettings, "globalSettings");
        this.projectSettings = Objects.requireNonNull(projectSettings, "projectSettings");
        this.effectiveSettings = globalSettings.merge(projectSettings);
        this.errors.addAll(initialErrors);
    }

    public static SettingsManager create(Path cwd) {
        Objects.requireNonNull(cwd, "cwd");
        var agentDir = Path.of(System.getProperty("user.home"), ".pi", "agent");
        return create(cwd, agentDir);
    }

    public static SettingsManager create(Path cwd, Path agentDir) {
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(agentDir, "agentDir");
        return fromStorage(new FileStorage(cwd.resolve(".pi").resolve("settings.json"), agentDir.resolve("settings.json")));
    }

    public static SettingsManager inMemory() {
        return inMemory(Settings.empty(), Settings.empty());
    }

    public static SettingsManager inMemory(Settings globalSettings, Settings projectSettings) {
        return fromStorage(new InMemoryStorage(serialize(globalSettings), serialize(projectSettings)));
    }

    static SettingsManager fromStorage(Storage storage) {
        var globalLoad = tryLoad(storage, SettingsScope.GLOBAL);
        var projectLoad = tryLoad(storage, SettingsScope.PROJECT);
        var initialErrors = new ArrayList<SettingsError>(2);
        if (globalLoad.error() != null) {
            initialErrors.add(new SettingsError(SettingsScope.GLOBAL, globalLoad.error()));
        }
        if (projectLoad.error() != null) {
            initialErrors.add(new SettingsError(SettingsScope.PROJECT, projectLoad.error()));
        }
        return new SettingsManager(storage, globalLoad.settings(), projectLoad.settings(), initialErrors);
    }

    public Settings effective() {
        return effectiveSettings;
    }

    public Settings global() {
        return globalSettings;
    }

    public Settings project() {
        return projectSettings;
    }

    public void reload() {
        var globalLoad = tryLoad(storage, SettingsScope.GLOBAL);
        if (globalLoad.error() == null) {
            globalSettings = globalLoad.settings();
        } else {
            recordError(SettingsScope.GLOBAL, globalLoad.error());
        }

        var projectLoad = tryLoad(storage, SettingsScope.PROJECT);
        if (projectLoad.error() == null) {
            projectSettings = projectLoad.settings();
        } else {
            recordError(SettingsScope.PROJECT, projectLoad.error());
        }

        effectiveSettings = globalSettings.merge(projectSettings);
    }

    public void applyOverrides(Settings overrides) {
        Objects.requireNonNull(overrides, "overrides");
        effectiveSettings = effectiveSettings.merge(overrides);
    }

    public List<PackageSource> getPackages() {
        return effectiveSettings.getPackageSources("/packages");
    }

    public void setPackages(List<PackageSource> packages) {
        updateGlobal(settings -> settings.withPackageSources("packages", packages));
    }

    public void setProjectPackages(List<PackageSource> packages) {
        updateProject(settings -> settings.withPackageSources("packages", packages));
    }

    public List<String> getEnabledModels() {
        return effectiveSettings.getStringList("/enabledModels");
    }

    public void setEnabledModels(List<String> patterns) {
        updateGlobal(settings -> settings.withStringList("enabledModels", patterns));
    }

    public void updateGlobal(UnaryOperator<Settings> update) {
        globalSettings = updateScoped(SettingsScope.GLOBAL, update, globalSettings);
        effectiveSettings = globalSettings.merge(projectSettings);
    }

    public void updateProject(UnaryOperator<Settings> update) {
        projectSettings = updateScoped(SettingsScope.PROJECT, update, projectSettings);
        effectiveSettings = globalSettings.merge(projectSettings);
    }

    public List<SettingsError> drainErrors() {
        var drained = List.copyOf(errors);
        errors.clear();
        return drained;
    }

    private Settings updateScoped(SettingsScope scope, UnaryOperator<Settings> update, Settings fallback) {
        Objects.requireNonNull(update, "update");
        try {
            return storage.update(scope, current -> {
                var base = parseSettings(current);
                var next = Objects.requireNonNull(update.apply(base), "update result");
                return new UpdateResult<>(next, serialize(next));
            });
        } catch (IOException | RuntimeException exception) {
            recordError(scope, exception);
            return fallback;
        }
    }

    private void recordError(SettingsScope scope, Exception exception) {
        errors.add(new SettingsError(scope, exception));
    }

    private static LoadResult tryLoad(Storage storage, SettingsScope scope) {
        try {
            return new LoadResult(storage.read(scope, SettingsManager::parseSettings), null);
        } catch (IOException | RuntimeException exception) {
            return new LoadResult(Settings.empty(), exception);
        }
    }

    private static Settings parseSettings(String content) {
        if (content == null || content.isBlank()) {
            return Settings.empty();
        }

        try {
            var json = OBJECT_MAPPER.readTree(content);
            if (json == null || json.isNull()) {
                return Settings.empty();
            }
            if (!(json instanceof ObjectNode objectNode)) {
                throw new IllegalArgumentException("Settings JSON must be an object");
            }
            return SettingsMigrations.migrate(new Settings(objectNode));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to parse settings JSON", exception);
        }
    }

    private static String serialize(Settings settings) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(settings.toObjectNode());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize settings JSON", exception);
        }
    }

    @FunctionalInterface
    interface Reader<T> {
        T apply(String current) throws IOException;
    }

    @FunctionalInterface
    interface Updater<T> {
        UpdateResult<T> apply(String current) throws IOException;
    }

    interface Storage {
        <T> T read(SettingsScope scope, Reader<T> reader) throws IOException;

        <T> T update(SettingsScope scope, Updater<T> updater) throws IOException;
    }

    static final class FileStorage implements Storage {
        private static final ConcurrentHashMap<Path, Object> PROCESS_LOCKS = new ConcurrentHashMap<>();

        private final Path projectSettingsPath;
        private final Path globalSettingsPath;

        private FileStorage(Path projectSettingsPath, Path globalSettingsPath) {
            this.projectSettingsPath = Objects.requireNonNull(projectSettingsPath, "projectSettingsPath");
            this.globalSettingsPath = Objects.requireNonNull(globalSettingsPath, "globalSettingsPath");
        }

        @Override
        public <T> T read(SettingsScope scope, Reader<T> reader) throws IOException {
            Objects.requireNonNull(reader, "reader");
            var path = path(scope);
            if (!Files.exists(path)) {
                return reader.apply(null);
            }
            return withLock(path, false, current -> new UpdateResult<>(reader.apply(current), null));
        }

        @Override
        public <T> T update(SettingsScope scope, Updater<T> updater) throws IOException {
            Objects.requireNonNull(updater, "updater");
            return withLock(path(scope), true, updater);
        }

        private Path path(SettingsScope scope) {
            return scope == SettingsScope.GLOBAL ? globalSettingsPath : projectSettingsPath;
        }

        private <T> T withLock(Path settingsPath, boolean createParent, Updater<T> updater) throws IOException {
            var parent = settingsPath.getParent();
            if (createParent && parent != null) {
                Files.createDirectories(parent);
            }

            var lockPath = settingsPath.resolveSibling(settingsPath.getFileName() + ".lock");
            var mutex = PROCESS_LOCKS.computeIfAbsent(lockPath.toAbsolutePath().normalize(), ignored -> new Object());

            synchronized (mutex) {
                try (
                    var channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    var ignored = channel.lock()
                ) {
                    var current = Files.exists(settingsPath) ? Files.readString(settingsPath, StandardCharsets.UTF_8) : null;
                    var result = updater.apply(current);
                    if (result.serialized() != null) {
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        Files.writeString(settingsPath, result.serialized(), StandardCharsets.UTF_8);
                    }
                    return result.value();
                }
            }
        }
    }

    static final class InMemoryStorage implements Storage {
        private String global;
        private String project;

        private InMemoryStorage(String global, String project) {
            this.global = global;
            this.project = project;
        }

        @Override
        public synchronized <T> T read(SettingsScope scope, Reader<T> reader) throws IOException {
            return reader.apply(scope == SettingsScope.GLOBAL ? global : project);
        }

        @Override
        public synchronized <T> T update(SettingsScope scope, Updater<T> updater) throws IOException {
            var current = scope == SettingsScope.GLOBAL ? global : project;
            var result = updater.apply(current);
            if (result.serialized() != null) {
                if (scope == SettingsScope.GLOBAL) {
                    global = result.serialized();
                } else {
                    project = result.serialized();
                }
            }
            return result.value();
        }
    }

    public enum SettingsScope {
        GLOBAL,
        PROJECT
    }

    public record SettingsError(
        SettingsScope scope,
        Exception error
    ) {
        public SettingsError {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(error, "error");
        }
    }

    record UpdateResult<T>(
        T value,
        String serialized
    ) {
    }

    record LoadResult(
        Settings settings,
        Exception error
    ) {
    }
}
