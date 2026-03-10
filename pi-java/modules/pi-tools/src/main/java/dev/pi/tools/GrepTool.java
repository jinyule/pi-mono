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
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class GrepTool implements AgentTool<GrepToolDetails> {
    private static final ObjectNode PARAMETERS_SCHEMA = createParametersSchema();
    private static final int DEFAULT_LIMIT = 100;

    private final Path cwd;
    private final GrepOperations operations;
    private final RipgrepRunner ripgrepRunner;

    public GrepTool(Path cwd) {
        this(cwd, GrepToolOptions.defaults());
    }

    public GrepTool(Path cwd, GrepToolOptions options) {
        this.cwd = Objects.requireNonNull(cwd, "cwd");
        var appliedOptions = options == null ? GrepToolOptions.defaults() : options;
        this.operations = appliedOptions.operations();
        this.ripgrepRunner = appliedOptions.ripgrepRunner();
    }

    @Override
    public String name() {
        return "grep";
    }

    @Override
    public String label() {
        return "grep";
    }

    @Override
    public String description() {
        return "Search file contents for a pattern. Returns matching lines with file paths and line numbers. "
            + "Respects .gitignore. Output is truncated to %d matches or %dKB (whichever is hit first). "
            + "Long lines are truncated to %d chars."
            .formatted(DEFAULT_LIMIT, TextTruncator.DEFAULT_MAX_BYTES / 1024, TextTruncator.GREP_MAX_LINE_LENGTH);
    }

    @Override
    public JsonNode parametersSchema() {
        return PARAMETERS_SCHEMA.deepCopy();
    }

    @Override
    public CompletionStage<AgentToolResult<GrepToolDetails>> execute(
        String toolCallId,
        JsonNode arguments,
        Consumer<AgentToolResult<GrepToolDetails>> onUpdate
    ) {
        return execute(toolCallId, arguments, onUpdate, () -> false);
    }

    @Override
    public CompletionStage<AgentToolResult<GrepToolDetails>> execute(
        String toolCallId,
        JsonNode arguments,
        Consumer<AgentToolResult<GrepToolDetails>> onUpdate,
        BooleanSupplier cancelled
    ) {
        Objects.requireNonNull(toolCallId, "toolCallId");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(onUpdate, "onUpdate");
        Objects.requireNonNull(cancelled, "cancelled");

        var future = new CompletableFuture<AgentToolResult<GrepToolDetails>>();
        Thread.ofVirtual().name("pi-tools-grep").start(() -> {
            try {
                future.complete(executeOnce(parseRequest(arguments), cancelled));
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private AgentToolResult<GrepToolDetails> executeOnce(GrepRequest request, BooleanSupplier cancelled)
        throws IOException, InterruptedException {
        var searchPath = ToolPaths.resolveToCwd(request.path() == null ? "." : request.path(), cwd);
        final boolean isDirectory;
        try {
            isDirectory = operations.isDirectory(searchPath);
        } catch (IOException exception) {
            throw new IOException("Path not found: " + searchPath, exception);
        }

        var effectiveLimit = Math.max(1, request.limit() == null ? DEFAULT_LIMIT : request.limit());
        var searchResult = ripgrepRunner.search(new RipgrepRunner.SearchRequest(
            request.pattern(),
            searchPath,
            request.glob(),
            Boolean.TRUE.equals(request.ignoreCase()),
            Boolean.TRUE.equals(request.literal()),
            effectiveLimit
        ), cancelled);

        if (searchResult.matches().isEmpty()) {
            return new AgentToolResult<>(List.of(new TextContent("No matches found", null)), null);
        }

        var fileCache = new HashMap<Path, List<String>>();
        var outputLines = new ArrayList<String>();
        var linesTruncated = false;
        var contextValue = request.context() == null || request.context() <= 0 ? 0 : request.context();

        for (var match : searchResult.matches()) {
            var block = formatBlock(searchPath, isDirectory, match.filePath(), match.lineNumber(), contextValue, fileCache);
            linesTruncated |= block.linesTruncated();
            outputLines.addAll(block.lines());
        }

        var rawOutput = String.join("\n", outputLines);
        var truncation = TextTruncator.truncateHead(rawOutput, new TruncationOptions(Integer.MAX_VALUE, TextTruncator.DEFAULT_MAX_BYTES));
        var output = truncation.content();
        TruncationResult detailsTruncation = null;
        Integer matchLimitReached = null;
        Boolean detailsLinesTruncated = null;
        var notices = new ArrayList<String>();

        if (searchResult.matchLimitReached()) {
            notices.add("%d matches limit reached. Use limit=%d for more, or refine pattern".formatted(effectiveLimit, effectiveLimit * 2));
            matchLimitReached = effectiveLimit;
        }
        if (truncation.truncated()) {
            notices.add("%s limit reached".formatted(TextTruncator.formatSize(TextTruncator.DEFAULT_MAX_BYTES)));
            detailsTruncation = truncation;
        }
        if (linesTruncated) {
            notices.add("Some lines truncated to %d chars. Use read tool to see full lines".formatted(TextTruncator.GREP_MAX_LINE_LENGTH));
            detailsLinesTruncated = true;
        }
        if (!notices.isEmpty()) {
            output += "\n\n[" + String.join(". ", notices) + "]";
        }

        return new AgentToolResult<>(
            List.of(new TextContent(output, null)),
            detailsTruncation == null && matchLimitReached == null && detailsLinesTruncated == null
                ? null
                : new GrepToolDetails(detailsTruncation, matchLimitReached, detailsLinesTruncated)
        );
    }

    private FormatBlockResult formatBlock(
        Path searchPath,
        boolean isDirectory,
        Path filePath,
        int lineNumber,
        int context,
        HashMap<Path, List<String>> fileCache
    ) {
        var relativePath = formatPath(searchPath, isDirectory, filePath);
        var lines = fileCache.computeIfAbsent(filePath, ignored -> readLines(filePath));
        if (lines.isEmpty()) {
            return new FormatBlockResult(List.of(relativePath + ":" + lineNumber + ": (unable to read file)"), false);
        }

        var block = new ArrayList<String>();
        var linesTruncated = false;
        var start = context > 0 ? Math.max(1, lineNumber - context) : lineNumber;
        var end = context > 0 ? Math.min(lines.size(), lineNumber + context) : lineNumber;

        for (int current = start; current <= end; current++) {
            var lineText = lines.get(current - 1);
            var truncated = TextTruncator.truncateLine(lineText);
            linesTruncated |= truncated.wasTruncated();
            if (current == lineNumber) {
                block.add(relativePath + ":" + current + ": " + truncated.text());
            } else {
                block.add(relativePath + "-" + current + "- " + truncated.text());
            }
        }

        return new FormatBlockResult(List.copyOf(block), linesTruncated);
    }

    private List<String> readLines(Path filePath) {
        try {
            var content = operations.readFile(filePath).replace("\r\n", "\n").replace('\r', '\n');
            return List.of(content.split("\n", -1));
        } catch (IOException exception) {
            return List.of();
        }
    }

    private static String formatPath(Path searchPath, boolean isDirectory, Path filePath) {
        if (isDirectory) {
            try {
                var relative = searchPath.relativize(filePath);
                var relativeText = relative.toString().replace('\\', '/');
                if (!relativeText.isEmpty() && !relativeText.startsWith("..")) {
                    return relativeText;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        var fileName = filePath.getFileName();
        return fileName == null ? filePath.toString().replace('\\', '/') : fileName.toString();
    }

    private static GrepRequest parseRequest(JsonNode arguments) {
        var pattern = arguments.path("pattern").asText(null);
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("grep.pattern must be a non-empty string");
        }
        return new GrepRequest(
            pattern,
            arguments.has("path") ? arguments.path("path").asText() : null,
            arguments.has("glob") ? arguments.path("glob").asText() : null,
            arguments.has("ignoreCase") ? arguments.path("ignoreCase").booleanValue() : null,
            arguments.has("literal") ? arguments.path("literal").booleanValue() : null,
            arguments.has("context") ? arguments.path("context").intValue() : null,
            arguments.has("limit") ? arguments.path("limit").intValue() : null
        );
    }

    private static ObjectNode createParametersSchema() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        var properties = schema.putObject("properties");
        properties.putObject("pattern")
            .put("type", "string")
            .put("description", "Search pattern (regex or literal string)");
        properties.putObject("path")
            .put("type", "string")
            .put("description", "Directory or file to search (default: current directory)");
        properties.putObject("glob")
            .put("type", "string")
            .put("description", "Filter files by glob pattern, e.g. '*.ts' or '**/*.spec.ts'");
        properties.putObject("ignoreCase")
            .put("type", "boolean")
            .put("description", "Case-insensitive search (default: false)");
        properties.putObject("literal")
            .put("type", "boolean")
            .put("description", "Treat pattern as literal string instead of regex (default: false)");
        properties.putObject("context")
            .put("type", "number")
            .put("description", "Number of lines to show before and after each match (default: 0)");
        properties.putObject("limit")
            .put("type", "number")
            .put("description", "Maximum number of matches to return (default: 100)");
        schema.putArray("required").add("pattern");
        return schema;
    }

    private record GrepRequest(
        String pattern,
        String path,
        String glob,
        Boolean ignoreCase,
        Boolean literal,
        Integer context,
        Integer limit
    ) {
    }

    private record FormatBlockResult(
        List<String> lines,
        boolean linesTruncated
    ) {
    }
}
