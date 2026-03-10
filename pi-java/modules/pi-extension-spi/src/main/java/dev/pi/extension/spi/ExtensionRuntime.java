package dev.pi.extension.spi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ExtensionRuntime implements AutoCloseable {
    private final ExtensionLoader loader;
    private final List<Path> sources;

    private ExtensionLoadResult currentLoadResult;
    private ExtensionRuntimeSnapshot snapshot;

    public ExtensionRuntime(List<Path> sources) {
        this(new ExtensionLoader(), sources);
    }

    ExtensionRuntime(ExtensionLoader loader, List<Path> sources) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        this.currentLoadResult = this.loader.load(this.sources);
        this.snapshot = snapshotFor(currentLoadResult);
    }

    public synchronized List<Path> sources() {
        return sources;
    }

    public synchronized ExtensionRuntimeSnapshot snapshot() {
        return snapshot;
    }

    public synchronized ExtensionRuntimeSnapshot reload() throws IOException {
        var nextLoadResult = loader.load(sources);
        var nextSnapshot = snapshotFor(nextLoadResult);
        var previousLoadResult = currentLoadResult;

        currentLoadResult = nextLoadResult;
        snapshot = nextSnapshot;

        if (previousLoadResult != null) {
            previousLoadResult.close();
        }

        return nextSnapshot;
    }

    @Override
    public synchronized void close() throws IOException {
        var previousLoadResult = currentLoadResult;
        currentLoadResult = null;
        snapshot = ExtensionRuntimeSnapshot.empty();

        if (previousLoadResult != null) {
            previousLoadResult.close();
        }
    }

    private static ExtensionRuntimeSnapshot snapshotFor(ExtensionLoadResult loadResult) {
        return new ExtensionRuntimeSnapshot(
            loadResult.extensions(),
            loadResult.failures(),
            new ExtensionEventBus(loadResult.extensions()),
            new ExtensionResourceDiscovery(loadResult.extensions())
        );
    }
}
