package dev.pi.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThinkingContent(
    String type,
    String thinking,
    String thinkingSignature,
    boolean redacted
) implements AssistantContent {
    public ThinkingContent(String thinking, String thinkingSignature, boolean redacted) {
        this("thinking", thinking, thinkingSignature, redacted);
    }

    public ThinkingContent {
        if (!"thinking".equals(type)) {
            throw new IllegalArgumentException("ThinkingContent type must be 'thinking'");
        }
        Objects.requireNonNull(thinking, "thinking");
    }
}

