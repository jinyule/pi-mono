package dev.pi.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import dev.pi.ai.model.AssistantMessageEvent;
import java.util.List;
import java.util.Objects;

public sealed interface AgentEvent permits
    AgentEvent.AgentStart,
    AgentEvent.AgentEnd,
    AgentEvent.TurnStart,
    AgentEvent.TurnEnd,
    AgentEvent.MessageStart,
    AgentEvent.MessageUpdate,
    AgentEvent.MessageEnd,
    AgentEvent.ToolExecutionStart,
    AgentEvent.ToolExecutionUpdate,
    AgentEvent.ToolExecutionEnd {

    String type();

    record AgentStart(String type) implements AgentEvent {
        public AgentStart() {
            this("agent_start");
        }

        public AgentStart {
            requireType(type, "agent_start");
        }
    }

    record AgentEnd(String type, List<AgentMessage> messages) implements AgentEvent {
        public AgentEnd(List<AgentMessage> messages) {
            this("agent_end", messages);
        }

        public AgentEnd {
            requireType(type, "agent_end");
            messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        }
    }

    record TurnStart(String type) implements AgentEvent {
        public TurnStart() {
            this("turn_start");
        }

        public TurnStart {
            requireType(type, "turn_start");
        }
    }

    record TurnEnd(
        String type,
        AgentMessage.AssistantMessage message,
        List<AgentMessage.ToolResultMessage> toolResults
    ) implements AgentEvent {
        public TurnEnd(AgentMessage.AssistantMessage message, List<AgentMessage.ToolResultMessage> toolResults) {
            this("turn_end", message, toolResults);
        }

        public TurnEnd {
            requireType(type, "turn_end");
            Objects.requireNonNull(message, "message");
            toolResults = List.copyOf(Objects.requireNonNull(toolResults, "toolResults"));
        }
    }

    record MessageStart(String type, AgentMessage message) implements AgentEvent {
        public MessageStart(AgentMessage message) {
            this("message_start", message);
        }

        public MessageStart {
            requireType(type, "message_start");
            Objects.requireNonNull(message, "message");
        }
    }

    record MessageUpdate(
        String type,
        AgentMessage.AssistantMessage message,
        AssistantMessageEvent assistantMessageEvent
    ) implements AgentEvent {
        public MessageUpdate(AgentMessage.AssistantMessage message, AssistantMessageEvent assistantMessageEvent) {
            this("message_update", message, assistantMessageEvent);
        }

        public MessageUpdate {
            requireType(type, "message_update");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(assistantMessageEvent, "assistantMessageEvent");
        }
    }

    record MessageEnd(String type, AgentMessage message) implements AgentEvent {
        public MessageEnd(AgentMessage message) {
            this("message_end", message);
        }

        public MessageEnd {
            requireType(type, "message_end");
            Objects.requireNonNull(message, "message");
        }
    }

    record ToolExecutionStart(
        String type,
        String toolCallId,
        String toolName,
        JsonNode arguments
    ) implements AgentEvent {
        public ToolExecutionStart(String toolCallId, String toolName, JsonNode arguments) {
            this("tool_execution_start", toolCallId, toolName, arguments);
        }

        public ToolExecutionStart {
            requireType(type, "tool_execution_start");
            Objects.requireNonNull(toolCallId, "toolCallId");
            Objects.requireNonNull(toolName, "toolName");
            Objects.requireNonNull(arguments, "arguments");
        }
    }

    record ToolExecutionUpdate(
        String type,
        String toolCallId,
        String toolName,
        JsonNode arguments,
        AgentToolResult<?> partialResult
    ) implements AgentEvent {
        public ToolExecutionUpdate(
            String toolCallId,
            String toolName,
            JsonNode arguments,
            AgentToolResult<?> partialResult
        ) {
            this("tool_execution_update", toolCallId, toolName, arguments, partialResult);
        }

        public ToolExecutionUpdate {
            requireType(type, "tool_execution_update");
            Objects.requireNonNull(toolCallId, "toolCallId");
            Objects.requireNonNull(toolName, "toolName");
            Objects.requireNonNull(arguments, "arguments");
            Objects.requireNonNull(partialResult, "partialResult");
        }
    }

    record ToolExecutionEnd(
        String type,
        String toolCallId,
        String toolName,
        AgentToolResult<?> result,
        boolean isError
    ) implements AgentEvent {
        public ToolExecutionEnd(
            String toolCallId,
            String toolName,
            AgentToolResult<?> result,
            boolean isError
        ) {
            this("tool_execution_end", toolCallId, toolName, result, isError);
        }

        public ToolExecutionEnd {
            requireType(type, "tool_execution_end");
            Objects.requireNonNull(toolCallId, "toolCallId");
            Objects.requireNonNull(toolName, "toolName");
            Objects.requireNonNull(result, "result");
        }
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("Event type must be '" + expected + "'");
        }
    }
}
