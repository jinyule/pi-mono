package dev.pi.agent.runtime;

import dev.pi.ai.model.Message;
import java.util.List;
import java.util.Objects;

public final class AgentMessages {
    private AgentMessages() {
    }

    public static AgentMessage fromLlmMessage(Message message) {
        Objects.requireNonNull(message, "message");
        return switch (message) {
            case Message.UserMessage userMessage -> new AgentMessage.UserMessage(userMessage.content(), userMessage.timestamp());
            case Message.AssistantMessage assistantMessage -> new AgentMessage.AssistantMessage(
                assistantMessage.content(),
                assistantMessage.api(),
                assistantMessage.provider(),
                assistantMessage.model(),
                assistantMessage.usage(),
                assistantMessage.stopReason(),
                assistantMessage.errorMessage(),
                assistantMessage.timestamp()
            );
            case Message.ToolResultMessage toolResultMessage -> new AgentMessage.ToolResultMessage(
                toolResultMessage.toolCallId(),
                toolResultMessage.toolName(),
                toolResultMessage.content(),
                toolResultMessage.details(),
                toolResultMessage.isError(),
                toolResultMessage.timestamp()
            );
        };
    }

    public static Message toLlmMessage(AgentMessage message) {
        Objects.requireNonNull(message, "message");
        return switch (message) {
            case AgentMessage.UserMessage userMessage -> new Message.UserMessage(userMessage.content(), userMessage.timestamp());
            case AgentMessage.AssistantMessage assistantMessage -> new Message.AssistantMessage(
                assistantMessage.content(),
                assistantMessage.api(),
                assistantMessage.provider(),
                assistantMessage.model(),
                assistantMessage.usage(),
                assistantMessage.stopReason(),
                assistantMessage.errorMessage(),
                assistantMessage.timestamp()
            );
            case AgentMessage.ToolResultMessage toolResultMessage -> new Message.ToolResultMessage(
                toolResultMessage.toolCallId(),
                toolResultMessage.toolName(),
                toolResultMessage.content(),
                toolResultMessage.details(),
                toolResultMessage.isError(),
                toolResultMessage.timestamp()
            );
            case AgentMessage.CustomMessage customMessage -> throw new IllegalArgumentException(
                "Custom agent message cannot be converted to LLM message: " + customMessage.role()
            );
        };
    }

    public static List<Message> toLlmMessages(List<AgentMessage> messages) {
        Objects.requireNonNull(messages, "messages");
        return messages.stream()
            .map(AgentMessages::toLlmMessage)
            .toList();
    }
}
