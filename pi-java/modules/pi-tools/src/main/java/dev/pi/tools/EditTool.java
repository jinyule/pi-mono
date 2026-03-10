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

public final class EditTool implements AgentTool<EditToolDetails> {
    private static final ObjectNode PARAMETERS_SCHEMA = createParametersSchema();

    private final Path cwd;
    private final EditOperations operations;

    public EditTool(Path cwd) {
        this(cwd, EditToolOptions.defaults());
    }

    public EditTool(Path cwd, EditToolOptions options) {
        this.cwd = Objects.requireNonNull(cwd, "cwd");
        var appliedOptions = options == null ? EditToolOptions.defaults() : options;
        this.operations = appliedOptions.operations();
    }

    @Override
    public String name() {
        return "edit";
    }

    @Override
    public String label() {
        return "edit";
    }

    @Override
    public String description() {
        return "Edit a file by replacing exact text. The oldText must match exactly "
            + "(including whitespace). Use this for precise, surgical edits.";
    }

    @Override
    public JsonNode parametersSchema() {
        return PARAMETERS_SCHEMA.deepCopy();
    }

    @Override
    public CompletionStage<AgentToolResult<EditToolDetails>> execute(
        String toolCallId,
        JsonNode arguments,
        Consumer<AgentToolResult<EditToolDetails>> onUpdate
    ) {
        Objects.requireNonNull(toolCallId, "toolCallId");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(onUpdate, "onUpdate");

        try {
            return CompletableFuture.completedFuture(executeOnce(parseRequest(arguments)));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private AgentToolResult<EditToolDetails> executeOnce(EditRequest request) throws IOException {
        var absolutePath = ToolPaths.resolveToCwd(request.path(), cwd);
        try {
            operations.access(absolutePath);
        } catch (IOException exception) {
            throw new IOException("File not found: " + request.path(), exception);
        }

        var rawContent = operations.readFile(absolutePath);
        var stripped = EditDiffs.stripBom(rawContent);
        var content = stripped.text();
        var bom = stripped.bom();
        var originalEnding = EditDiffs.detectLineEnding(content);
        var normalizedContent = EditDiffs.normalizeToLf(content);
        var normalizedOldText = EditDiffs.normalizeToLf(request.oldText());
        var normalizedNewText = EditDiffs.normalizeToLf(request.newText());
        var matchResult = EditDiffs.fuzzyFindText(normalizedContent, normalizedOldText);

        if (!matchResult.found()) {
            throw new IOException(
                "Could not find the exact text in %s. The old text must match exactly including all whitespace and newlines."
                    .formatted(request.path())
            );
        }

        var occurrences = countOccurrences(
            EditDiffs.normalizeForFuzzyMatch(normalizedContent),
            EditDiffs.normalizeForFuzzyMatch(normalizedOldText)
        );
        if (occurrences > 1) {
            throw new IOException(
                "Found %d occurrences of the text in %s. The text must be unique. Please provide more context to make it unique."
                    .formatted(occurrences, request.path())
            );
        }

        var baseContent = matchResult.contentForReplacement();
        var newContent = baseContent.substring(0, matchResult.index())
            + normalizedNewText
            + baseContent.substring(matchResult.index() + matchResult.matchLength());

        if (baseContent.equals(newContent)) {
            throw new IOException(
                "No changes made to %s. The replacement produced identical content. This might indicate an issue with special characters or the text not existing as expected."
                    .formatted(request.path())
            );
        }

        var finalContent = bom + EditDiffs.restoreLineEndings(newContent, originalEnding);
        operations.writeFile(absolutePath, finalContent);

        var diff = EditDiffs.generateDiffString(baseContent, newContent);
        return new AgentToolResult<>(
            List.of(new TextContent("Successfully replaced text in %s.".formatted(request.path()), null)),
            new EditToolDetails(diff.diff(), diff.firstChangedLine())
        );
    }

    private static int countOccurrences(String content, String needle) {
        if (needle.isEmpty()) {
            return 0;
        }
        var count = 0;
        var index = 0;
        while ((index = content.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static EditRequest parseRequest(JsonNode arguments) {
        var path = arguments.path("path").asText(null);
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("edit.path must be a non-empty string");
        }

        if (!arguments.has("oldText")) {
            throw new IllegalArgumentException("edit.oldText must be provided");
        }
        if (!arguments.has("newText")) {
            throw new IllegalArgumentException("edit.newText must be provided");
        }

        return new EditRequest(path, arguments.path("oldText").asText(), arguments.path("newText").asText());
    }

    private static ObjectNode createParametersSchema() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        var properties = schema.putObject("properties");
        properties.putObject("path")
            .put("type", "string")
            .put("description", "Path to the file to edit (relative or absolute)");
        properties.putObject("oldText")
            .put("type", "string")
            .put("description", "Exact text to find and replace (must match exactly)");
        properties.putObject("newText")
            .put("type", "string")
            .put("description", "New text to replace the old text with");
        schema.putArray("required").add("path").add("oldText").add("newText");
        return schema;
    }

    private record EditRequest(
        String path,
        String oldText,
        String newText
    ) {
    }
}
