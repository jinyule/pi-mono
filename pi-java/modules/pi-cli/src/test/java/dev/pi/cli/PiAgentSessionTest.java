package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.agent.runtime.AgentLoopConfig;
import dev.pi.ai.model.AssistantMessageEvent;
import dev.pi.ai.model.Context;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.AssistantMessageEventStream;
import dev.pi.session.InstructionResources;
import dev.pi.session.SessionManager;
import dev.pi.session.SettingsManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class PiAgentSessionTest {
    @Test
    void rehydratesSessionContextAndPersistsPromptedMessages() {
        var sessionManager = SessionManager.inMemory("/workspace");
        var restored = new Message.UserMessage(List.of(new TextContent("existing", null)), 100L);
        try {
            sessionManager.appendMessage(restored);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }

        var session = PiAgentSession.builder(
            testModel(),
            sessionManager,
            SettingsManager.inMemory(),
            new InstructionResources(List.of(), "Base system prompt", List.of("Append prompt"))
        )
            .streamFunction(fakeAssistant("Ack: hello"))
            .build();

        assertThat(session.state().messages())
            .extracting(message -> message.role())
            .containsExactly("user");
        assertThat(session.state().systemPrompt()).contains("Base system prompt").contains("Append prompt");

        session.prompt("hello").toCompletableFuture().join();
        session.waitForIdle().toCompletableFuture().join();

        assertThat(session.state().messages())
            .extracting(message -> message.role())
            .containsExactly("user", "user", "assistant");
        assertThat(sessionManager.buildSessionContext().messages())
            .extracting(Message::role)
            .containsExactly("user", "user", "assistant");
        assertThat(session.drainPersistenceErrors()).isEmpty();
    }

    private static Model testModel() {
        return new Model(
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
        );
    }

    private static AgentLoopConfig.AssistantStreamFunction fakeAssistant(String text) {
        return (model, context, options) -> {
            var stream = new AssistantMessageEventStream();
            Thread.ofVirtual().start(() -> stream.push(new AssistantMessageEvent.Done(
                StopReason.STOP,
                new Message.AssistantMessage(
                    List.of(new TextContent(text, null)),
                    model.api(),
                    model.provider(),
                    model.id(),
                    new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                    StopReason.STOP,
                    null,
                    200L
                )
            )));
            return stream;
        };
    }
}
