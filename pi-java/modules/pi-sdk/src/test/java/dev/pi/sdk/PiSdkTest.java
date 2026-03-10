package dev.pi.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.agent.runtime.AgentLoopConfig;
import dev.pi.ai.model.AssistantMessageEvent;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.AssistantMessageEventStream;
import dev.pi.ai.stream.Subscription;
import dev.pi.session.SessionManager;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PiSdkTest {
    @Test
    void exposesSessionHelpersAndCreatesAgentSessions() throws Exception {
        var sdk = new PiSdk();
        var sessionManager = sdk.createInMemorySession("/workspace");

        assertThat(sessionManager).isNotNull();
        assertThat(sdk.modelRegistry()).isNotNull();

        var session = sdk.createAgentSession(CreateAgentSessionOptions.builder(
            testModel(),
            fakeAssistant("Ack: hi"),
            sessionManager
        ).systemPrompt("SDK prompt").build());

        session.prompt("hi").toCompletableFuture().join();
        session.waitForIdle().toCompletableFuture().join();

        assertThat(session.state().messages())
            .extracting(message -> message.role())
            .containsExactly("user", "assistant");
        assertThat(session.sessionManager().buildSessionContext().messages())
            .extracting(Message::role)
            .containsExactly("user", "assistant");
    }

    @Test
    void canCreatePersistentSessionManager() {
        var sdk = new PiSdk();
        var sessionManager = sdk.createPersistentSession(Path.of("session.jsonl"), "/workspace");

        assertThat(sessionManager.persistent()).isTrue();
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
