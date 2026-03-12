package dev.pi.cli;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchService;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class PiGitBranchWatcher implements AutoCloseable {
    private final WatchService watchService;
    private final Thread thread;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private PiGitBranchWatcher(WatchService watchService, Thread thread) {
        this.watchService = watchService;
        this.thread = thread;
    }

    static PiGitBranchWatcher start(String cwd, Runnable onChange) {
        Objects.requireNonNull(onChange, "onChange");
        try {
            var headPath = PiGitBranchResolver.headPath(cwd);
            if (headPath == null) {
                return null;
            }
            var parent = headPath.getParent();
            if (parent == null) {
                return null;
            }
            var watchService = FileSystems.getDefault().newWatchService();
            parent.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            );
            var watcher = new PiGitBranchWatcher(
                watchService,
                new Thread(() -> watchLoop(watchService, parent, onChange), "pi-git-branch-watcher")
            );
            watcher.thread.setDaemon(true);
            watcher.thread.start();
            return watcher;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            watchService.close();
        } catch (IOException ignored) {
        }
        thread.interrupt();
    }

    private static void watchLoop(WatchService watchService, Path directory, Runnable onChange) {
        try {
            while (true) {
                var key = watchService.take();
                var shouldNotify = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (!(event.context() instanceof Path path)) {
                        continue;
                    }
                    if ("HEAD".equals(path.getFileName().toString())) {
                        shouldNotify = true;
                    }
                }
                if (!key.reset()) {
                    return;
                }
                if (shouldNotify) {
                    onChange.run();
                }
            }
        } catch (ClosedWatchServiceException | InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
