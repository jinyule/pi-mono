package dev.pi.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolCall(
    String type,
    String id,
    String name,
    JsonNode arguments,
    String thoughtSignature
) implements AssistantContent {
    public ToolCall(String id, String name, JsonNode arguments, String thoughtSignature) {
        this("toolCall", id, name, arguments, thoughtSignature);
    }

    public ToolCall {
        if (!"toolCall".equals(type)) {
            throw new IllegalArgumentException("ToolCall type must be 'toolCall'");
        }
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        arguments = arguments == null ? JsonNodeFactory.instance.objectNode() : arguments.deepCopy();
    }
}

