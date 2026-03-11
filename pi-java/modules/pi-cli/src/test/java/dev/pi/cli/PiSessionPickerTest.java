package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.ai.model.Message;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.session.SessionManager;
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

        var sessions = List.of(
            session("first.jsonl", "First task", 3, Instant.now().minusSeconds(60)),
            session("second.jsonl", "Second task", 5, Instant.now().minusSeconds(10))
        );
        Thread.ofVirtual().start(() -> selected[0] = picker.pick(sessions, sessions));

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

        var sessions = List.of(
            session("alpha.jsonl", "Alpha task", 2, Instant.now().minusSeconds(60)),
            session("beta.jsonl", "Beta task", 4, Instant.now().minusSeconds(10))
        );
        Thread.ofVirtual().start(() -> picker.pick(sessions, sessions));

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

        var sessions = List.of(
            session("alpha.jsonl", "Alpha task", 2, Instant.now().minusSeconds(60), "/workspace/api"),
            session("beta.jsonl", "Beta task", 4, Instant.now().minusSeconds(10), "/workspace/web")
        );
        Thread.ofVirtual().start(() -> picker.pick(sessions, sessions));

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

        var sessions = List.of(
            session(first, "First task", 2, Instant.now().minusSeconds(60), "/workspace/api"),
            session(second, "Second task", 4, Instant.now().minusSeconds(10), "/workspace/web")
        );
        Thread.ofVirtual().start(() -> picker.pick(sessions, sessions));

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

    @Test
    void renamesSelectedSessionAfterConfirmation(@TempDir Path tempDir) throws Exception {
        var terminal = new VirtualTerminal(100, 12);
        var picker = new PiSessionPicker(terminal);
        var sessionManager = SessionManager.create(tempDir.resolve("rename-me.jsonl"), "/workspace/api");
        sessionManager.appendMessage(userMessage("hello", 1L));
        sessionManager.appendMessage(assistantMessage("done", 2L));
        sessionManager.appendSessionInfo("Old name");
        var sessions = SessionManager.list(tempDir);

        Thread.ofVirtual().start(() -> picker.pick(sessions, sessions));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Resume session")));

        terminal.sendInput("\u0012");
        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Rename session")));
        terminal.sendInput("New name");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("New name")));

        assertThat(SessionManager.list(tempDir))
            .extracting(SessionInfo::name)
            .contains("New name");
        assertThat(String.join("\n", terminal.getViewport())).contains("New name");

        terminal.sendInput("\u001b");
    }

    @Test
    void togglesBetweenCurrentAndAllScopes() {
        var terminal = new VirtualTerminal(100, 12);
        var picker = new PiSessionPicker(terminal);

        Thread.ofVirtual().start(() -> picker.pick(
            List.of(session("current.jsonl", "Current task", 2, Instant.now().minusSeconds(30), "/workspace/current")),
            List.of(
                session("current.jsonl", "Current task", 2, Instant.now().minusSeconds(30), "/workspace/current"),
                session("global.jsonl", "Global task", 5, Instant.now().minusSeconds(10), "/workspace/other")
            )
        ));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Resume session (Current folder)")));
        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Current task")
            .doesNotContain("Global task");

        terminal.sendInput("\t");
        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Resume session (All)")));

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Global task")
            .contains("/workspace/other");

        terminal.sendInput("\u001b");
    }

    @Test
    void togglesSortModeAndPrioritizesBetterMatches() {
        var terminal = new VirtualTerminal(100, 12);
        var picker = new PiSessionPicker(terminal);

        Thread.ofVirtual().start(() -> picker.pick(
            List.of(
                session("cwd-match.jsonl", "Cwd match", 2, Instant.now().minusSeconds(10), "/workspace/global"),
                session("label-match.jsonl", "Global match", 2, Instant.now().minusSeconds(120), "/workspace/current")
            ),
            List.of()
        ));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Resume session")));

        terminal.sendInput("global");
        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Cwd match")));

        var recentView = String.join("\n", terminal.getViewport());
        assertThat(recentView).contains("Recent");
        assertThat(recentView.indexOf("Cwd match")).isLessThan(recentView.indexOf("Global match"));

        terminal.sendInput("\u0013");
        waitFor(() -> {
            var view = String.join("\n", terminal.getViewport());
            return view.contains("Relevance") && view.indexOf("Global match") < view.indexOf("Cwd match");
        });

        var relevanceView = String.join("\n", terminal.getViewport());
        assertThat(relevanceView).contains("Relevance");
        assertThat(relevanceView.indexOf("Global match")).isLessThan(relevanceView.indexOf("Cwd match"));

        terminal.sendInput("\u001b");
    }

    @Test
    void togglesNamedFilterAndHidesUnnamedSessions() {
        var terminal = new VirtualTerminal(100, 12);
        var picker = new PiSessionPicker(terminal);

        Thread.ofVirtual().start(() -> picker.pick(
            List.of(
                namedSession("named.jsonl", "Project Mercury", "Named fallback", 3, Instant.now().minusSeconds(30), "/workspace/named"),
                session("unnamed.jsonl", "Unnamed task", 2, Instant.now().minusSeconds(20), "/workspace/unnamed"),
                sessionWithRawName("blank.jsonl", "   ", "Whitespace task", 1, Instant.now().minusSeconds(10), "/workspace/blank")
            ),
            List.of()
        ));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Project Mercury")));

        var allView = String.join("\n", terminal.getViewport());
        assertThat(allView)
            .contains("Project Mercury")
            .contains("Unnamed task")
            .contains("Whitespace task")
            .contains("All");

        terminal.sendInput("\u000e");
        waitFor(() -> {
            var view = String.join("\n", terminal.getViewport());
            return view.contains("Named") && !view.contains("Unnamed task") && !view.contains("Whitespace task");
        });

        var namedView = String.join("\n", terminal.getViewport());
        assertThat(namedView)
            .contains("Project Mercury")
            .doesNotContain("Unnamed task")
            .doesNotContain("Whitespace task");

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

    private static SessionInfo namedSession(
        String fileName,
        String name,
        String firstMessage,
        int messageCount,
        Instant modified,
        String cwd
    ) {
        return new SessionInfo(
            Path.of(fileName),
            fileName.replace(".jsonl", ""),
            cwd,
            name,
            null,
            modified.minusSeconds(60),
            modified,
            messageCount,
            firstMessage,
            firstMessage
        );
    }

    private static SessionInfo sessionWithRawName(
        String fileName,
        String name,
        String firstMessage,
        int messageCount,
        Instant modified,
        String cwd
    ) {
        return new SessionInfo(
            Path.of(fileName),
            fileName.replace(".jsonl", ""),
            cwd,
            name,
            null,
            modified.minusSeconds(60),
            modified,
            messageCount,
            firstMessage,
            firstMessage
        );
    }

    private static Message.UserMessage userMessage(String text, long timestamp) {
        return new Message.UserMessage(List.of(new TextContent(text, null)), timestamp);
    }

    private static Message.AssistantMessage assistantMessage(String text, long timestamp) {
        return new Message.AssistantMessage(
            List.of(new TextContent(text, null)),
            "anthropic-messages",
            "anthropic",
            "claude-test",
            new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
            StopReason.STOP,
            null,
            timestamp
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
