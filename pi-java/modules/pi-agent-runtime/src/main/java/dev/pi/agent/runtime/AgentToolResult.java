package dev.pi.agent.runtime;

import dev.pi.ai.model.UserContent;
import java.util.List;
import java.util.Objects;

public record AgentToolResult<TDetails>(
    List<UserContent> content,
    TDetails details
) {
    public AgentToolResult {
        content = List.copyOf(Objects.requireNonNull(content, "content"));
    }
}
