package dev.pi.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.AssistantContent;
import dev.pi.ai.model.AssistantMessageEvent;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.AssistantMessageEventStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AgentTest {
    @Test
    void promptPublishesEventsAndStateUpdates() {
        var stateSnapshots = new CopyOnWriteArrayList<AgentState>();
        var events = new CopyOnWriteArrayList<AgentEvent>();
        var agent = Agent.builder(model())
            .systemPrompt("System")
            .streamFunction(successStream("Hello back."))
            .build();

        try (
            var ignoredState = agent.subscribeState(stateSnapshots::add);
            var ignoredEvents = agent.subscribe(events::add)
        ) {
            agent.prompt("Hello").toCompletableFuture().join();
        }

        assertThat(agent.state().messages()).extracting(AgentMessage::role)
            .containsExactly("user", "assistant");
        assertThat(agent.state().isStreaming()).isFalse();
        assertThat(agent.state().pendingToolCalls()).isEmpty();

        assertThat(stateSnapshots).isNotEmpty();
        assertThat(stateSnapshots.get(0).messages()).isEmpty();
        assertThat(stateSnapshots.stream().anyMatch(AgentState::isStreaming)).isTrue();
        assertThat(stateSnapshots.get(stateSnapshots.size() - 1).messages())
            .extracting(AgentMessage::role)
            .containsExactly("user", "assistant");

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

    @Test
    void resumeUsesQueuedFollowUpAfterAssistantTail() {
        var capturedContexts = new CopyOnWriteArrayList<List<Message>>();
        var agent = Agent.builder(model())
            .systemPrompt("System")
            .streamFunction(scriptedStream(
                capturedContexts,
                assistant(model(), List.of(new TextContent("First answer.", null)), StopReason.STOP, null, 10L),
                assistant(model(), List.of(new TextContent("Follow-up answer.", null)), StopReason.STOP, null, 20L)
            ))
            .build();

        agent.prompt("Hello").toCompletableFuture().join();
        agent.followUp(new AgentMessage.UserMessage(List.of(new TextContent("One more thing.", null)), 30L));
        agent.resume().toCompletableFuture().join();
        agent.waitForIdle().toCompletableFuture().join();

        assertThat(agent.state().messages()).extracting(AgentMessage::role)
            .containsExactly("user", "assistant", "user", "assistant");
        assertThat(capturedContexts).hasSize(2);
        assertThat(capturedContexts.get(1)).extracting(Message::role)
            .containsExactly("user", "assistant", "user");
        assertThat(agent.state().isStreaming()).isFalse();
    }

    @Test
    void abortClosesInnerAssistantStreamAndPublishesAbortedAssistant() throws Exception {
        var started = new CountDownLatch(1);
        var closed = new CountDownLatch(1);
        var events = new CopyOnWriteArrayList<AgentEvent>();
        var agent = Agent.builder(model())
            .systemPrompt("System")
            .streamFunction((model, context, options) -> {
                var stream = new AssistantMessageEventStream();
                stream.onClose(closed::countDown);
                Thread.ofVirtual().start(() -> {
                    started.countDown();
                    stream.push(new AssistantMessageEvent.Start(
                        assistant(model, List.of(), StopReason.STOP, null, 10L)
                    ));
                    while (!stream.isClosed()) {
                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                });
                return stream;
            })
            .build();

        try (var ignored = agent.subscribe(events::add)) {
            var prompt = agent.prompt("Hello");

            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            agent.abort();
            prompt.toCompletableFuture().join();
            agent.waitForIdle().toCompletableFuture().join();

            assertThat(closed.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(agent.state().isStreaming()).isFalse();
            assertThat(agent.state().pendingToolCalls()).isEmpty();
            assertThat(agent.state().error()).isEqualTo("Request was aborted");
            assertThat(agent.state().messages()).extracting(AgentMessage::role)
                .containsExactly("user", "assistant");

            var abortedMessage = (AgentMessage.AssistantMessage) agent.state().messages().get(1);
            assertThat(abortedMessage.stopReason()).isEqualTo(StopReason.ABORTED);
            assertThat(abortedMessage.errorMessage()).isEqualTo("Request was aborted");
            assertThat(events).extracting(AgentEvent::type).containsSubsequence(
                "message_start",
                "message_end",
                "turn_end",
                "agent_end"
            );
        }
    }

    @Test
    void exposesSteeringQueueSnapshotAndDrain() {
        var agent = Agent.builder(model())
            .systemPrompt("System")
            .streamFunction(successStream("Hello back."))
            .build();
        var first = new AgentMessage.UserMessage(List.of(new TextContent("First", null)), 10L);
        var second = new AgentMessage.UserMessage(List.of(new TextContent("Second", null)), 20L);

        agent.steer(first);
        agent.steer(second);

        assertThat(agent.steeringMessages()).containsExactly(first, second);
        assertThat(agent.drainSteeringQueue()).containsExactly(first, second);
        assertThat(agent.steeringMessages()).isEmpty();
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

    private static AgentLoopConfig.AssistantStreamFunction scriptedStream(
        CopyOnWriteArrayList<List<Message>> capturedContexts,
        Message.AssistantMessage... scriptedMessages
    ) {
        var index = new AtomicInteger();
        return (model, context, options) -> {
            capturedContexts.add(List.copyOf(context.messages()));
            var messageIndex = index.getAndIncrement();
            if (messageIndex >= scriptedMessages.length) {
                throw new IllegalStateException("Unexpected stream invocation");
            }
            return scriptedAssistant(scriptedMessages[messageIndex]);
        };
    }

    private static AssistantMessageEventStream scriptedAssistant(Message.AssistantMessage message) {
        var stream = new AssistantMessageEventStream();
        Thread.ofVirtual().start(() -> {
            stream.push(new AssistantMessageEvent.Start(
                new Message.AssistantMessage(
                    List.of(),
                    message.api(),
                    message.provider(),
                    message.model(),
                    message.usage(),
                    message.stopReason(),
                    message.errorMessage(),
                    message.timestamp()
                )
            ));
            stream.push(new AssistantMessageEvent.Done(message.stopReason(), message));
        });
        return stream;
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
