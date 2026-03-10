package dev.pi.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.pi.agent.runtime.AgentTool;
import dev.pi.agent.runtime.AgentToolResult;
import dev.pi.ai.model.TextContent;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public final class WriteTool implements AgentTool<Void> {
    private static final ObjectNode PARAMETERS_SCHEMA = createParametersSchema();

    private final Path cwd;
    private final WriteOperations operations;

    public WriteTool(Path cwd) {
        this(cwd, WriteToolOptions.defaults());
    }

    public WriteTool(Path cwd, WriteToolOptions options) {
        this.cwd = Objects.requireNonNull(cwd, "cwd");
        var appliedOptions = options == null ? WriteToolOptions.defaults() : options;
        this.operations = appliedOptions.operations();
    }

    @Override
    public String name() {
        return "write";
    }

    @Override
    public String label() {
        return "write";
    }

    @Override
    public String description() {
        return "Write content to a file. Creates the file if it doesn't exist, overwrites if it does. "
            + "Automatically creates parent directories.";
    }

    @Override
    public JsonNode parametersSchema() {
        return PARAMETERS_SCHEMA.deepCopy();
    }

    @Override
    public CompletionStage<AgentToolResult<Void>> execute(
        String toolCallId,
        JsonNode arguments,
        Consumer<AgentToolResult<Void>> onUpdate
    ) {
        Objects.requireNonNull(toolCallId, "toolCallId");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(onUpdate, "onUpdate");

        try {
            var request = parseRequest(arguments);
            var absolutePath = ToolPaths.resolveToCwd(request.path(), cwd);
            var parent = absolutePath.getParent();
            if (parent != null) {
                operations.mkdir(parent);
            }
            operations.writeFile(absolutePath, request.content());
            return CompletableFuture.completedFuture(new AgentToolResult<>(
                List.of(new TextContent("Successfully wrote %d bytes to %s".formatted(request.content().length(), request.path()), null)),
                null
            ));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static WriteRequest parseRequest(JsonNode arguments) {
        var path = arguments.path("path").asText(null);
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("write.path must be a non-empty string");
        }

        if (!arguments.has("content")) {
            throw new IllegalArgumentException("write.content must be provided");
        }
        return new WriteRequest(path, arguments.path("content").asText());
    }

    private static ObjectNode createParametersSchema() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        var properties = schema.putObject("properties");
        properties.putObject("path")
            .put("type", "string")
            .put("description", "Path to the file to write (relative or absolute)");
        properties.putObject("content")
            .put("type", "string")
            .put("description", "Content to write to the file");
        schema.putArray("required").add("path").add("content");
        return schema;
    }

    private record WriteRequest(
        String path,
        String content
    ) {
    }
}
