package dev.pi.extension.spi;

import dev.pi.agent.runtime.AgentTool;
import java.util.Objects;

public record ToolDefinition<TDetails>(
    AgentTool<TDetails> tool
) {
    public ToolDefinition {
        tool = Objects.requireNonNull(tool, "tool");
    }

    public String name() {
        return tool.name();
    }

    public String label() {
        return tool.label();
    }

    public String description() {
        return tool.description();
    }
}
