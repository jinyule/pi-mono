package dev.pi.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TextContent(
    String type,
    String text,
    String textSignature
) implements UserContent, AssistantContent {
    public TextContent(String text, String textSignature) {
        this("text", text, textSignature);
    }

    public TextContent {
        if (!"text".equals(type)) {
            throw new IllegalArgumentException("TextContent type must be 'text'");
        }
        Objects.requireNonNull(text, "text");
    }
}

