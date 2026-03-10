package dev.pi.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.pi.agent.runtime.AgentTool;
import dev.pi.agent.runtime.AgentToolResult;
import dev.pi.ai.model.TextContent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public final class LsTool implements AgentTool<LsToolDetails> {
    private static final ObjectNode PARAMETERS_SCHEMA = createParametersSchema();
    private static final int DEFAULT_LIMIT = 500;

    private final Path cwd;
    private final LsOperations operations;

    public LsTool(Path cwd) {
        this(cwd, LsToolOptions.defaults());
    }

    public LsTool(Path cwd, LsToolOptions options) {
        this.cwd = Objects.requireNonNull(cwd, "cwd");
        var appliedOptions = options == null ? LsToolOptions.defaults() : options;
        this.operations = appliedOptions.operations();
    }

    @Override
    public String name() {
        return "ls";
    }

    @Override
    public String label() {
        return "ls";
    }

    @Override
    public String description() {
        return "List directory contents. Returns entries sorted alphabetically, with '/' suffix for directories. "
            + "Includes dotfiles. Output is truncated to %d entries or %dKB (whichever is hit first)."
            .formatted(DEFAULT_LIMIT, TextTruncator.DEFAULT_MAX_BYTES / 1024);
    }

    @Override
    public JsonNode parametersSchema() {
        return PARAMETERS_SCHEMA.deepCopy();
    }

    @Override
    public CompletionStage<AgentToolResult<LsToolDetails>> execute(
        String toolCallId,
        JsonNode arguments,
        Consumer<AgentToolResult<LsToolDetails>> onUpdate
    ) {
        Objects.requireNonNull(toolCallId, "toolCallId");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(onUpdate, "onUpdate");

        var future = new CompletableFuture<AgentToolResult<LsToolDetails>>();
        Thread.ofVirtual().name("pi-tools-ls").start(() -> {
            try {
                future.complete(executeOnce(parseRequest(arguments)));
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private AgentToolResult<LsToolDetails> executeOnce(LsRequest request) throws IOException {
        var dirPath = ToolPaths.resolveToCwd(request.path() == null ? "." : request.path(), cwd);
        var effectiveLimit = request.limit() == null ? DEFAULT_LIMIT : request.limit();

        if (!operations.exists(dirPath)) {
            throw new IOException("Path not found: " + dirPath);
        }
        if (!operations.isDirectory(dirPath)) {
            throw new IOException("Not a directory: " + dirPath);
        }

        List<String> entries;
        try {
            entries = new ArrayList<>(operations.readdir(dirPath));
        } catch (IOException exception) {
            throw new IOException("Cannot read directory: " + exception.getMessage(), exception);
        }

        entries.sort(Comparator.comparing(String::toLowerCase));

        var results = new ArrayList<String>();
        var entryLimitReached = false;
        for (var entry : entries) {
            if (results.size() >= effectiveLimit) {
                entryLimitReached = true;
                break;
            }
            var suffix = "";
            try {
                if (operations.isDirectory(dirPath.resolve(entry))) {
                    suffix = "/";
                }
            } catch (IOException exception) {
                continue;
            }
            results.add(entry + suffix);
        }

        if (results.isEmpty()) {
            return new AgentToolResult<>(List.of(new TextContent("(empty directory)", null)), null);
        }

        var rawOutput = String.join("\n", results);
        var truncation = TextTruncator.truncateHead(rawOutput, new TruncationOptions(Integer.MAX_VALUE, TextTruncator.DEFAULT_MAX_BYTES));
        var output = truncation.content();
        var notices = new ArrayList<String>();

        if (entryLimitReached) {
            notices.add("%d entries limit reached. Use limit=%d for more".formatted(effectiveLimit, effectiveLimit * 2));
        }
        if (truncation.truncated()) {
            notices.add("%s limit reached".formatted(TextTruncator.formatSize(TextTruncator.DEFAULT_MAX_BYTES)));
        }
        if (!notices.isEmpty()) {
            output += "\n\n[" + String.join(". ", notices) + "]";
        }

        return new AgentToolResult<>(
            List.of(new TextContent(output, null)),
            !entryLimitReached && !truncation.truncated()
                ? null
                : new LsToolDetails(truncation.truncated() ? truncation : null, entryLimitReached ? effectiveLimit : null)
        );
    }

    private static LsRequest parseRequest(JsonNode arguments) {
        return new LsRequest(
            arguments.has("path") ? arguments.path("path").asText() : null,
            arguments.has("limit") ? arguments.path("limit").intValue() : null
        );
    }

    private static ObjectNode createParametersSchema() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        var properties = schema.putObject("properties");
        properties.putObject("path")
            .put("type", "string")
            .put("description", "Directory to list (default: current directory)");
        properties.putObject("limit")
            .put("type", "number")
            .put("description", "Maximum number of entries to return (default: 500)");
        return schema;
    }

    private record LsRequest(
        String path,
        Integer limit
    ) {
    }
}
