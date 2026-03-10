package dev.pi.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.pi.agent.runtime.AgentTool;
import dev.pi.agent.runtime.AgentToolResult;
import dev.pi.ai.model.TextContent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public final class FindTool implements AgentTool<FindToolDetails> {
    private static final ObjectNode PARAMETERS_SCHEMA = createParametersSchema();
    private static final int DEFAULT_LIMIT = 1000;

    private final Path cwd;
    private final FindOperations operations;

    public FindTool(Path cwd) {
        this(cwd, FindToolOptions.defaults());
    }

    public FindTool(Path cwd, FindToolOptions options) {
        this.cwd = Objects.requireNonNull(cwd, "cwd");
        var appliedOptions = options == null ? FindToolOptions.defaults() : options;
        this.operations = appliedOptions.operations();
    }

    @Override
    public String name() {
        return "find";
    }

    @Override
    public String label() {
        return "find";
    }

    @Override
    public String description() {
        return "Search for files by glob pattern. Returns matching file paths relative to the search directory. "
            + "Respects .gitignore. Output is truncated to %d results or %dKB (whichever is hit first)."
            .formatted(DEFAULT_LIMIT, TextTruncator.DEFAULT_MAX_BYTES / 1024);
    }

    @Override
    public JsonNode parametersSchema() {
        return PARAMETERS_SCHEMA.deepCopy();
    }

    @Override
    public CompletionStage<AgentToolResult<FindToolDetails>> execute(
        String toolCallId,
        JsonNode arguments,
        Consumer<AgentToolResult<FindToolDetails>> onUpdate
    ) {
        Objects.requireNonNull(toolCallId, "toolCallId");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(onUpdate, "onUpdate");

        var future = new CompletableFuture<AgentToolResult<FindToolDetails>>();
        Thread.ofVirtual().name("pi-tools-find").start(() -> {
            try {
                future.complete(executeOnce(parseRequest(arguments)));
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private AgentToolResult<FindToolDetails> executeOnce(FindRequest request) throws IOException {
        var searchPath = ToolPaths.resolveToCwd(request.path() == null ? "." : request.path(), cwd);
        if (!operations.exists(searchPath)) {
            throw new IOException("Path not found: " + searchPath);
        }

        var effectiveLimit = request.limit() == null ? DEFAULT_LIMIT : request.limit();
        var results = operations.glob(request.pattern(), searchPath, new FindOperations.SearchOptions(effectiveLimit));
        if (results.isEmpty()) {
            return new AgentToolResult<>(List.of(new TextContent("No files found matching pattern", null)), null);
        }

        var resultLimitReached = results.size() >= effectiveLimit ? effectiveLimit : null;
        var rawOutput = String.join("\n", results);
        var truncation = TextTruncator.truncateHead(rawOutput, new TruncationOptions(Integer.MAX_VALUE, TextTruncator.DEFAULT_MAX_BYTES));
        var output = truncation.content();
        var notices = new java.util.ArrayList<String>();

        if (resultLimitReached != null) {
            notices.add("%d results limit reached. Use limit=%d for more, or refine pattern".formatted(effectiveLimit, effectiveLimit * 2));
        }
        if (truncation.truncated()) {
            notices.add("%s limit reached".formatted(TextTruncator.formatSize(TextTruncator.DEFAULT_MAX_BYTES)));
        }
        if (!notices.isEmpty()) {
            output += "\n\n[" + String.join(". ", notices) + "]";
        }

        return new AgentToolResult<>(
            List.of(new TextContent(output, null)),
            resultLimitReached == null && !truncation.truncated()
                ? null
                : new FindToolDetails(truncation.truncated() ? truncation : null, resultLimitReached)
        );
    }

    private static FindRequest parseRequest(JsonNode arguments) {
        var pattern = arguments.path("pattern").asText(null);
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("find.pattern must be a non-empty string");
        }
        return new FindRequest(
            pattern,
            arguments.has("path") ? arguments.path("path").asText() : null,
            arguments.has("limit") ? arguments.path("limit").intValue() : null
        );
    }

    private static ObjectNode createParametersSchema() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        var properties = schema.putObject("properties");
        properties.putObject("pattern")
            .put("type", "string")
            .put("description", "Glob pattern to match files, e.g. '*.ts', '**/*.json', or 'src/**/*.spec.ts'");
        properties.putObject("path")
            .put("type", "string")
            .put("description", "Directory to search in (default: current directory)");
        properties.putObject("limit")
            .put("type", "number")
            .put("description", "Maximum number of results (default: 1000)");
        schema.putArray("required").add("pattern");
        return schema;
    }

    private record FindRequest(
        String pattern,
        String path,
        Integer limit
    ) {
    }
}
