package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiGitBranchWatcherTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void notifiesWhenHeadChanges() throws IOException, InterruptedException {
        var project = tempDir.resolve("project");
        var gitDir = project.resolve(".git");
        Files.createDirectories(gitDir);
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main\n", StandardCharsets.UTF_8);

        var notifications = new AtomicInteger();
        var changed = new CountDownLatch(1);
        try (var watcher = PiGitBranchWatcher.start(project.toString(), () -> {
            notifications.incrementAndGet();
            changed.countDown();
        })) {
            assertThat(watcher).isNotNull();

            Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/feature/footer\n", StandardCharsets.UTF_8);

            assertThat(changed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(notifications.get()).isGreaterThanOrEqualTo(1);
        }
    }
}
