package dev.pi.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import dev.pi.ai.model.Tool;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface AgentTool<TDetails> {
    String name();

    String label();

    String description();

    JsonNode parametersSchema();

    CompletionStage<AgentToolResult<TDetails>> execute(
        String toolCallId,
        JsonNode arguments,
        Consumer<AgentToolResult<TDetails>> onUpdate
    );

    default CompletionStage<AgentToolResult<TDetails>> execute(
        String toolCallId,
        JsonNode arguments,
        Consumer<AgentToolResult<TDetails>> onUpdate,
        BooleanSupplier cancelled
    ) {
        Objects.requireNonNull(cancelled, "cancelled");
        return execute(toolCallId, arguments, onUpdate);
    }

    default Tool toTool() {
        return new Tool(name(), description(), Objects.requireNonNull(parametersSchema(), "parametersSchema"));
    }
}
