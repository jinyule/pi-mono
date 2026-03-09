package dev.pi.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Objects;

public record Tool(
    String name,
    String description,
    JsonNode parametersSchema
) {
    public Tool {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        parametersSchema = parametersSchema == null
            ? JsonNodeFactory.instance.objectNode()
            : parametersSchema.deepCopy();
    }
}

