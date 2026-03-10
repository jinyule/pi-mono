package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.session.SessionInfo;
import dev.pi.tui.VirtualTerminal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

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
            .contains("Second task");

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

    private static SessionInfo session(String fileName, String firstMessage, int messageCount, Instant modified) {
        return new SessionInfo(
            Path.of(fileName),
            fileName.replace(".jsonl", ""),
            "/workspace",
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
