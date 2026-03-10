package dev.pi.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.pi.ai.model.AssistantContent;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AgentLoopToolExecutionTest {
    @Test
    void startExecutesToolCallsSequentiallyAndContinuesWithToolResults() {
        var executionOrder = new CopyOnWriteArrayList<String>();
        var capturedContexts = new CopyOnWriteArrayList<List<Message>>();
        var events = new CopyOnWriteArrayList<AgentEvent>();
        var model = model();

        var firstAssistant = assistant(
            model,
            List.of(
                new ToolCall("call-1", "search", objectNode("query", "alpha"), null),
                new ToolCall("call-2", "search", objectNode("query", "beta"), null)
            ),
            StopReason.TOOL_USE,
            null,
            10L
        );
        var secondAssistant = assistant(
            model,
            List.of(new TextContent("All tools finished.", null)),
            StopReason.STOP,
            null,
            20L
        );

        var stream = AgentLoop.start(
            List.of(new AgentMessage.UserMessage(List.of(new TextContent("Run tools", null)), 1L)),
            new AgentContext("System", List.of(), List.of(searchTool(executionOrder))),
            builder(model, scriptedStream(capturedContexts, firstAssistant, secondAssistant)).build()
        );

        try (var ignored = stream.subscribe(events::add)) {
            var result = stream.result().join();

            assertThat(result).extracting(AgentMessage::role)
                .containsExactly("user", "assistant", "toolResult", "toolResult", "assistant");
            assertThat(executionOrder).containsExactly("alpha", "beta");
            assertThat(capturedContexts).hasSize(2);
            assertThat(capturedContexts.get(1)).extracting(Message::role)
                .containsExactly("user", "assistant", "toolResult", "toolResult");
            assertThat(events.stream()
                .filter(AgentEvent.ToolExecutionStart.class::isInstance)
                .map(AgentEvent.ToolExecutionStart.class::cast)
                .map(AgentEvent.ToolExecutionStart::toolCallId))
                .containsExactly("call-1", "call-2");
            assertThat(events.stream().filter(AgentEvent.ToolExecutionUpdate.class::isInstance)).hasSize(2);
            assertThat(events.stream()
                .filter(AgentEvent.TurnEnd.class::isInstance)
                .map(AgentEvent.TurnEnd.class::cast)
                .map(turnEnd -> turnEnd.toolResults().size()))
                .containsExactly(2, 0);
        }
    }

    @Test
    void startTurnsValidationFailureIntoErrorToolResult() {
        var executeCount = new AtomicInteger();
        var events = new CopyOnWriteArrayList<AgentEvent>();
        var model = model();

        var firstAssistant = assistant(
            model,
            List.of(new ToolCall("call-1", "lookup", JsonNodeFactory.instance.objectNode(), null)),
            StopReason.TOOL_USE,
            null,
            10L
        );
        var secondAssistant = assistant(
            model,
            List.of(new TextContent("Handled validation error.", null)),
            StopReason.STOP,
            null,
            20L
        );

        var stream = AgentLoop.start(
            List.of(new AgentMessage.UserMessage(List.of(new TextContent("Lookup item", null)), 1L)),
            new AgentContext("System", List.of(), List.of(lookupTool(executeCount))),
            builder(model, scriptedStream(new CopyOnWriteArrayList<>(), firstAssistant, secondAssistant)).build()
        );

        try (var ignored = stream.subscribe(events::add)) {
            var result = stream.result().join();

            assertThat(executeCount.get()).isZero();
            assertThat(result).extracting(AgentMessage::role)
                .containsExactly("user", "assistant", "toolResult", "assistant");

            var toolResult = (AgentMessage.ToolResultMessage) result.get(2);
            assertThat(toolResult.isError()).isTrue();
            assertThat(toolResult.content()).hasSize(1);
            assertThat(((TextContent) toolResult.content().get(0)).text())
                .contains("Validation failed for tool \"lookup\"");
            assertThat(events.stream()
                .filter(AgentEvent.ToolExecutionEnd.class::isInstance)
                .map(AgentEvent.ToolExecutionEnd.class::cast)
                .allMatch(AgentEvent.ToolExecutionEnd::isError)).isTrue();
            assertThat(events.stream().filter(AgentEvent.ToolExecutionUpdate.class::isInstance)).isEmpty();
        }
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

    private static AgentTool<?> searchTool(CopyOnWriteArrayList<String> executionOrder) {
        return new AgentTool<ObjectNode>() {
            @Override
            public String name() {
                return "search";
            }

            @Override
            public String label() {
                return "Search";
            }

            @Override
            public String description() {
                return "Search tool";
            }

            @Override
            public JsonNode parametersSchema() {
                return requiredStringSchema("query");
            }

            @Override
            public CompletableFuture<AgentToolResult<ObjectNode>> execute(
                String toolCallId,
                JsonNode arguments,
                java.util.function.Consumer<AgentToolResult<ObjectNode>> onUpdate
            ) {
                executionOrder.add(arguments.get("query").asText());
                onUpdate.accept(new AgentToolResult<>(
                    List.of(new TextContent("running " + arguments.get("query").asText(), null)),
                    objectNode("status", "running")
                ));
                return CompletableFuture.completedFuture(new AgentToolResult<>(
                    List.of(new TextContent("done " + arguments.get("query").asText(), null)),
                    objectNode("status", "done")
                ));
            }
        };
    }

    private static AgentTool<?> lookupTool(AtomicInteger executeCount) {
        return new AgentTool<ObjectNode>() {
            @Override
            public String name() {
                return "lookup";
            }

            @Override
            public String label() {
                return "Lookup";
            }

            @Override
            public String description() {
                return "Lookup tool";
            }

            @Override
            public JsonNode parametersSchema() {
                return requiredStringSchema("query");
            }

            @Override
            public CompletableFuture<AgentToolResult<ObjectNode>> execute(
                String toolCallId,
                JsonNode arguments,
                java.util.function.Consumer<AgentToolResult<ObjectNode>> onUpdate
            ) {
                executeCount.incrementAndGet();
                return CompletableFuture.completedFuture(new AgentToolResult<>(
                    List.of(new TextContent("unexpected", null)),
                    JsonNodeFactory.instance.objectNode()
                ));
            }
        };
    }

    private static ObjectNode requiredStringSchema(String propertyName) {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        var properties = schema.putObject("properties");
        properties.putObject(propertyName).put("type", "string");
        schema.putArray("required").add(propertyName);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static ObjectNode objectNode(String key, String value) {
        var node = JsonNodeFactory.instance.objectNode();
        node.put(key, value);
        return node;
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
