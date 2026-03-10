package dev.pi.extension.spi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class ExtensionResourceDiscovery {
    private final List<LoadedExtension> extensions;

    public ExtensionResourceDiscovery(List<LoadedExtension> extensions) {
        this.extensions = List.copyOf(Objects.requireNonNull(extensions, "extensions"));
    }

    public boolean hasHandlers() {
        for (var extension : extensions) {
            var handlers = extension.eventHandlers().get(ResourcesDiscoverEvent.class);
            if (handlers != null && !handlers.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public CompletionStage<ExtensionResourceDiscoveryResult> discover(
        ResourcesDiscoverEvent event,
        ExtensionContext context
    ) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(context, "context");

        var skillPaths = new LinkedHashMap<Path, ExtensionResourcePath>();
        var promptPaths = new LinkedHashMap<Path, ExtensionResourcePath>();
        var themePaths = new LinkedHashMap<Path, ExtensionResourcePath>();
        var failures = new java.util.ArrayList<ExtensionEventFailure>();

        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (var extension : extensions) {
            var handlers = extension.eventHandlers().getOrDefault(ResourcesDiscoverEvent.class, List.of());
            for (var handler : handlers) {
                chain = chain.thenCompose(ignored -> invoke(
                    extension,
                    handler,
                    event,
                    context,
                    skillPaths,
                    promptPaths,
                    themePaths,
                    failures
                ));
            }
        }

        return chain.thenApply(ignored -> new ExtensionResourceDiscoveryResult(
            List.copyOf(skillPaths.values()),
            List.copyOf(promptPaths.values()),
            List.copyOf(themePaths.values()),
            failures
        ));
    }

    @SuppressWarnings("unchecked")
    private static CompletionStage<Void> invoke(
        LoadedExtension extension,
        ExtensionHandler<?, ?> handler,
        ResourcesDiscoverEvent event,
        ExtensionContext context,
        LinkedHashMap<Path, ExtensionResourcePath> skillPaths,
        LinkedHashMap<Path, ExtensionResourcePath> promptPaths,
        LinkedHashMap<Path, ExtensionResourcePath> themePaths,
        List<ExtensionEventFailure> failures
    ) {
        try {
            var typedHandler = (ExtensionHandler<ResourcesDiscoverEvent, ResourcesDiscoverResult>) handler;
            return typedHandler.handle(event, context)
                .handle((result, error) -> {
                    if (error != null) {
                        failures.add(new ExtensionEventFailure(
                            extension.id(),
                            extension.source(),
                            event.type(),
                            unwrap(error)
                        ));
                        return null;
                    }
                    if (result != null) {
                        var baseDir = baseDirFor(extension, event.cwd());
                        collectEntries(result.skillPaths(), extension, baseDir, event.type(), skillPaths, failures);
                        collectEntries(result.promptPaths(), extension, baseDir, event.type(), promptPaths, failures);
                        collectEntries(result.themePaths(), extension, baseDir, event.type(), themePaths, failures);
                    }
                    return null;
                });
        } catch (RuntimeException exception) {
            failures.add(new ExtensionEventFailure(extension.id(), extension.source(), event.type(), exception));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static void collectEntries(
        List<String> declaredPaths,
        LoadedExtension extension,
        Path baseDir,
        String eventType,
        LinkedHashMap<Path, ExtensionResourcePath> target,
        List<ExtensionEventFailure> failures
    ) {
        for (var declaredPath : declaredPaths) {
            try {
                var resolvedPath = resolvePath(baseDir, declaredPath);
                target.putIfAbsent(
                    resolvedPath,
                    new ExtensionResourcePath(
                        declaredPath,
                        resolvedPath,
                        extension.id(),
                        extension.source(),
                        baseDir
                    )
                );
            } catch (RuntimeException exception) {
                failures.add(new ExtensionEventFailure(extension.id(), extension.source(), eventType, exception));
            }
        }
    }

    private static Path resolvePath(Path baseDir, String declaredPath) {
        if (declaredPath == null || declaredPath.isBlank()) {
            throw new IllegalArgumentException("resource path must be a non-empty string");
        }

        var trimmed = declaredPath.trim();
        var expanded = expandHome(trimmed);
        var path = Path.of(expanded);
        if (path.isAbsolute()) {
            return path.toAbsolutePath().normalize();
        }
        return baseDir.resolve(path).toAbsolutePath().normalize();
    }

    private static String expandHome(String input) {
        if ("~".equals(input)) {
            return System.getProperty("user.home");
        }
        if (input.startsWith("~/")) {
            return Path.of(System.getProperty("user.home"), input.substring(2)).toString();
        }
        if (input.startsWith("~")) {
            return Path.of(System.getProperty("user.home"), input.substring(1)).toString();
        }
        return input;
    }

    private static Path baseDirFor(LoadedExtension extension, Path fallbackCwd) {
        var source = extension.source().toAbsolutePath().normalize();
        if (Files.isDirectory(source)) {
            return source;
        }
        var parent = source.getParent();
        if (parent != null) {
            return parent.toAbsolutePath().normalize();
        }
        return fallbackCwd.toAbsolutePath().normalize();
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof java.util.concurrent.CompletionException || throwable instanceof java.util.concurrent.ExecutionException) {
            return throwable.getCause() == null ? throwable : throwable.getCause();
        }
        return throwable;
    }
}
