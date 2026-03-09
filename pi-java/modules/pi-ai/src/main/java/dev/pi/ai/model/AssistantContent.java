package dev.pi.ai.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextContent.class, name = "text"),
    @JsonSubTypes.Type(value = ThinkingContent.class, name = "thinking"),
    @JsonSubTypes.Type(value = ToolCall.class, name = "toolCall"),
})
public sealed interface AssistantContent permits TextContent, ThinkingContent, ToolCall {
    String type();
}

