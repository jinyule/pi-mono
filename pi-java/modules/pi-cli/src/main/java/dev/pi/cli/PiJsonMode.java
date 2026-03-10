package dev.pi.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentMessage;
import dev.pi.agent.runtime.AgentState;
import dev.pi.ai.stream.Subscription;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public final class PiJsonMode {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PiInteractiveSession session;
    private final Appendable output;

    public PiJsonMode(PiInteractiveSession session, Appendable output) {
        this.session = Objects.requireNonNull(session, "session");
        this.output = Objects.requireNonNull(output, "output");
    }

    public CompletionStage<Void> run(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("JSON mode requires a non-blank prompt");
        }

        var eventSubscription = session.subscribe(this::writeEventLine);
        var stateSubscription = session.subscribeState(this::writeStateLine);

        return session.prompt(prompt)
            .thenCompose(ignored -> session.waitForIdle())
            .whenComplete((ignored, throwable) -> {
                stateSubscription.unsubscribe();
                eventSubscription.unsubscribe();
            });
    }

    private void writeEventLine(AgentEvent event) {
        var node = OBJECT_MAPPER.createObjectNode();
        node.put("type", "event");
        node.put("eventType", event.type());
        switch (event) {
            case AgentEvent.MessageStart messageStart -> {
                node.put("role", messageStart.message().role());
                node.put("text", renderMessageText(messageStart.message()));
            }
            case AgentEvent.MessageUpdate messageUpdate -> {
                node.put("role", messageUpdate.message().role());
                node.put("text", PiMessageRenderer.renderAssistantContent(messageUpdate.message().content()));
                node.put("assistantEventType", messageUpdate.assistantMessageEvent().type());
            }
            case AgentEvent.MessageEnd messageEnd -> {
                node.put("role", messageEnd.message().role());
                node.put("text", renderMessageText(messageEnd.message()));
            }
            case AgentEvent.ToolExecutionStart toolExecutionStart -> {
                node.put("toolCallId", toolExecutionStart.toolCallId());
                node.put("toolName", toolExecutionStart.toolName());
                node.set("arguments", toolExecutionStart.arguments().deepCopy());
            }
            case AgentEvent.ToolExecutionUpdate toolExecutionUpdate -> {
                node.put("toolCallId", toolExecutionUpdate.toolCallId());
                node.put("toolName", toolExecutionUpdate.toolName());
                node.set("arguments", toolExecutionUpdate.arguments().deepCopy());
            }
            case AgentEvent.ToolExecutionEnd toolExecutionEnd -> {
                node.put("toolCallId", toolExecutionEnd.toolCallId());
                node.put("toolName", toolExecutionEnd.toolName());
                node.put("isError", toolExecutionEnd.isError());
            }
            case AgentEvent.TurnEnd turnEnd -> {
                node.put("stopReason", turnEnd.message().stopReason().value());
                node.put("toolResultCount", turnEnd.toolResults().size());
            }
            case AgentEvent.AgentEnd agentEnd -> node.put("messageCount", agentEnd.messages().size());
            default -> {
            }
        }
        writeJsonLine(node);
    }

    private void writeStateLine(AgentState state) {
        var node = OBJECT_MAPPER.createObjectNode();
        node.put("type", "state");
        node.put("sessionId", session.sessionId());
        node.put("modelProvider", state.model().provider());
        node.put("modelId", state.model().id());
        node.put("thinkingLevel", state.thinkingLevel() == null ? "off" : state.thinkingLevel().value());
        node.put("messageCount", state.messages().size());
        node.put("streaming", state.isStreaming());
        node.put("error", state.error());
        if (state.streamMessage() != null) {
            node.put("streamText", renderMessageText(state.streamMessage()));
        }
        ArrayNode pending = node.putArray("pendingToolCalls");
        for (var toolCallId : state.pendingToolCalls()) {
            pending.add(toolCallId);
        }
        writeJsonLine(node);
    }

    private static String renderMessageText(AgentMessage message) {
        return switch (message) {
            case AgentMessage.UserMessage userMessage -> PiMessageRenderer.renderUserContent(userMessage.content());
            case AgentMessage.AssistantMessage assistantMessage -> PiMessageRenderer.renderAssistantContent(assistantMessage.content());
            case AgentMessage.ToolResultMessage toolResultMessage -> PiMessageRenderer.renderUserContent(toolResultMessage.content());
            case AgentMessage.CustomMessage customMessage -> String.valueOf(customMessage.payload());
        };
    }

    private void writeJsonLine(ObjectNode node) {
        try {
            output.append(OBJECT_MAPPER.writeValueAsString(node));
            output.append(System.lineSeparator());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize JSON mode line", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write JSON mode line", exception);
        }
    }
}
