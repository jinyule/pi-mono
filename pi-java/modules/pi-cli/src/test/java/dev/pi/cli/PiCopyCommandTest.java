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
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class PiCopyCommandTest {
    @Test
    void copiesLastAssistantMessageText() {
        var copied = new StringBuilder();
        var session = new StubSession(List.of(
            new AgentMessage.UserMessage(List.of(new TextContent("hello", null)), 1L),
            new AgentMessage.AssistantMessage(
                List.of(new TextContent("Copied text", null)),
                "openai-responses",
                "openai",
                "test-model",
                new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                StopReason.STOP,
                null,
                2L
            )
        ));

        var result = new PiCopyCommand(session, copied::append).copyLastAssistantMessage();

        assertThat(result).isEqualTo("Copied text");
        assertThat(copied.toString()).isEqualTo("Copied text");
    }

    @Test
    void rejectsMissingAssistantMessage() {
        var session = new StubSession(List.of(new AgentMessage.UserMessage(List.of(new TextContent("hello", null)), 1L)));

        assertThatThrownBy(() -> new PiCopyCommand(session, ignored -> {
        }).copyLastAssistantMessage())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No agent messages");
    }

    @Test
    void rejectsBlankAssistantMessage() {
        var session = new StubSession(List.of(
            new AgentMessage.AssistantMessage(
                List.of(new TextContent(" ", null)),
                "openai-responses",
                "openai",
                "test-model",
                new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                StopReason.STOP,
                null,
                2L
            )
        ));

        assertThatThrownBy(() -> new PiCopyCommand(session, ignored -> {
        }).copyLastAssistantMessage())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no copyable text");
    }

    private static final class StubSession implements PiInteractiveSession {
        private final AgentState state;

        private StubSession(List<AgentMessage> messages) {
            this.state = new AgentState(
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
                messages,
                false,
                null,
                Set.of(),
                null
            );
        }

        @Override
        public String sessionId() {
            return "session";
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
            return () -> {
            };
        }

        @Override
        public CompletionStage<Void> prompt(String text) {
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
    }
}
