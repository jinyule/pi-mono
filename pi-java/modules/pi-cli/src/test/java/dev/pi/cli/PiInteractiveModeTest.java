package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentMessage;
import dev.pi.agent.runtime.AgentState;
import dev.pi.agent.runtime.AgentTool;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.Subscription;
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

    private static final class FakeSession implements PiInteractiveSession {
        private final List<String> prompts = new ArrayList<>();
        private final CopyOnWriteArrayList<Consumer<AgentState>> stateListeners = new CopyOnWriteArrayList<>();
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
            state = state.appendMessage(new AgentMessage.UserMessage(List.of(new TextContent(text, null)), 1L));
            emitState();
            state = state.appendMessage(new AgentMessage.AssistantMessage(
                List.of(new TextContent("Ack: " + text, null)),
                state.model().api(),
                state.model().provider(),
                state.model().id(),
                new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                dev.pi.ai.model.StopReason.STOP,
                null,
                2L
            ));
            emitState();
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

        private void emitState() {
            for (var listener : stateListeners) {
                listener.accept(state);
            }
        }
    }
}
