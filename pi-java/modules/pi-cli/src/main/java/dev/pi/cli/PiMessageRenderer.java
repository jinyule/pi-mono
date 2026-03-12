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
        return renderMessage(message, false);
    }

    public static String renderMessage(AgentMessage message, boolean hideThinking) {
        return switch (message) {
            case AgentMessage.UserMessage userMessage -> "You: " + renderUserContent(userMessage.content());
            case AgentMessage.AssistantMessage assistantMessage ->
                "Assistant: " + renderAssistantContent(assistantMessage.content(), hideThinking);
            case AgentMessage.ToolResultMessage toolResultMessage ->
                "Tool %s: %s".formatted(toolResultMessage.toolName(), renderUserContent(toolResultMessage.content()));
            case AgentMessage.CustomMessage customMessage ->
                "%s: %s".formatted(customMessage.role(), String.valueOf(customMessage.payload()));
        };
    }

    public static String renderStreamingMessage(AgentMessage message) {
        return renderStreamingMessage(message, false);
    }

    public static String renderStreamingMessage(AgentMessage message, boolean hideThinking) {
        return switch (message) {
            case AgentMessage.AssistantMessage assistantMessage ->
                "Assistant (streaming): " + renderAssistantContent(assistantMessage.content(), hideThinking);
            default -> renderMessage(message, hideThinking);
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
        return renderAssistantContent(content, false);
    }

    public static String renderAssistantContent(List<AssistantContent> content, boolean hideThinking) {
        return content.stream()
            .map(block -> renderAssistantBlock(block, hideThinking))
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

    private static String renderAssistantBlock(AssistantContent block, boolean hideThinking) {
        return switch (block) {
            case TextContent textContent -> textContent.text();
            case ThinkingContent thinkingContent -> hideThinking ? "" : "Thinking: " + thinkingContent.thinking();
            case ToolCall toolCall -> "Tool call %s(%s)".formatted(toolCall.name(), toolCall.arguments());
        };
    }
}
