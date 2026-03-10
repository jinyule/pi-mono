package dev.pi.extension.spi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public final class ExtensionLoader {
    private final ClassLoader parentClassLoader;

    public ExtensionLoader() {
        this(PiExtension.class.getClassLoader());
    }

    public ExtensionLoader(ClassLoader parentClassLoader) {
        this.parentClassLoader = Objects.requireNonNull(parentClassLoader, "parentClassLoader");
    }

    public ExtensionLoadResult load(Path source) {
        return load(List.of(source));
    }

    public ExtensionLoadResult load(List<Path> sources) {
        Objects.requireNonNull(sources, "sources");
        var extensions = new ArrayList<LoadedExtension>();
        var failures = new ArrayList<ExtensionLoadFailure>();

        for (var source : sources) {
            loadSource(Objects.requireNonNull(source, "source"), extensions, failures);
        }

        return new ExtensionLoadResult(extensions, failures);
    }

    private void loadSource(Path source, List<LoadedExtension> extensions, List<ExtensionLoadFailure> failures) {
        ExtensionClassLoader classLoader = null;
        try {
            classLoader = new ExtensionClassLoader(source, parentClassLoader);
            var serviceLoader = ServiceLoader.load(PiExtension.class, classLoader);
            var iterator = serviceLoader.iterator();
            if (!iterator.hasNext()) {
                classLoader.close();
                failures.add(new ExtensionLoadFailure(source, "No PiExtension services found", null));
                return;
            }

            while (iterator.hasNext()) {
                var extension = iterator.next();
                var api = new CapturingExtensionApi();
                extension.register(api);
                extensions.add(new LoadedExtension(
                    extension.id(),
                    extension.version(),
                    source,
                    extension,
                    classLoader,
                    api.eventHandlers(),
                    api.toolDefinitions(),
                    api.commandDefinitions(),
                    api.shortcutDefinitions(),
                    api.flagDefinitions(),
                    api.flagDefaults(),
                    api.messageRenderers()
                ));
            }
        } catch (ServiceConfigurationError | RuntimeException | IOException exception) {
            closeQuietly(classLoader);
            failures.add(new ExtensionLoadFailure(source, exception.getMessage() == null ? exception.toString() : exception.getMessage(), exception));
        }
    }

    private static void closeQuietly(ExtensionClassLoader classLoader) {
        if (classLoader == null) {
            return;
        }
        try {
            classLoader.close();
        } catch (IOException ignored) {
        }
    }

    private static final class CapturingExtensionApi implements ExtensionApi {
        private final Map<Class<? extends ExtensionEvent>, List<ExtensionHandler<?, ?>>> eventHandlers = new LinkedHashMap<>();
        private final Map<String, ToolDefinition<?>> toolDefinitions = new LinkedHashMap<>();
        private final Map<String, CommandDefinition> commandDefinitions = new LinkedHashMap<>();
        private final Map<String, ShortcutDefinition> shortcutDefinitions = new LinkedHashMap<>();
        private final Map<String, FlagDefinition> flagDefinitions = new LinkedHashMap<>();
        private final Map<String, Object> flagDefaults = new LinkedHashMap<>();
        private final Map<String, MessageRenderer<?, ?>> messageRenderers = new LinkedHashMap<>();

        @Override
        public <E extends ExtensionEvent, R> void on(Class<E> eventType, ExtensionHandler<E, R> handler) {
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(handler, "handler");
            eventHandlers.computeIfAbsent(eventType, ignored -> new ArrayList<>()).add(handler);
        }

        @Override
        public void registerTool(ToolDefinition<?> toolDefinition) {
            Objects.requireNonNull(toolDefinition, "toolDefinition");
            putUnique(toolDefinitions, toolDefinition.name(), toolDefinition, "tool");
        }

        @Override
        public void registerCommand(CommandDefinition commandDefinition) {
            Objects.requireNonNull(commandDefinition, "commandDefinition");
            putUnique(commandDefinitions, commandDefinition.name(), commandDefinition, "command");
        }

        @Override
        public void registerShortcut(ShortcutDefinition shortcutDefinition) {
            Objects.requireNonNull(shortcutDefinition, "shortcutDefinition");
            putUnique(shortcutDefinitions, shortcutDefinition.keyId(), shortcutDefinition, "shortcut");
        }

        @Override
        public void registerFlag(FlagDefinition flagDefinition) {
            Objects.requireNonNull(flagDefinition, "flagDefinition");
            putUnique(flagDefinitions, flagDefinition.name(), flagDefinition, "flag");
            if (flagDefinition.defaultValue() != null && !flagDefaults.containsKey(flagDefinition.name())) {
                flagDefaults.put(flagDefinition.name(), flagDefinition.defaultValue());
            }
        }

        @Override
        public Optional<Object> getFlag(String name) {
            if (name == null || !flagDefinitions.containsKey(name)) {
                return Optional.empty();
            }
            return Optional.ofNullable(flagDefaults.get(name));
        }

        @Override
        public <TMessage, TView> void registerMessageRenderer(String customType, MessageRenderer<TMessage, TView> renderer) {
            if (customType == null || customType.isBlank()) {
                throw new IllegalArgumentException("customType must be a non-empty string");
            }
            Objects.requireNonNull(renderer, "renderer");
            putUnique(messageRenderers, customType, renderer, "message renderer");
        }

        Map<String, ToolDefinition<?>> toolDefinitions() {
            return toolDefinitions;
        }

        Map<Class<? extends ExtensionEvent>, List<ExtensionHandler<?, ?>>> eventHandlers() {
            return eventHandlers;
        }

        Map<String, CommandDefinition> commandDefinitions() {
            return commandDefinitions;
        }

        Map<String, ShortcutDefinition> shortcutDefinitions() {
            return shortcutDefinitions;
        }

        Map<String, FlagDefinition> flagDefinitions() {
            return flagDefinitions;
        }

        Map<String, Object> flagDefaults() {
            return flagDefaults;
        }

        Map<String, MessageRenderer<?, ?>> messageRenderers() {
            return messageRenderers;
        }

        private static <T> void putUnique(Map<String, T> target, String key, T value, String kind) {
            if (target.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate %s registration: %s".formatted(kind, key));
            }
            target.put(key, value);
        }
    }
}
