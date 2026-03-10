package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.session.SessionInfo;
import dev.pi.tui.VirtualTerminal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiSessionPickerTest {
    @Test
    void rendersSessionsAndSelectsCurrentItem() {
        var terminal = new VirtualTerminal(80, 12);
        var picker = new PiSessionPicker(terminal);
        Path[] selected = new Path[1];

        Thread.ofVirtual().start(() -> selected[0] = picker.pick(List.of(
            session("first.jsonl", "First task", 3, Instant.now().minusSeconds(60)),
            session("second.jsonl", "Second task", 5, Instant.now().minusSeconds(10))
        )));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Resume session")));

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Resume session")
            .contains("First task")
            .contains("Second task")
            .contains("/workspace");

        terminal.sendInput("\u001b[B");
        terminal.sendInput("\r");

        waitFor(() -> selected[0] != null);

        assertThat(selected[0]).isEqualTo(Path.of("second.jsonl").toAbsolutePath().normalize());
    }

    @Test
    void filtersSessionsFromSearchInput() {
        var terminal = new VirtualTerminal(80, 12);
        var picker = new PiSessionPicker(terminal);

        Thread.ofVirtual().start(() -> picker.pick(List.of(
            session("alpha.jsonl", "Alpha task", 2, Instant.now().minusSeconds(60)),
            session("beta.jsonl", "Beta task", 4, Instant.now().minusSeconds(10))
        )));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Alpha task")));

        terminal.sendInput("Be");
        waitFor(() -> terminal.getViewport().stream().noneMatch(line -> line.contains("Alpha task")));

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Beta task")
            .doesNotContain("Alpha task");

        terminal.sendInput("\u001b");
    }

    @Test
    void filtersSessionsByCwdTokens() {
        var terminal = new VirtualTerminal(100, 12);
        var picker = new PiSessionPicker(terminal);

        Thread.ofVirtual().start(() -> picker.pick(List.of(
            session("alpha.jsonl", "Alpha task", 2, Instant.now().minusSeconds(60), "/workspace/api"),
            session("beta.jsonl", "Beta task", 4, Instant.now().minusSeconds(10), "/workspace/web")
        )));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Alpha task")));

        terminal.sendInput("workspace web");
        waitFor(() -> terminal.getViewport().stream().noneMatch(line -> line.contains("Alpha task")));

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Beta task")
            .contains("/workspace/web")
            .doesNotContain("Alpha task");

        terminal.sendInput("\u001b");
    }

    @Test
    void deletesSelectedSessionAfterConfirmation(@TempDir Path tempDir) throws Exception {
        var terminal = new VirtualTerminal(100, 12);
        var picker = new PiSessionPicker(terminal);
        var first = tempDir.resolve("first.jsonl");
        var second = tempDir.resolve("second.jsonl");
        Files.writeString(first, "{}", StandardCharsets.UTF_8);
        Files.writeString(second, "{}", StandardCharsets.UTF_8);

        Thread.ofVirtual().start(() -> picker.pick(List.of(
            session(first, "First task", 2, Instant.now().minusSeconds(60), "/workspace/api"),
            session(second, "Second task", 4, Instant.now().minusSeconds(10), "/workspace/web")
        )));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("First task")));

        terminal.sendInput("\u0004");
        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Delete selected session?")));
        terminal.sendInput("\r");

        waitFor(() -> !Files.exists(first));

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Second task")
            .doesNotContain("First task");

        terminal.sendInput("\u001b");
    }

    private static SessionInfo session(String fileName, String firstMessage, int messageCount, Instant modified) {
        return session(fileName, firstMessage, messageCount, modified, "/workspace");
    }

    private static SessionInfo session(String fileName, String firstMessage, int messageCount, Instant modified, String cwd) {
        return new SessionInfo(
            Path.of(fileName),
            fileName.replace(".jsonl", ""),
            cwd,
            null,
            null,
            modified.minusSeconds(60),
            modified,
            messageCount,
            firstMessage,
            firstMessage
        );
    }

    private static SessionInfo session(Path path, String firstMessage, int messageCount, Instant modified, String cwd) {
        return new SessionInfo(
            path,
            path.getFileName().toString().replace(".jsonl", ""),
            cwd,
            null,
            null,
            modified.minusSeconds(60),
            modified,
            messageCount,
            firstMessage,
            firstMessage
        );
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) {
        var deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
        throw new AssertionError("condition not met");
    }
}
