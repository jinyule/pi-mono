package dev.pi.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentMessage;
import dev.pi.agent.runtime.AgentState;
import dev.pi.ai.stream.Subscription;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class PiRpcMode implements AutoCloseable {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PiInteractiveSession session;
    private final Appendable output;

    private Subscription eventSubscription;
    private Subscription stateSubscription;
    private boolean started;

    public PiRpcMode(PiInteractiveSession session, Appendable output) {
        this.session = Objects.requireNonNull(session, "session");
        this.output = Objects.requireNonNull(output, "output");
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        eventSubscription = session.subscribe(this::writeEventNotification);
        stateSubscription = session.subscribeState(this::writeStateNotification);
    }

    public CompletionStage<Void> handleCommand(String jsonLine) {
        Objects.requireNonNull(jsonLine, "jsonLine");
        start();

        JsonNode command;
        try {
            command = OBJECT_MAPPER.readTree(jsonLine);
        } catch (JsonProcessingException exception) {
            writeResponse(null, "unknown", false, null, "Invalid JSON command");
            return CompletableFuture.completedFuture(null);
        }

        var id = textOrNull(command.get("id"));
        var type = command.path("type").asText("");
        return switch (type) {
            case "prompt" -> requireText(command, "text", type, id)
                .thenCompose(text -> session.prompt(text))
                .thenCompose(ignored -> session.waitForIdle())
                .handle((ignored, throwable) -> {
                    if (throwable != null) {
                        writeResponse(id, type, false, null, rootMessage(throwable));
                    } else {
                        writeResponse(id, type, true, null, null);
                    }
                    return null;
                });
            case "state" -> {
                writeResponse(id, type, true, statePayload(session.state()), null);
                yield CompletableFuture.completedFuture(null);
            }
            case "resume" -> session.resume()
                .thenCompose(ignored -> session.waitForIdle())
                .handle((ignored, throwable) -> {
                    if (throwable != null) {
                        writeResponse(id, type, false, null, rootMessage(throwable));
                    } else {
                        writeResponse(id, type, true, null, null);
                    }
                    return null;
                });
            case "abort" -> {
                session.abort();
                writeResponse(id, type, true, null, null);
                yield CompletableFuture.completedFuture(null);
            }
            default -> {
                writeResponse(id, type, false, null, "Unsupported RPC command: " + type);
                yield CompletableFuture.completedFuture(null);
            }
        };
    }

    @Override
    public void close() {
        if (eventSubscription != null) {
            eventSubscription.unsubscribe();
            eventSubscription = null;
        }
        if (stateSubscription != null) {
            stateSubscription.unsubscribe();
            stateSubscription = null;
        }
        started = false;
    }

    private CompletionStage<String> requireText(JsonNode command, String field, String type, String id) {
        var value = textOrNull(command.get(field));
        if (value == null || value.isBlank()) {
            writeResponse(id, type, false, null, "Command requires non-blank `" + field + "`");
            return CompletableFuture.failedFuture(new IllegalArgumentException("missing " + field));
        }
        return CompletableFuture.completedFuture(value);
    }

    private void writeEventNotification(AgentEvent event) {
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
            case AgentEvent.TurnEnd turnEnd -> {
                node.put("stopReason", turnEnd.message().stopReason().value());
                node.put("toolResultCount", turnEnd.toolResults().size());
            }
            default -> {
            }
        }
        writeJsonLine(node);
    }

    private void writeStateNotification(AgentState state) {
        var node = statePayload(state);
        node.put("type", "state");
        writeJsonLine(node);
    }

    private ObjectNode statePayload(AgentState state) {
        var node = OBJECT_MAPPER.createObjectNode();
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
        return node;
    }

    private void writeResponse(String id, String command, boolean success, ObjectNode data, String error) {
        var node = OBJECT_MAPPER.createObjectNode();
        node.put("type", "response");
        if (id != null) {
            node.put("id", id);
        }
        node.put("command", command);
        node.put("success", success);
        if (data != null) {
            node.set("data", data);
        }
        if (error != null) {
            node.put("error", error);
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
            throw new IllegalStateException("Failed to serialize RPC mode line", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write RPC mode line", exception);
        }
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String rootMessage(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
