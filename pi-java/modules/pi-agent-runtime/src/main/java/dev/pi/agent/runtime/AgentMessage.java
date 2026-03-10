package dev.pi.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.AssistantContent;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.ai.model.UserContent;
import java.util.List;
import java.util.Objects;

public sealed interface AgentMessage permits
    AgentMessage.UserMessage,
    AgentMessage.AssistantMessage,
    AgentMessage.ToolResultMessage,
    AgentMessage.CustomMessage {

    String role();

    long timestamp();

    record UserMessage(
        String role,
        List<UserContent> content,
        long timestamp
    ) implements AgentMessage {
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
    ) implements AgentMessage {
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

        public static AssistantMessage error(
            String api,
            String provider,
            String model,
            String errorMessage,
            long timestamp
        ) {
            return new AssistantMessage(
                List.of(new TextContent("", null)),
                api,
                provider,
                model,
                new Usage(0, 0, 0, 0, 0, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                StopReason.ERROR,
                errorMessage,
                timestamp
            );
        }
    }

    record ToolResultMessage(
        String role,
        String toolCallId,
        String toolName,
        List<UserContent> content,
        JsonNode details,
        boolean isError,
        long timestamp
    ) implements AgentMessage {
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

    record CustomMessage(
        String role,
        Object payload,
        long timestamp
    ) implements AgentMessage {
        public CustomMessage {
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("CustomMessage role must not be blank");
            }
            Objects.requireNonNull(payload, "payload");
        }
    }
}
