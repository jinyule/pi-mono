package dev.pi.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.Context;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.ToolCall;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class AgentLoop {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AgentLoop() {
    }

    public static AgentEventStream start(
        List<AgentMessage> prompts,
        AgentContext context,
        AgentLoopConfig config
    ) {
        Objects.requireNonNull(prompts, "prompts");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(config, "config");

        var stream = new AgentEventStream();
        var activeAssistantStream = new AtomicReference<dev.pi.ai.stream.AssistantMessageEventStream>();
        var cancelled = new AtomicBoolean(false);
        stream.onClose(() -> cancelStream(cancelled, activeAssistantStream));
        Thread.ofVirtual().name("agent-loop").start(() -> runStart(prompts, context, config, stream, activeAssistantStream, cancelled));
        return stream;
    }

    public static AgentEventStream continueLoop(
        AgentContext context,
        AgentLoopConfig config
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(config, "config");

        if (context.messages().isEmpty()) {
            throw new IllegalArgumentException("Cannot continue without messages");
        }
        if (context.messages().get(context.messages().size() - 1) instanceof AgentMessage.AssistantMessage) {
            throw new IllegalArgumentException("Cannot continue from assistant message");
        }

        var stream = new AgentEventStream();
        var activeAssistantStream = new AtomicReference<dev.pi.ai.stream.AssistantMessageEventStream>();
        var cancelled = new AtomicBoolean(false);
        stream.onClose(() -> cancelStream(cancelled, activeAssistantStream));
        Thread.ofVirtual().name("agent-loop").start(() -> runContinue(context, config, stream, activeAssistantStream, cancelled));
        return stream;
    }

    private static void runStart(
        List<AgentMessage> prompts,
        AgentContext context,
        AgentLoopConfig config,
        AgentEventStream stream,
        AtomicReference<dev.pi.ai.stream.AssistantMessageEventStream> activeAssistantStream,
        AtomicBoolean cancelled
    ) {
        var newMessages = new ArrayList<AgentMessage>();

        try {
            stream.push(new AgentEvent.AgentStart());
            runLoop(context, List.copyOf(prompts), newMessages, config, stream, activeAssistantStream, cancelled);
        } catch (Exception exception) {
            emitLoopFailure(config, newMessages, exception, stream);
        }
    }

    private static void runContinue(
        AgentContext context,
        AgentLoopConfig config,
        AgentEventStream stream,
        AtomicReference<dev.pi.ai.stream.AssistantMessageEventStream> activeAssistantStream,
        AtomicBoolean cancelled
    ) {
        var newMessages = new ArrayList<AgentMessage>();

        try {
            stream.push(new AgentEvent.AgentStart());
            runLoop(context, List.of(), newMessages, config, stream, activeAssistantStream, cancelled);
        } catch (Exception exception) {
            emitLoopFailure(config, newMessages, exception, stream);
        }
    }

    private static void runLoop(
        AgentContext context,
        List<AgentMessage> pendingMessages,
        List<AgentMessage> newMessages,
        AgentLoopConfig config,
        AgentEventStream stream,
        AtomicReference<dev.pi.ai.stream.AssistantMessageEventStream> activeAssistantStream,
        AtomicBoolean cancelled
    ) {
        var currentContext = context;
        var currentPendingMessages = combinePendingMessages(pendingMessages, pollMessages(config.steeringMessages()));
        var firstTurn = true;

        while (true) {
            var hasMoreToolCalls = true;
            List<AgentMessage> steeringAfterTools = null;

            while (hasMoreToolCalls || !currentPendingMessages.isEmpty()) {
                if (!firstTurn) {
                    stream.push(new AgentEvent.TurnStart());
                } else {
                    firstTurn = false;
                    stream.push(new AgentEvent.TurnStart());
                }

                if (!currentPendingMessages.isEmpty()) {
                    currentContext = currentContext.appendMessages(currentPendingMessages);
                    for (var pendingMessage : currentPendingMessages) {
                        newMessages.add(pendingMessage);
                        stream.push(new AgentEvent.MessageStart(pendingMessage));
                        stream.push(new AgentEvent.MessageEnd(pendingMessage));
                    }
                    currentPendingMessages = List.of();
                }

                var assistantMessage = streamAssistantResponse(currentContext, config, stream, activeAssistantStream);
                newMessages.add(assistantMessage);
                currentContext = currentContext.appendMessage(assistantMessage);

                if (assistantMessage.stopReason() == StopReason.ERROR || assistantMessage.stopReason() == StopReason.ABORTED) {
                    stream.push(new AgentEvent.TurnEnd(assistantMessage, List.of()));
                    stream.push(new AgentEvent.AgentEnd(List.copyOf(newMessages)));
                    return;
                }

                var toolExecution = executeToolCalls(
                    currentContext.tools(),
                    assistantMessage,
                    stream,
                    config.steeringMessages(),
                    cancelled
                );
                var toolResults = toolExecution.toolResults();
                hasMoreToolCalls = !toolResults.isEmpty();
                steeringAfterTools = toolExecution.steeringMessages();

                if (!toolResults.isEmpty()) {
                    currentContext = currentContext.appendMessages(new ArrayList<>(toolResults));
                    newMessages.addAll(toolResults);
                }

                stream.push(new AgentEvent.TurnEnd(assistantMessage, toolResults));

                if (steeringAfterTools != null && !steeringAfterTools.isEmpty()) {
                    currentPendingMessages = List.copyOf(steeringAfterTools);
                    steeringAfterTools = null;
                } else {
                    currentPendingMessages = pollMessages(config.steeringMessages());
                }
            }

            var followUpMessages = pollMessages(config.followUpMessages());
            if (!followUpMessages.isEmpty()) {
                currentPendingMessages = followUpMessages;
                continue;
            }

            stream.push(new AgentEvent.AgentEnd(List.copyOf(newMessages)));
            return;
        }
    }

    private static AgentMessage.AssistantMessage streamAssistantResponse(
        AgentContext context,
        AgentLoopConfig config,
        AgentEventStream stream,
        AtomicReference<dev.pi.ai.stream.AssistantMessageEventStream> activeAssistantStream
    ) {
        var transformedMessages = await(config.transformContext() == null
            ? AgentLoopConfig.completedFuture(context.messages())
            : config.transformContext().transform(context.messages()));
        var llmMessages = await(config.convertToLlm().convert(transformedMessages));
        var llmContext = new Context(
            context.systemPrompt(),
            llmMessages,
            context.tools().stream().map(AgentTool::toTool).toList()
        );
        var assistantStream = config.streamFunction().stream(
            config.model(),
            llmContext,
            config.toRequestOptions(resolveApiKey(config))
        );
        activeAssistantStream.set(assistantStream);

        try (var ignored = assistantStream.subscribe(event -> handleAssistantEvent(event, stream))) {
            return asAssistant(assistantStream.result().join());
        } finally {
            activeAssistantStream.compareAndSet(assistantStream, null);
        }
    }

    private static void handleAssistantEvent(
        dev.pi.ai.model.AssistantMessageEvent event,
        AgentEventStream stream
    ) {
        switch (event) {
            case dev.pi.ai.model.AssistantMessageEvent.Start start ->
                stream.push(new AgentEvent.MessageStart(asAssistant(start.partial())));
            case dev.pi.ai.model.AssistantMessageEvent.Done done -> {
                var assistant = asAssistant(done.message());
                stream.push(new AgentEvent.MessageEnd(assistant));
            }
            case dev.pi.ai.model.AssistantMessageEvent.Error error -> {
                var assistant = asAssistant(error.error());
                stream.push(new AgentEvent.MessageEnd(assistant));
            }
            default -> stream.push(new AgentEvent.MessageUpdate(asAssistant(partialMessage(event)), event));
        }
    }

    private static Message.AssistantMessage partialMessage(dev.pi.ai.model.AssistantMessageEvent event) {
        return switch (event) {
            case dev.pi.ai.model.AssistantMessageEvent.TextStart textStart -> textStart.partial();
            case dev.pi.ai.model.AssistantMessageEvent.TextDelta textDelta -> textDelta.partial();
            case dev.pi.ai.model.AssistantMessageEvent.TextEnd textEnd -> textEnd.partial();
            case dev.pi.ai.model.AssistantMessageEvent.ThinkingStart thinkingStart -> thinkingStart.partial();
            case dev.pi.ai.model.AssistantMessageEvent.ThinkingDelta thinkingDelta -> thinkingDelta.partial();
            case dev.pi.ai.model.AssistantMessageEvent.ThinkingEnd thinkingEnd -> thinkingEnd.partial();
            case dev.pi.ai.model.AssistantMessageEvent.ToolcallStart toolcallStart -> toolcallStart.partial();
            case dev.pi.ai.model.AssistantMessageEvent.ToolcallDelta toolcallDelta -> toolcallDelta.partial();
            case dev.pi.ai.model.AssistantMessageEvent.ToolcallEnd toolcallEnd -> toolcallEnd.partial();
            default -> throw new IllegalStateException("Unexpected event for partial extraction: " + event.type());
        };
    }

    private static ToolExecutionResult executeToolCalls(
        List<AgentTool<?>> tools,
        AgentMessage.AssistantMessage assistantMessage,
        AgentEventStream stream,
        AgentLoopConfig.MessageSource steeringMessagesSource,
        AtomicBoolean cancelled
    ) {
        var toolCalls = assistantMessage.content().stream()
            .filter(ToolCall.class::isInstance)
            .map(ToolCall.class::cast)
            .toList();
        if (toolCalls.isEmpty()) {
            return new ToolExecutionResult(List.of(), null);
        }

        var toolResults = new ArrayList<AgentMessage.ToolResultMessage>(toolCalls.size());
        List<AgentMessage> steeringMessages = null;
        for (int index = 0; index < toolCalls.size(); index++) {
            var toolCall = toolCalls.get(index);
            stream.push(new AgentEvent.ToolExecutionStart(toolCall.id(), toolCall.name(), toolCall.arguments()));

            AgentToolResult<?> result;
            var isError = false;
            try {
                var tool = findTool(tools, toolCall.name());
                var validatedArguments = ToolArgumentsValidator.validate(tool, toolCall);
                result = await(tool.execute(
                    toolCall.id(),
                    validatedArguments,
                    partialResult -> stream.push(new AgentEvent.ToolExecutionUpdate(
                        toolCall.id(),
                        toolCall.name(),
                        toolCall.arguments(),
                        partialResult
                    )),
                    cancelled::get
                ));
            } catch (Exception exception) {
                result = errorToolResult(exception);
                isError = true;
            }

            stream.push(new AgentEvent.ToolExecutionEnd(toolCall.id(), toolCall.name(), result, isError));

            var toolResultMessage = new AgentMessage.ToolResultMessage(
                toolCall.id(),
                toolCall.name(),
                result.content(),
                toJsonNode(result.details()),
                isError,
                System.currentTimeMillis()
            );
            toolResults.add(toolResultMessage);
            stream.push(new AgentEvent.MessageStart(toolResultMessage));
            stream.push(new AgentEvent.MessageEnd(toolResultMessage));

            if (steeringMessagesSource != null) {
                var polledMessages = pollMessages(steeringMessagesSource);
                if (!polledMessages.isEmpty()) {
                    steeringMessages = polledMessages;
                    for (var skippedToolCall : toolCalls.subList(index + 1, toolCalls.size())) {
                        toolResults.add(skipToolCall(skippedToolCall, stream));
                    }
                    break;
                }
            }
        }

        return new ToolExecutionResult(List.copyOf(toolResults), steeringMessages == null ? null : List.copyOf(steeringMessages));
    }

    private static String resolveApiKey(AgentLoopConfig config) {
        if (config.apiKeyProvider() != null) {
            var resolved = await(config.apiKeyProvider().resolve(config.model().provider()));
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
        }
        return config.apiKey();
    }

    private static List<AgentMessage> pollMessages(AgentLoopConfig.MessageSource messageSource) {
        if (messageSource == null) {
            return List.of();
        }
        var messages = await(messageSource.get());
        return messages == null ? List.of() : List.copyOf(messages);
    }

    private static List<AgentMessage> combinePendingMessages(
        List<AgentMessage> initialMessages,
        List<AgentMessage> queuedMessages
    ) {
        if (initialMessages.isEmpty()) {
            return queuedMessages;
        }
        if (queuedMessages.isEmpty()) {
            return List.copyOf(initialMessages);
        }
        var combined = new ArrayList<AgentMessage>(initialMessages.size() + queuedMessages.size());
        combined.addAll(initialMessages);
        combined.addAll(queuedMessages);
        return List.copyOf(combined);
    }

    private static AgentTool<?> findTool(List<AgentTool<?>> tools, String toolName) {
        return tools.stream()
            .filter(tool -> tool.name().equals(toolName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Tool " + toolName + " not found"));
    }

    private static AgentMessage.ToolResultMessage skipToolCall(
        ToolCall toolCall,
        AgentEventStream stream
    ) {
        var result = new AgentToolResult<>(
            List.of(new TextContent("Skipped due to queued user message.", null)),
            Map.of()
        );

        stream.push(new AgentEvent.ToolExecutionStart(toolCall.id(), toolCall.name(), toolCall.arguments()));
        stream.push(new AgentEvent.ToolExecutionEnd(toolCall.id(), toolCall.name(), result, true));

        var toolResultMessage = new AgentMessage.ToolResultMessage(
            toolCall.id(),
            toolCall.name(),
            result.content(),
            toJsonNode(result.details()),
            true,
            System.currentTimeMillis()
        );
        stream.push(new AgentEvent.MessageStart(toolResultMessage));
        stream.push(new AgentEvent.MessageEnd(toolResultMessage));
        return toolResultMessage;
    }

    private static void emitLoopFailure(
        AgentLoopConfig config,
        List<AgentMessage> newMessages,
        Exception exception,
        AgentEventStream stream
    ) {
        var errorAssistant = AgentMessage.AssistantMessage.error(
            config.model().api(),
            config.model().provider(),
            config.model().id(),
            exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
            System.currentTimeMillis()
        );
        newMessages.add(errorAssistant);

        stream.push(new AgentEvent.MessageStart(errorAssistant));
        stream.push(new AgentEvent.MessageEnd(errorAssistant));
        stream.push(new AgentEvent.TurnEnd(errorAssistant, List.of()));
        stream.push(new AgentEvent.AgentEnd(List.copyOf(newMessages)));
    }

    private static AgentMessage.AssistantMessage asAssistant(Message.AssistantMessage message) {
        var agentMessage = AgentMessages.fromLlmMessage(message);
        if (agentMessage instanceof AgentMessage.AssistantMessage assistantMessage) {
            return assistantMessage;
        }
        throw new IllegalStateException("Expected assistant message");
    }

    private static AgentToolResult<Map<String, Object>> errorToolResult(Exception exception) {
        return new AgentToolResult<>(
            List.of(new TextContent(messageOf(exception), null)),
            Map.of()
        );
    }

    private static JsonNode toJsonNode(Object value) {
        if (value instanceof JsonNode jsonNode) {
            return jsonNode.deepCopy();
        }
        if (value == null) {
            return JsonNodeFactory.instance.nullNode();
        }
        return OBJECT_MAPPER.valueToTree(value);
    }

    private static String messageOf(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static void cancelStream(
        AtomicBoolean cancelled,
        AtomicReference<dev.pi.ai.stream.AssistantMessageEventStream> activeAssistantStream
    ) {
        cancelled.set(true);
        var assistantStream = activeAssistantStream.getAndSet(null);
        if (assistantStream != null) {
            assistantStream.close();
        }
    }

    private record ToolExecutionResult(
        List<AgentMessage.ToolResultMessage> toolResults,
        List<AgentMessage> steeringMessages
    ) {
    }
}
