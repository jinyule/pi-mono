package dev.pi.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.AssistantMessageEvent;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.ToolCall;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.AssistantMessageEventStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class AgentLoopCancellationTest {
    @Test
    void closingAgentStreamCancelsRunningToolExecution() throws Exception {
        var toolStarted = new CountDownLatch(1);
        var toolCancelled = new CountDownLatch(1);
        var model = model();

        var stream = AgentLoop.start(
            List.of(new AgentMessage.UserMessage(List.of(new TextContent("Run tool", null)), 1L)),
            new AgentContext("System", List.of(), List.of(cancelAwareTool(toolStarted, toolCancelled))),
            AgentLoopConfig.builder(
                model,
                (requestedModel, context, options) -> scriptedAssistant(new Message.AssistantMessage(
                    List.of(new ToolCall("call-1", "wait", JsonNodeFactory.instance.objectNode(), null)),
                    model.api(),
                    model.provider(),
                    model.id(),
                    zeroUsage(),
                    StopReason.TOOL_USE,
                    null,
                    10L
                )),
                messages -> CompletableFuture.completedFuture(AgentMessages.toLlmMessages(messages))
            ).build()
        );

        assertThat(toolStarted.await(1, TimeUnit.SECONDS)).isTrue();
        stream.close();

        assertThatThrownBy(() -> stream.result().join()).isInstanceOf(java.util.concurrent.CancellationException.class);
        assertThat(toolCancelled.await(1, TimeUnit.SECONDS)).isTrue();
    }

    private static AgentTool<?> cancelAwareTool(CountDownLatch toolStarted, CountDownLatch toolCancelled) {
        return new AgentTool<Object>() {
            @Override
            public String name() {
                return "wait";
            }

            @Override
            public String label() {
                return "Wait";
            }

            @Override
            public String description() {
                return "Wait tool";
            }

            @Override
            public JsonNode parametersSchema() {
                return JsonNodeFactory.instance.objectNode().put("type", "object");
            }

            @Override
            public CompletableFuture<AgentToolResult<Object>> execute(
                String toolCallId,
                JsonNode arguments,
                java.util.function.Consumer<AgentToolResult<Object>> onUpdate
            ) {
                return CompletableFuture.failedFuture(new IllegalStateException("Should call cancellation-aware overload"));
            }

            @Override
            public CompletableFuture<AgentToolResult<Object>> execute(
                String toolCallId,
                JsonNode arguments,
                java.util.function.Consumer<AgentToolResult<Object>> onUpdate,
                BooleanSupplier cancelled
            ) {
                return CompletableFuture.supplyAsync(() -> {
                    toolStarted.countDown();
                    while (!cancelled.getAsBoolean()) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    toolCancelled.countDown();
                    return new AgentToolResult<>(List.of(new TextContent("cancelled", null)), java.util.Map.of());
                });
            }
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

    private static Usage zeroUsage() {
        return new Usage(0, 0, 0, 0, 0, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0));
    }
}
