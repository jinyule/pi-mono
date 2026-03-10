package dev.pi.cli;

import dev.pi.agent.runtime.AgentMessage;
import dev.pi.ai.model.AssistantContent;
import dev.pi.ai.model.ImageContent;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.ThinkingContent;
import dev.pi.ai.model.ToolCall;
import dev.pi.ai.model.UserContent;
import java.util.List;

public final class PiMessageRenderer {
    private PiMessageRenderer() {
    }

    public static String renderMessage(AgentMessage message) {
        return switch (message) {
            case AgentMessage.UserMessage userMessage -> "You: " + renderUserContent(userMessage.content());
            case AgentMessage.AssistantMessage assistantMessage -> "Assistant: " + renderAssistantContent(assistantMessage.content());
            case AgentMessage.ToolResultMessage toolResultMessage ->
                "Tool %s: %s".formatted(toolResultMessage.toolName(), renderUserContent(toolResultMessage.content()));
            case AgentMessage.CustomMessage customMessage ->
                "%s: %s".formatted(customMessage.role(), String.valueOf(customMessage.payload()));
        };
    }

    public static String renderStreamingMessage(AgentMessage message) {
        return switch (message) {
            case AgentMessage.AssistantMessage assistantMessage ->
                "Assistant (streaming): " + renderAssistantContent(assistantMessage.content());
            default -> renderMessage(message);
        };
    }

    public static String renderUserContent(List<UserContent> content) {
        return content.stream()
            .map(PiMessageRenderer::renderUserBlock)
            .filter(text -> !text.isBlank())
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
    }

    public static String renderAssistantContent(List<AssistantContent> content) {
        return content.stream()
            .map(PiMessageRenderer::renderAssistantBlock)
            .filter(text -> !text.isBlank())
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
    }

    private static String renderUserBlock(UserContent block) {
        return switch (block) {
            case TextContent textContent -> textContent.text();
            case ImageContent imageContent -> "[image " + imageContent.mimeType() + "]";
        };
    }

    private static String renderAssistantBlock(AssistantContent block) {
        return switch (block) {
            case TextContent textContent -> textContent.text();
            case ThinkingContent thinkingContent -> "Thinking: " + thinkingContent.thinking();
            case ToolCall toolCall -> "Tool call %s(%s)".formatted(toolCall.name(), toolCall.arguments());
        };
    }
}
