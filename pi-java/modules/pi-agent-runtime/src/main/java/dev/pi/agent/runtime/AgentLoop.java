package dev.pi.agent.runtime;

import dev.pi.ai.model.Context;
import dev.pi.ai.model.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

public final class AgentLoop {
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
        Thread.ofVirtual().name("agent-loop").start(() -> runStart(prompts, context, config, stream));
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
        Thread.ofVirtual().name("agent-loop").start(() -> runContinue(context, config, stream));
        return stream;
    }

    private static void runStart(
        List<AgentMessage> prompts,
        AgentContext context,
        AgentLoopConfig config,
        AgentEventStream stream
    ) {
        var newMessages = new ArrayList<>(prompts);
        var currentContext = context.appendMessages(prompts);

        try {
            stream.push(new AgentEvent.AgentStart());
            stream.push(new AgentEvent.TurnStart());
            for (AgentMessage prompt : prompts) {
                stream.push(new AgentEvent.MessageStart(prompt));
                stream.push(new AgentEvent.MessageEnd(prompt));
            }

            var assistantMessage = streamAssistantResponse(currentContext, config, stream);
            newMessages.add(assistantMessage);

            stream.push(new AgentEvent.TurnEnd(assistantMessage, List.of()));
            stream.push(new AgentEvent.AgentEnd(List.copyOf(newMessages)));
        } catch (Exception exception) {
            emitLoopFailure(config, newMessages, exception, stream);
        }
    }

    private static void runContinue(
        AgentContext context,
        AgentLoopConfig config,
        AgentEventStream stream
    ) {
        var newMessages = new ArrayList<AgentMessage>();

        try {
            stream.push(new AgentEvent.AgentStart());
            stream.push(new AgentEvent.TurnStart());

            var assistantMessage = streamAssistantResponse(context, config, stream);
            newMessages.add(assistantMessage);

            stream.push(new AgentEvent.TurnEnd(assistantMessage, List.of()));
            stream.push(new AgentEvent.AgentEnd(List.copyOf(newMessages)));
        } catch (Exception exception) {
            emitLoopFailure(config, newMessages, exception, stream);
        }
    }

    private static AgentMessage.AssistantMessage streamAssistantResponse(
        AgentContext context,
        AgentLoopConfig config,
        AgentEventStream stream
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
        var finalMessage = new AtomicReference<AgentMessage.AssistantMessage>();

        try (var ignored = assistantStream.subscribe(event -> handleAssistantEvent(event, stream, finalMessage))) {
            return asAssistant(assistantStream.result().join());
        }
    }

    private static void handleAssistantEvent(
        dev.pi.ai.model.AssistantMessageEvent event,
        AgentEventStream stream,
        AtomicReference<AgentMessage.AssistantMessage> finalMessage
    ) {
        switch (event) {
            case dev.pi.ai.model.AssistantMessageEvent.Start start ->
                stream.push(new AgentEvent.MessageStart(asAssistant(start.partial())));
            case dev.pi.ai.model.AssistantMessageEvent.Done done -> {
                var assistant = asAssistant(done.message());
                finalMessage.set(assistant);
                stream.push(new AgentEvent.MessageEnd(assistant));
            }
            case dev.pi.ai.model.AssistantMessageEvent.Error error -> {
                var assistant = asAssistant(error.error());
                finalMessage.set(assistant);
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

    private static String resolveApiKey(AgentLoopConfig config) {
        if (config.apiKeyProvider() != null) {
            var resolved = await(config.apiKeyProvider().resolve(config.model().provider()));
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
        }
        return config.apiKey();
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

    private static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }
}
