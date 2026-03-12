package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentMessage;
import dev.pi.agent.runtime.AgentMessages;
import dev.pi.agent.runtime.AgentState;
import dev.pi.agent.runtime.AgentTool;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.ThinkingLevel;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.Subscription;
import dev.pi.session.SessionEntry;
import dev.pi.session.SessionManager;
import dev.pi.session.SessionTreeNode;
import dev.pi.tui.InputHandler;
import dev.pi.tui.Terminal;
import dev.pi.tui.VirtualTerminal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class PiInteractiveModeTest {
    @Test
    void rendersHeaderAndSubmitsPromptThroughTui() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(60, 12);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("pi-java interactive")
            .contains("model: openai/test-model")
            .contains("Ready")
            .contains(">");

        terminal.sendInput("Hello");
        terminal.sendInput("\r");

        assertThat(session.prompts).containsExactly("Hello");
        assertThat(String.join("\n", terminal.getViewport()))
            .contains("You: Hello")
            .contains("Assistant: Ack: Hello");

        mode.stop();
    }

    @Test
    void handlesCopySlashCommand() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(60, 12);
        var copied = new StringBuilder();
        var mode = new PiInteractiveMode(session, terminal, new PiCopyCommand(session, copied::append));

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");
        terminal.sendInput("/copy");
        terminal.sendInput("\r");

        assertThat(session.prompts).containsExactly("Hello");
        assertThat(copied.toString()).isEqualTo("Ack: Hello");
        assertThat(String.join("\n", terminal.getViewport())).contains("Copied last agent message to clipboard");

        mode.stop();
    }

    @Test
    void rendersFooterUsageAndModelInfo() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 14);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("↑1")
            .contains("↓1")
            .contains("$0.000")
            .contains("test-model");

        mode.stop();
    }

    @Test
    void stylesFooterStatsAndModelSummaryWithAnsiHierarchy() {
        var session = new FakeSession();
        var terminal = new RecordingTerminal(80, 14);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");

        waitFor(() -> terminal.output().contains("test-model"));

        assertThat(terminal.output())
            .contains("\u001b[90m↑1 ↓1 $0.000\u001b[0m")
            .contains("\u001b[1mtest-model\u001b[0m");

        mode.stop();
    }

    @Test
    void rendersReasoningThinkingLevelInFooter() {
        var session = new FakeSession().withReasoningModel("reasoning-model", ThinkingLevel.HIGH);
        var terminal = new RecordingTerminal(100, 14);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        waitFor(() -> terminal.output().contains("reasoning-model"));

        assertThat(terminal.output())
            .contains("\u001b[1mreasoning-model\u001b[0m")
            .contains("\u001b[90m • high\u001b[0m");

        mode.stop();
    }

    @Test
    void rendersProviderPrefixInFooterWhenWidthAllows() {
        var session = new FakeSession();
        var terminal = new RecordingTerminal(100, 14);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        waitFor(() -> terminal.output().contains("test-model"));

        assertThat(terminal.output()).contains("\u001b[90mopenai/\u001b[0m\u001b[1mtest-model\u001b[0m");

        mode.stop();
    }

    @Test
    void handlesTreeSlashCommand() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");
        terminal.sendInput("/tree");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Navigate session tree")));

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Navigate session tree")
            .contains("user: Hello")
            .contains("assistant: Ack: Hello");

        terminal.sendInput("\u001b[A");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Moved to selected tree entry")));
        terminal.sendInput("!");
        terminal.sendInput("\r");

        assertThat(session.prompts).containsExactly("Hello", "!Hello");
        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Moved to selected tree entry")
            .contains("Assistant: Ack: !Hello")
            .doesNotContain("You: Hello\n\nAssistant: Ack: Hello");

        mode.stop();
    }

    @Test
    void usesAppKeybindingForTreeOverlay() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.TREE, java.util.List.of("alt+t"))));

            mode.start();
            terminal.sendInput("Hello");
            terminal.sendInput("\r");
            terminal.sendInput("\u001bt");

            waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Navigate session tree")));
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void handlesForkSlashCommand() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");
        var originalSessionId = session.sessionId();
        terminal.sendInput("/fork");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Fork from previous message")));

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Fork from previous message")
            .contains("Hello");

        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Forked to new session")));
        terminal.sendInput("!");
        terminal.sendInput("\r");

        assertThat(session.prompts).containsExactly("Hello", "!Hello");
        assertThat(session.sessionId()).isNotEqualTo(originalSessionId);
        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Forked to new session")
            .contains("Assistant: Ack: !Hello")
            .doesNotContain("You: Hello\n\nAssistant: Ack: Hello");

        mode.stop();
    }

    @Test
    void usesAppKeybindingForForkOverlay() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.FORK, java.util.List.of("alt+f"))));

            mode.start();
            terminal.sendInput("Hello");
            terminal.sendInput("\r");
            terminal.sendInput("\u001bf");

            waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Fork from previous message")));
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void handlesCompactSlashCommand() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(100, 20);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");
        terminal.sendInput("Second");
        terminal.sendInput("\r");
        terminal.sendInput("/compact Focus on latest work");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Compacted context")));

        assertThat(session.prompts).containsExactly("Hello", "Second");
        var compactedSummary = (AgentMessage.UserMessage) session.state().messages().getFirst();
        assertThat(((TextContent) compactedSummary.content().getFirst()).text())
            .contains("Focus on latest work")
            .contains("[User]: Hello")
            .contains("[Assistant]: Ack: Hello");
        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Compacted context");

        mode.stop();
    }

    @Test
    void handlesReloadSlashCommand() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("/reload");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Reloaded settings, instruction resources, and extensions")));

        assertThat(session.reloadCount).isEqualTo(1);
        assertThat(session.state().systemPrompt()).isEqualTo("Reloaded system prompt");
        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Reloaded settings, instruction resources, and extensions");

        mode.stop();
    }

    @Test
    void handlesReloadSlashCommandWarnings() {
        var session = new FakeSession();
        session.reloadWarnings = List.of("reload-plugin.jar: broken extension");
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("/reload");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Reloaded with 1 warning")));

        assertThat(String.join("\n", terminal.getViewport())).contains("Reloaded with 1 warning");

        mode.stop();
    }

    @Test
    void ctrlDOnEmptyInputStopsMode() throws Exception {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var stopped = new CompletableFuture<Void>();
        mode.setOnStop(() -> stopped.complete(null));

        mode.start();
        terminal.sendInput("\u0004");

        stopped.get(2, TimeUnit.SECONDS);
    }

    @Test
    void usesAppKeybindingForInterrupt() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.INTERRUPT, java.util.List.of("alt+x"))));

            mode.start();
            terminal.sendInput("\u001b");
            assertThat(session.abortCount).isZero();

            terminal.sendInput("\u001bx");
            assertThat(session.abortCount).isEqualTo(1);
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void usesAppKeybindingForResume() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.RESUME, java.util.List.of("alt+u"))));

            mode.start();
            terminal.sendInput("\u001bu");

            waitFor(() -> session.resumeCount == 1);
            assertThat(String.join("\n", terminal.getViewport())).contains("Resumed session");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    private static final class FakeSession implements PiInteractiveSession {
        private final List<String> prompts = new ArrayList<>();
        private final CopyOnWriteArrayList<Consumer<AgentState>> stateListeners = new CopyOnWriteArrayList<>();
        private final SessionManager sessionManager = SessionManager.inMemory("/workspace");
        private int reloadCount;
        private int abortCount;
        private int resumeCount;
        private List<String> reloadWarnings = List.of();
        private AgentState state = new AgentState(
            "",
            new Model(
                "test-model",
                "Test Model",
                "openai-responses",
                "openai",
                "https://example.com",
                false,
                List.of("text"),
                new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
                128_000,
                8_192,
                null,
                null
            ),
            null,
            List.<AgentTool<?>>of(),
            List.of(),
            false,
            null,
            Set.of(),
            null
        );

        @Override
        public String sessionId() {
            return sessionManager.sessionId();
        }

        @Override
        public AgentState state() {
            return state;
        }

        @Override
        public Subscription subscribe(Consumer<AgentEvent> listener) {
            return () -> {
            };
        }

        @Override
        public Subscription subscribeState(Consumer<AgentState> listener) {
            listener.accept(state);
            stateListeners.add(listener);
            return () -> stateListeners.remove(listener);
        }

        @Override
        public CompletionStage<Void> prompt(String text) {
            prompts.add(text);
            try {
                sessionManager.appendMessage(new Message.UserMessage(List.of(new TextContent(text, null)), 1L));
                sessionManager.appendMessage(new Message.AssistantMessage(
                    List.of(new TextContent("Ack: " + text, null)),
                    state.model().api(),
                    state.model().provider(),
                    state.model().id(),
                    new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                    dev.pi.ai.model.StopReason.STOP,
                    null,
                    2L
                ));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            syncState();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> resume() {
            resumeCount += 1;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> waitForIdle() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void abort() {
            abortCount += 1;
        }

        @Override
        public String leafId() {
            return sessionManager.leafId();
        }

        @Override
        public List<SessionTreeNode> tree() {
            return sessionManager.tree();
        }

        @Override
        public TreeNavigationResult navigateTree(String targetId) {
            var entry = sessionManager.entry(targetId);
            if (entry == null) {
                throw new IllegalArgumentException("Unknown session entry: " + targetId);
            }
            var editorText = switch (entry) {
                case SessionEntry.MessageEntry messageEntry when "user".equals(messageEntry.message().path("role").asText()) -> {
                    sessionManager.navigate(messageEntry.parentId());
                    yield extractUserText(messageEntry);
                }
                default -> {
                    sessionManager.navigate(targetId);
                    yield null;
                }
            };
            syncState();
            return new TreeNavigationResult(sessionManager.leafId(), editorText);
        }

        @Override
        public List<ForkMessage> forkMessages() {
            var messages = new ArrayList<ForkMessage>();
            for (var entry : sessionManager.entries()) {
                if (!(entry instanceof SessionEntry.MessageEntry messageEntry)) {
                    continue;
                }
                if (!"user".equals(messageEntry.message().path("role").asText())) {
                    continue;
                }
                var text = extractUserText(messageEntry);
                if (!text.isBlank()) {
                    messages.add(new ForkMessage(entry.id(), text));
                }
            }
            return List.copyOf(messages);
        }

        @Override
        public ForkResult fork(String entryId) {
            var entry = sessionManager.entry(entryId);
            if (!(entry instanceof SessionEntry.MessageEntry messageEntry) || !"user".equals(messageEntry.message().path("role").asText())) {
                throw new IllegalArgumentException("Invalid entry ID for forking");
            }
            try {
                sessionManager.createBranchedSession(messageEntry.parentId());
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            syncState();
            return new ForkResult(extractUserText(messageEntry), sessionManager.sessionId());
        }

        @Override
        public CompactionResult compact(String customInstructions) {
            try {
                var result = PiCompactor.compact(sessionManager, customInstructions);
                syncState();
                return result;
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        @Override
        public ReloadResult reload() {
            reloadCount++;
            state = state.withSystemPrompt("Reloaded system prompt");
            emitState();
            return new ReloadResult(List.of(), List.of(), reloadWarnings);
        }

        private void emitState() {
            for (var listener : stateListeners) {
                listener.accept(state);
            }
        }

        private void syncState() {
            var messages = sessionManager.buildSessionContext().messages().stream()
                .map(AgentMessages::fromLlmMessage)
                .toList();
            state = state.withMessages(messages);
            emitState();
        }

        private FakeSession withReasoningModel(String modelId, ThinkingLevel thinkingLevel) {
            state = state.withModel(new Model(
                modelId,
                modelId,
                "openai-responses",
                "openai",
                "https://example.com",
                true,
                List.of("text"),
                new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
                128_000,
                8_192,
                null,
                null
            )).withThinkingLevel(thinkingLevel);
            emitState();
            return this;
        }

        private static String extractUserText(SessionEntry.MessageEntry entry) {
            var parts = new ArrayList<String>();
            for (var item : entry.message().path("content")) {
                if ("text".equals(item.path("type").asText())) {
                    var text = item.path("text").asText("");
                    if (!text.isBlank()) {
                        parts.add(text);
                    }
                }
            }
            return String.join("\n", parts);
        }
    }

    private static final class RecordingTerminal implements Terminal {
        private final int columns;
        private final int rows;
        private final List<String> writes = new CopyOnWriteArrayList<>();
        private volatile InputHandler inputHandler;

        private RecordingTerminal(int columns, int rows) {
            this.columns = columns;
            this.rows = rows;
        }

        @Override
        public void start(InputHandler onInput, Runnable onResize) {
            inputHandler = onInput;
        }

        @Override
        public void stop() {
            inputHandler = null;
        }

        @Override
        public void write(String data) {
            writes.add(data);
        }

        @Override
        public int columns() {
            return columns;
        }

        @Override
        public int rows() {
            return rows;
        }

        private void sendInput(String data) {
            if (inputHandler != null) {
                inputHandler.onInput(data);
            }
        }

        private String output() {
            return String.join("", writes);
        }
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
