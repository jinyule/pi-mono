package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.ai.model.Message;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.session.SessionManager;
import dev.pi.session.SessionInfo;
import dev.pi.tui.EditorAction;
import dev.pi.tui.EditorKeybindings;
import dev.pi.tui.VirtualTerminal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiSessionPickerTest {
    @Test
    void rendersSessionsAndSelectsCurrentItem() {
        var terminal = new VirtualTerminal(120, 12);
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
    void omitsSessionFileNameFromDefaultMetadata() {
        var terminal = new VirtualTerminal(100, 12);
        var picker = new PiSessionPicker(terminal);

        var sessions = List.of(session("alpha.jsonl", "Alpha task", 2, Instant.now().minusSeconds(60)));
        Thread.ofVirtual().start(() -> picker.pick(sessions, sessions));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Alpha task")));

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("/workspace")
            .contains("2 msg")
            .doesNotContain("alpha.jsonl");

        terminal.sendInput("\u001b");
    }

    @Test
    void usesAppKeybindingsForNamedFilterToggle() {
        var terminal = new VirtualTerminal(100, 12);
        var picker = new PiSessionPicker(terminal);
        var previousEditor = EditorKeybindings.global();
        var previousApp = PiAppKeybindings.global();
        try {
            EditorKeybindings.setGlobal(new EditorKeybindings(Map.of(EditorAction.SESSION_NAMED_FILTER_TOGGLE, List.of("ctrl+n"))));
            PiAppKeybindings.setGlobal(new PiAppKeybindings(Map.of(PiAppAction.TOGGLE_SESSION_NAMED_FILTER, List.of("alt+n"))));

            var sessions = List.of(
                namedSession("named.jsonl", "Named session", "Named task", 2, Instant.now().minusSeconds(60), "/workspace"),
                session("unnamed.jsonl", "Unnamed task", 4, Instant.now().minusSeconds(10))
            );
            Thread.ofVirtual().start(() -> picker.pick(sessions, sessions));

            waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("named(All)")));

            terminal.sendInput("\u001bn");
            waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("named(Named)")));

            assertThat(String.join("\n", terminal.getViewport()))
                .contains("Named session")
                .doesNotContain("Unnamed task");
        } finally {
            EditorKeybindings.setGlobal(previousEditor);
            PiAppKeybindings.setGlobal(previousApp);
        }

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
        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Delete session?")));
        terminal.sendInput("\r");

        waitFor(() -> !Files.exists(second));

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("First task")
            .doesNotContain("Second task");

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
    void showsCurrentLoadingHeaderBeforeInitialSessionsArrive() {
        var terminal = new VirtualTerminal(120, 12);
        var picker = new PiSessionPicker(terminal);
        var currentSessions = new CompletableFuture<List<SessionInfo>>();

        Thread.ofVirtual().start(() -> picker.pick(progress -> currentSessions.join(), progress -> List.of()));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Loading current")));

        currentSessions.complete(List.of(session("loaded.jsonl", "Loaded task", 1, Instant.now().minusSeconds(5))));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Loaded task")));
        terminal.sendInput("\u001b");
    }

    @Test
    void rendersSearchAndActionHintLines() {
        var terminal = new VirtualTerminal(140, 12);
        var picker = new PiSessionPicker(terminal);

        Thread.ofVirtual().start(() -> picker.pick(
            List.of(session("current.jsonl", "Current task", 2, Instant.now().minusSeconds(30), "/workspace/current")),
            List.of(session("current.jsonl", "Current task", 2, Instant.now().minusSeconds(30), "/workspace/current"))
        ));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("re:<pattern> regex")));

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("tab scope")
            .contains("re:<pattern> regex")
            .contains("\"phrase\" exact")
            .contains("delete")
            .contains("rename");

        terminal.sendInput("\u001b");
    }

    @Test
    void rendersTitleAndScopeSummaryOnSingleHeaderLine() {
        var terminal = new VirtualTerminal(140, 12);
        var picker = new PiSessionPicker(terminal);

        Thread.ofVirtual().start(() -> picker.pick(
            List.of(session("current.jsonl", "Current task", 2, Instant.now().minusSeconds(30), "/workspace/current")),
            List.of(session("current.jsonl", "Current task", 2, Instant.now().minusSeconds(30), "/workspace/current"))
        ));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line ->
            line.contains("Resume session (Current folder)") && line.contains("◉ Current Folder | ○ All")));

        terminal.sendInput("\u001b");
    }

    @Test
    void showsAllLoadingProgressAfterScopeToggle() {
        var terminal = new VirtualTerminal(140, 12);
        var picker = new PiSessionPicker(terminal);
        var progressUpdates = new CopyOnWriteArrayList<String>();
        var allowAllLoad = new CountDownLatch(1);

        Thread.ofVirtual().start(() -> picker.pick(
            progress -> List.of(session("current.jsonl", "Current task", 1, Instant.now().minusSeconds(5))),
            progress -> {
                progress.onProgress(1, 2);
                progressUpdates.add("1/2");
                try {
                    if (!allowAllLoad.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting for all-scope load");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                progress.onProgress(2, 2);
                progressUpdates.add("2/2");
                return List.of(
                    session("current.jsonl", "Current task", 1, Instant.now().minusSeconds(5)),
                    session("global.jsonl", "Global task", 2, Instant.now().minusSeconds(2), "/workspace/global")
                );
            }
        ));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Current task")));

        terminal.sendInput("\t");
        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Loading 1/2")));
        assertThat(progressUpdates).contains("1/2");

        allowAllLoad.countDown();
        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Global task")));
        assertThat(progressUpdates).contains("2/2");
        terminal.sendInput("\u001b");
    }

    @Test
    void togglesSortModeAndPrioritizesBetterMatches() {
        var terminal = new VirtualTerminal(100, 12);
        var picker = new PiSessionPicker(terminal);

        Thread.ofVirtual().start(() -> picker.pick(
            List.of(
                session("late.jsonl", "Later global", 2, Instant.now().minusSeconds(10), "/workspace/current"),
                session("early.jsonl", "Global first", 2, Instant.now().minusSeconds(120), "/workspace/current")
            ),
            List.of()
        ));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Later global")));

        terminal.sendInput("global");
        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Global first")));

        var threadedView = String.join("\n", terminal.getViewport());
        assertThat(threadedView).contains("Threaded");
        assertThat(threadedView.indexOf("Global first")).isLessThan(threadedView.indexOf("Later global"));

        terminal.sendInput("\u0013");
        waitFor(() -> String.join("\n", terminal.getViewport()).contains("Recent"));

        var recentView = String.join("\n", terminal.getViewport());
        assertThat(recentView).contains("Recent");
        assertThat(recentView.indexOf("Later global")).isLessThan(recentView.indexOf("Global first"));

        terminal.sendInput("\u0013");
        waitFor(() -> String.join("\n", terminal.getViewport()).contains("Fuzzy"));

        var relevanceView = String.join("\n", terminal.getViewport());
        assertThat(relevanceView).contains("Fuzzy");

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

    @Test
    void togglesPathDisplayInDescriptions() {
        var terminal = new VirtualTerminal(200, 12);
        var picker = new PiSessionPicker(terminal);
        var session = session("path-toggle.jsonl", "Path toggle", 2, Instant.now().minusSeconds(15), "/workspace/path");

        Thread.ofVirtual().start(() -> picker.pick(List.of(session), List.of()));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Path toggle")));

        var hiddenView = String.join("\n", terminal.getViewport());
        assertThat(hiddenView)
            .contains("path(off)")
            .doesNotContain(session.path().toString());

        terminal.sendInput("\u0010");
        waitFor(() -> {
            var view = String.join("\n", terminal.getViewport());
            return view.contains("path(on)") && view.contains(session.path().toString());
        });

        var shownView = String.join("\n", terminal.getViewport());
        assertThat(shownView)
            .contains("path(on)")
            .contains(session.path().toString());

        terminal.sendInput("\u001b");
    }

    @Test
    void filterAndSortSessionsPrioritizesBetterLabelMatches() {
        var sessions = List.of(
            session("late.jsonl", "Later global", 2, Instant.parse("2026-03-11T07:00:10Z"), "/workspace/current"),
            session("early.jsonl", "Global first", 2, Instant.parse("2026-03-11T07:00:00Z"), "/workspace/current")
        );

        assertThat(PiSessionPicker.filterAndSortSessions(sessions, "global", PiSessionPicker.SortMode.RECENT, PiSessionPicker.NameFilter.ALL))
            .extracting(SessionInfo::firstMessage)
            .containsExactly("Later global", "Global first");

        assertThat(PiSessionPicker.filterAndSortSessions(sessions, "global", PiSessionPicker.SortMode.RELEVANCE, PiSessionPicker.NameFilter.ALL))
            .extracting(SessionInfo::firstMessage)
            .containsExactly("Global first", "Later global");
    }

    @Test
    void filterAndSortSessionsMatchesQuotedPhraseAcrossWhitespace() {
        var sessions = List.of(
            sessionWithTranscript("a.jsonl", "Alpha", "node\n\n   cve was discussed", Instant.parse("2026-03-11T07:00:00Z")),
            sessionWithTranscript("b.jsonl", "Beta", "node something else", Instant.parse("2026-03-11T07:00:10Z"))
        );

        assertThat(PiSessionPicker.filterAndSortSessions(sessions, "\"node cve\"", PiSessionPicker.SortMode.RECENT, PiSessionPicker.NameFilter.ALL))
            .extracting(SessionInfo::path)
            .containsExactly(sessions.getFirst().path());
    }

    @Test
    void filterAndSortSessionsSupportsCaseInsensitiveRegex() {
        var sessions = List.of(
            sessionWithTranscript("a.jsonl", "Alpha", "Brave is great", Instant.parse("2026-03-11T07:00:00Z")),
            sessionWithTranscript("b.jsonl", "Beta", "bravery is not the same", Instant.parse("2026-03-11T07:00:10Z"))
        );

        assertThat(PiSessionPicker.filterAndSortSessions(sessions, "re:\\bbrave\\b", PiSessionPicker.SortMode.RECENT, PiSessionPicker.NameFilter.ALL))
            .extracting(SessionInfo::path)
            .containsExactly(sessions.getFirst().path());
    }

    @Test
    void filterAndSortSessionsSupportsFuzzySubsequenceSearch() {
        var sessions = List.of(
            sessionWithTranscript("x.jsonl", "Alpha", "alpha beta gamma", Instant.parse("2026-03-11T07:00:00Z")),
            sessionWithTranscript("y.jsonl", "Beta", "delta epsilon", Instant.parse("2026-03-11T07:00:10Z"))
        );

        assertThat(PiSessionPicker.filterAndSortSessions(sessions, "abg", PiSessionPicker.SortMode.RECENT, PiSessionPicker.NameFilter.ALL))
            .extracting(SessionInfo::path)
            .containsExactly(sessions.getFirst().path());
    }

    @Test
    void filterAndSortSessionsFavorsConsecutiveFuzzyMatches() {
        var sessions = List.of(
            sessionWithTranscript("exact.jsonl", "Exact", "foobar", Instant.parse("2026-03-11T07:00:00Z")),
            sessionWithTranscript("scattered.jsonl", "Scattered", "f_o_o_bar", Instant.parse("2026-03-11T07:00:10Z"))
        );

        assertThat(PiSessionPicker.filterAndSortSessions(sessions, "foo", PiSessionPicker.SortMode.RELEVANCE, PiSessionPicker.NameFilter.ALL))
            .extracting(SessionInfo::path)
            .containsExactly(
                sessions.getFirst().path(),
                sessions.getLast().path()
            );
    }

    @Test
    void filterAndSortSessionsFavorsWordBoundaryFuzzyMatches() {
        var sessions = List.of(
            sessionWithTranscript("boundary.jsonl", "Boundary", "foo-bar", Instant.parse("2026-03-11T07:00:00Z")),
            sessionWithTranscript("inline.jsonl", "Inline", "afbx", Instant.parse("2026-03-11T07:00:10Z"))
        );

        assertThat(PiSessionPicker.filterAndSortSessions(sessions, "fb", PiSessionPicker.SortMode.RELEVANCE, PiSessionPicker.NameFilter.ALL))
            .extracting(SessionInfo::path)
            .containsExactly(
                sessions.getFirst().path(),
                sessions.getLast().path()
            );
    }

    @Test
    void filterAndSortSessionsSupportsSwappedAlphaNumericFuzzyMatches() {
        var sessions = List.of(
            sessionWithTranscript("match.jsonl", "Match", "gpt-5.2-codex", Instant.parse("2026-03-11T07:00:00Z")),
            sessionWithTranscript("miss.jsonl", "Miss", "claude sonnet", Instant.parse("2026-03-11T07:00:10Z"))
        );

        assertThat(PiSessionPicker.filterAndSortSessions(sessions, "codex52", PiSessionPicker.SortMode.RECENT, PiSessionPicker.NameFilter.ALL))
            .extracting(SessionInfo::path)
            .containsExactly(sessions.getFirst().path());
    }

    @Test
    void filterAndSortSessionsDoesNotSearchPathText() {
        var sessions = List.of(
            new SessionInfo(
                Path.of("sessions", "path-only", "alpha.jsonl"),
                "alpha",
                "/workspace/api",
                null,
                null,
                Instant.parse("2026-03-11T06:59:00Z"),
                Instant.parse("2026-03-11T07:00:00Z"),
                2,
                "Alpha task",
                "normal transcript"
            ),
            sessionWithTranscript("beta.jsonl", "Beta", "normal transcript", Instant.parse("2026-03-11T07:00:10Z"))
        );

        assertThat(PiSessionPicker.filterAndSortSessions(sessions, "path-only", PiSessionPicker.SortMode.RECENT, PiSessionPicker.NameFilter.ALL))
            .isEmpty();
    }

    @Test
    void togglesSortModeIntoThreadedTreeOrder() {
        var terminal = new VirtualTerminal(140, 12);
        var picker = new PiSessionPicker(terminal);
        var root = sessionWithParent("root.jsonl", null, "Root task", 3, Instant.parse("2026-03-11T07:00:20Z"), "/workspace/root");
        var child = sessionWithParent("child.jsonl", root.path().toString(), "Child task", 2, Instant.parse("2026-03-11T07:00:10Z"), "/workspace/root");
        var sibling = sessionWithParent("sibling.jsonl", null, "Sibling task", 1, Instant.parse("2026-03-11T07:00:00Z"), "/workspace/root");

        Thread.ofVirtual().start(() -> picker.pick(List.of(sibling, child, root), List.of()));

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("sort(Threaded)")));

        var threadedView = String.join("\n", terminal.getViewport());
        assertThat(threadedView)
            .contains("Root task")
            .contains("└─ Child task")
            .contains("Sibling task");

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

    private static SessionInfo sessionWithParent(
        String fileName,
        String parentSessionPath,
        String firstMessage,
        int messageCount,
        Instant modified,
        String cwd
    ) {
        return new SessionInfo(
            Path.of(fileName),
            fileName.replace(".jsonl", ""),
            cwd,
            null,
            parentSessionPath,
            modified.minusSeconds(60),
            modified,
            messageCount,
            firstMessage,
            firstMessage
        );
    }

    private static SessionInfo sessionWithTranscript(String fileName, String firstMessage, String allMessagesText, Instant modified) {
        return new SessionInfo(
            Path.of(fileName),
            fileName.replace(".jsonl", ""),
            "/workspace",
            null,
            null,
            modified.minusSeconds(60),
            modified,
            1,
            firstMessage,
            allMessagesText
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
