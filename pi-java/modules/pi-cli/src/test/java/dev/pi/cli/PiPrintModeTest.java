package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentMessage;
import dev.pi.agent.runtime.AgentState;
import dev.pi.agent.runtime.AgentTool;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.Subscription;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class PiPrintModeTest {
    @Test
    void printsAssistantOutputToStdout() {
        var stdout = new StringBuilder();
        var stderr = new StringBuilder();
        var mode = new PiPrintMode(new FakePrintSession(false), stdout, stderr);

        var result = mode.run("Summarize").toCompletableFuture().join();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("Ack: Summarize");
        assertThat(stdout.toString()).contains("Ack: Summarize");
        assertThat(stderr).isEmpty();
    }

    @Test
    void writesAssistantErrorsToStderr() {
        var stdout = new StringBuilder();
        var stderr = new StringBuilder();
        var mode = new PiPrintMode(new FakePrintSession(true), stdout, stderr);

        var result = mode.run("Summarize").toCompletableFuture().join();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("Model failed");
        assertThat(stdout).isEmpty();
        assertThat(stderr.toString()).contains("Model failed");
    }

    @Test
    void rejectsBlankPrompt() {
        var mode = new PiPrintMode(new FakePrintSession(false), new StringBuilder());

        assertThatThrownBy(() -> mode.run("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-blank");
    }

    private static final class FakePrintSession implements PiInteractiveSession {
        private final boolean fail;
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

        private FakePrintSession(boolean fail) {
            this.fail = fail;
        }

        @Override
        public String sessionId() {
            return "session-print";
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
            state = state.appendMessage(new AgentMessage.UserMessage(List.of(new TextContent(text, null)), 1L));
            state = state.appendMessage(new AgentMessage.AssistantMessage(
                List.of(new TextContent("Ack: " + text, null)),
                state.model().api(),
                state.model().provider(),
                state.model().id(),
                new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                fail ? StopReason.ERROR : StopReason.STOP,
                fail ? "Model failed" : null,
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
