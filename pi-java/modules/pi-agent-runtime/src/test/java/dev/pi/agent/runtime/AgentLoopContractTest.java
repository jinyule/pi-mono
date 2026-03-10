package dev.pi.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.AssistantContent;
import dev.pi.ai.model.AssistantMessageEvent;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.AssistantMessageEventStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class AgentLoopContractTest {
    @Test
    void startEmitsSingleTurnLifecycleForPromptAndAssistant() {
        var events = new CopyOnWriteArrayList<AgentEvent>();
        var stream = AgentLoop.start(
            List.of(new AgentMessage.UserMessage(List.of(new TextContent("Hello", null)), 1L)),
            new AgentContext("You are a helpful assistant.", List.of(), List.of()),
            builder(model(), successStream("Hello back.")).build()
        );

        try (var ignored = stream.subscribe(events::add)) {
            var result = stream.result().join();

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isInstanceOf(AgentMessage.UserMessage.class);
            assertThat(result.get(1)).isInstanceOf(AgentMessage.AssistantMessage.class);
            assertThat(((AgentMessage.AssistantMessage) result.get(1)).stopReason()).isEqualTo(StopReason.STOP);

            assertThat(events).extracting(AgentEvent::type).containsExactly(
                "agent_start",
                "turn_start",
                "message_start",
                "message_end",
                "message_start",
                "message_update",
                "message_update",
                "message_update",
                "message_end",
                "turn_end",
                "agent_end"
            );
        }
    }

    @Test
    void startAppliesTransformContextBeforeConversion() {
        var steps = new ArrayList<String>();
        var capturedMessages = new java.util.concurrent.atomic.AtomicReference<List<Message>>();

        var stream = AgentLoop.start(
            List.of(new AgentMessage.UserMessage(List.of(new TextContent("prompt", null)), 1L)),
            new AgentContext("System", List.of(new AgentMessage.UserMessage(List.of(new TextContent("before", null)), 0L)), List.of()),
            builder(
                model(),
                (model, context, options) -> {
                    capturedMessages.set(context.messages());
                    return successStream("ok").stream(model, context, options);
                }
            )
                .transformContext(messages -> {
                    steps.add("transform");
                    var transformed = new ArrayList<>(messages);
                    transformed.add(new AgentMessage.UserMessage(List.of(new TextContent("transformed", null)), 2L));
                    return CompletableFuture.completedFuture(List.copyOf(transformed));
                })
                .convertToLlm(messages -> {
                    steps.add("convert");
                    return CompletableFuture.completedFuture(AgentMessages.toLlmMessages(messages));
                })
                .build()
        );

        stream.result().join();

        assertThat(steps).containsExactly("transform", "convert");
        assertThat(capturedMessages.get()).extracting(Message::role).containsExactly("user", "user", "user");
        assertThat(((Message.UserMessage) capturedMessages.get().get(2)).content())
            .extracting(TextContent.class::cast)
            .extracting(TextContent::text)
            .containsExactly("transformed");
    }

    @Test
    void continueLoopRejectsAssistantTailMessage() {
        assertThatThrownBy(() -> AgentLoop.continueLoop(
            new AgentContext(
                "System",
                List.of(new AgentMessage.AssistantMessage(List.of(), "api", "provider", "model", zeroUsage(), StopReason.STOP, null, 1L)),
                List.of()
            ),
            builder(model(), successStream("unused")).build()
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("assistant");
    }

    private static AgentLoopConfig.Builder builder(
        Model model,
        AgentLoopConfig.AssistantStreamFunction streamFunction
    ) {
        return AgentLoopConfig.builder(
            model,
            streamFunction,
            messages -> CompletableFuture.completedFuture(AgentMessages.toLlmMessages(messages))
        );
    }

    private static AgentLoopConfig.AssistantStreamFunction successStream(String text) {
        return (model, context, options) -> {
            var stream = new AssistantMessageEventStream();
            Thread.ofVirtual().start(() -> {
                var partialStart = assistant(model, List.of(), StopReason.STOP, null, 10L);
                var partialText = assistant(model, List.of(new TextContent(text, null)), StopReason.STOP, null, 10L);
                stream.push(new AssistantMessageEvent.Start(partialStart));
                stream.push(new AssistantMessageEvent.TextStart(0, partialText));
                stream.push(new AssistantMessageEvent.TextDelta(0, text, partialText));
                stream.push(new AssistantMessageEvent.TextEnd(0, text, partialText));
                stream.push(new AssistantMessageEvent.Done(StopReason.STOP, partialText));
            });
            return stream;
        };
    }

    private static Model model() {
        return new Model(
            "runtime-test-model",
            "runtime-test-model",
            "test-api",
            "test-provider",
            "https://example.com",
            false,
            List.of("text"),
            zeroUsage().cost(),
            100_000,
            8_192,
            java.util.Map.of(),
            JsonNodeFactory.instance.objectNode()
        );
    }

    private static Message.AssistantMessage assistant(
        Model model,
        List<AssistantContent> content,
        StopReason stopReason,
        String errorMessage,
        long timestamp
    ) {
        return new Message.AssistantMessage(
            content,
            model.api(),
            model.provider(),
            model.id(),
            zeroUsage(),
            stopReason,
            errorMessage,
            timestamp
        );
    }

    private static Usage zeroUsage() {
        return new Usage(0, 0, 0, 0, 0, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0));
    }
}
