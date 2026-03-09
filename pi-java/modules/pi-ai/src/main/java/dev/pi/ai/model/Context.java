package dev.pi.ai.model;

import java.util.List;
import java.util.Objects;

public record Context(
    String systemPrompt,
    List<Message> messages,
    List<Tool> tools
) {
    public Context {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}

