package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentMessage;
import dev.pi.agent.runtime.AgentState;
import dev.pi.agent.runtime.AgentTool;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class PiJsonModeTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void writesJsonlForStateAndEvents() throws Exception {
        var output = new StringBuilder();
        var mode = new PiJsonMode(new FakeJsonSession(), output);

        mode.run("Summarize").toCompletableFuture().join();

        var lines = output.toString().lines().filter(line -> !line.isBlank()).toList();
        assertThat(lines).isNotEmpty();

        var parsed = lines.stream().map(line -> {
            try {
                return OBJECT_MAPPER.readTree(line);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }).toList();

        assertThat(parsed).anySatisfy(node -> {
            assertThat(node.path("type").asText()).isEqualTo("state");
            assertThat(node.path("sessionId").asText()).isEqualTo("session-json");
        });
        assertThat(parsed).anySatisfy(node -> {
            assertThat(node.path("type").asText()).isEqualTo("event");
            assertThat(node.path("eventType").asText()).isEqualTo("message_end");
        });
    }

    @Test
    void rejectsBlankPrompt() {
        var mode = new PiJsonMode(new FakeJsonSession(), new StringBuilder());

        assertThatThrownBy(() -> mode.run(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-blank");
    }

    private static final class FakeJsonSession implements PiInteractiveSession {
        private final CopyOnWriteArrayList<Consumer<AgentEvent>> eventListeners = new CopyOnWriteArrayList<>();
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
            return "session-json";
        }

        @Override
        public AgentState state() {
            return state;
        }

        @Override
        public Subscription subscribe(Consumer<AgentEvent> listener) {
            eventListeners.add(listener);
            return () -> eventListeners.remove(listener);
        }

        @Override
        public Subscription subscribeState(Consumer<AgentState> listener) {
            listener.accept(state);
            stateListeners.add(listener);
            return () -> stateListeners.remove(listener);
        }

        @Override
        public CompletionStage<Void> prompt(String text) {
            var user = new AgentMessage.UserMessage(List.of(new TextContent(text, null)), 1L);
            state = state.appendMessage(user);
            emitState();
            emitEvent(new AgentEvent.MessageEnd(user));

            var assistant = new AgentMessage.AssistantMessage(
                List.of(new TextContent("Ack: " + text, null)),
                state.model().api(),
                state.model().provider(),
                state.model().id(),
                new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                StopReason.STOP,
                null,
                2L
            );
            state = state.appendMessage(assistant);
            emitState();
            emitEvent(new AgentEvent.MessageEnd(assistant));
            emitEvent(new AgentEvent.TurnEnd(assistant, List.of()));
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

        private void emitEvent(AgentEvent event) {
            for (var listener : eventListeners) {
                listener.accept(event);
            }
        }

        private void emitState() {
            for (var listener : stateListeners) {
                listener.accept(state);
            }
        }
    }
}
