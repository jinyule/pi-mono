package dev.pi.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Objects;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "role", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Message.UserMessage.class, name = "user"),
    @JsonSubTypes.Type(value = Message.AssistantMessage.class, name = "assistant"),
    @JsonSubTypes.Type(value = Message.ToolResultMessage.class, name = "toolResult"),
})
public sealed interface Message permits Message.UserMessage, Message.AssistantMessage, Message.ToolResultMessage {
    String role();

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserMessage(
        String role,
        List<UserContent> content,
        long timestamp
    ) implements Message {
        public UserMessage(List<UserContent> content, long timestamp) {
            this("user", content, timestamp);
        }

        public UserMessage {
            if (!"user".equals(role)) {
                throw new IllegalArgumentException("UserMessage role must be 'user'");
            }
            content = List.copyOf(Objects.requireNonNull(content, "content"));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AssistantMessage(
        String role,
        List<AssistantContent> content,
        String api,
        String provider,
        String model,
        Usage usage,
        StopReason stopReason,
        String errorMessage,
        long timestamp
    ) implements Message {
        public AssistantMessage(
            List<AssistantContent> content,
            String api,
            String provider,
            String model,
            Usage usage,
            StopReason stopReason,
            String errorMessage,
            long timestamp
        ) {
            this("assistant", content, api, provider, model, usage, stopReason, errorMessage, timestamp);
        }

        public AssistantMessage {
            if (!"assistant".equals(role)) {
                throw new IllegalArgumentException("AssistantMessage role must be 'assistant'");
            }
            content = List.copyOf(Objects.requireNonNull(content, "content"));
            Objects.requireNonNull(api, "api");
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(model, "model");
            usage = Objects.requireNonNull(usage, "usage");
            stopReason = Objects.requireNonNull(stopReason, "stopReason");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ToolResultMessage(
        String role,
        String toolCallId,
        String toolName,
        List<UserContent> content,
        JsonNode details,
        boolean isError,
        long timestamp
    ) implements Message {
        public ToolResultMessage(
            String toolCallId,
            String toolName,
            List<UserContent> content,
            JsonNode details,
            boolean isError,
            long timestamp
        ) {
            this("toolResult", toolCallId, toolName, content, details, isError, timestamp);
        }

        public ToolResultMessage {
            if (!"toolResult".equals(role)) {
                throw new IllegalArgumentException("ToolResultMessage role must be 'toolResult'");
            }
            Objects.requireNonNull(toolCallId, "toolCallId");
            Objects.requireNonNull(toolName, "toolName");
            content = List.copyOf(Objects.requireNonNull(content, "content"));
            details = details == null ? JsonNodeFactory.instance.nullNode() : details.deepCopy();
        }
    }
}

