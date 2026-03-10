package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentMessage;
import dev.pi.agent.runtime.AgentMessages;
import dev.pi.agent.runtime.AgentState;
import dev.pi.agent.runtime.AgentTool;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.Subscription;
import dev.pi.session.SessionEntry;
import dev.pi.session.SessionManager;
import dev.pi.session.SessionTreeNode;
import dev.pi.tui.VirtualTerminal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
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

    private static final class FakeSession implements PiInteractiveSession {
        private final List<String> prompts = new ArrayList<>();
        private final CopyOnWriteArrayList<Consumer<AgentState>> stateListeners = new CopyOnWriteArrayList<>();
        private final SessionManager sessionManager = SessionManager.inMemory("/workspace");
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
            return "session-1";
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
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> waitForIdle() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void abort() {
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
