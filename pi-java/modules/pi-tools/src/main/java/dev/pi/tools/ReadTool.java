package dev.pi.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.pi.agent.runtime.AgentTool;
import dev.pi.agent.runtime.AgentToolResult;
import dev.pi.ai.model.ImageContent;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.UserContent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public final class ReadTool implements AgentTool<ReadToolDetails> {
    private static final ObjectNode PARAMETERS_SCHEMA = createParametersSchema();

    private final Path cwd;
    private final boolean autoResizeImages;
    private final ReadOperations operations;
    private final ImageResizer imageResizer = new ImageResizer();

    public ReadTool(Path cwd) {
        this(cwd, ReadToolOptions.defaults());
    }

    public ReadTool(Path cwd, ReadToolOptions options) {
        this.cwd = Objects.requireNonNull(cwd, "cwd");
        var appliedOptions = options == null ? ReadToolOptions.defaults() : options;
        this.autoResizeImages = appliedOptions.autoResizeImages();
        this.operations = appliedOptions.operations();
    }

    @Override
    public String name() {
        return "read";
    }

    @Override
    public String label() {
        return "read";
    }

    @Override
    public String description() {
        return "Read the contents of a file. Supports text files and images (jpg, png, gif, webp). "
            + "For text files, output is truncated to %d lines or %dKB (whichever is hit first). "
            + "Use offset/limit for large files."
            .formatted(TextTruncator.DEFAULT_MAX_LINES, TextTruncator.DEFAULT_MAX_BYTES / 1024);
    }

    @Override
    public JsonNode parametersSchema() {
        return PARAMETERS_SCHEMA.deepCopy();
    }

    @Override
    public CompletionStage<AgentToolResult<ReadToolDetails>> execute(
        String toolCallId,
        JsonNode arguments,
        Consumer<AgentToolResult<ReadToolDetails>> onUpdate
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

    private AgentToolResult<ReadToolDetails> executeOnce(ReadRequest request) throws IOException {
        var absolutePath = ToolPaths.resolveReadPath(request.path(), cwd);
        try {
            operations.access(absolutePath);
        } catch (IOException exception) {
            throw new IOException("File not found: " + request.path(), exception);
        }

        var mimeType = operations.detectImageMimeType(absolutePath);
        if (mimeType != null) {
            return readImage(absolutePath, mimeType);
        }
        return readText(absolutePath, request);
    }

    private AgentToolResult<ReadToolDetails> readImage(Path absolutePath, String mimeType) throws IOException {
        var bytes = operations.readFile(absolutePath);
        if (autoResizeImages) {
            var resized = imageResizer.resize(bytes, mimeType, null);
            var note = ImageResizer.formatDimensionNote(resized);
            var text = note == null ? "Read image file [%s]".formatted(resized.mimeType()) : "Read image file [%s]\n%s".formatted(resized.mimeType(), note);
            return new AgentToolResult<>(
                List.of(
                    new TextContent(text, null),
                    new ImageContent(Base64.getEncoder().encodeToString(resized.data()), resized.mimeType())
                ),
                null
            );
        }

        return new AgentToolResult<>(
            List.of(
                new TextContent("Read image file [%s]".formatted(mimeType), null),
                new ImageContent(Base64.getEncoder().encodeToString(bytes), mimeType)
            ),
            null
        );
    }

    private AgentToolResult<ReadToolDetails> readText(Path absolutePath, ReadRequest request) throws IOException {
        var textContent = new String(operations.readFile(absolutePath), StandardCharsets.UTF_8);
        var allLines = textContent.split("\n", -1);
        var totalFileLines = allLines.length;

        var startLine = request.offset() == null ? 0 : Math.max(0, request.offset() - 1);
        var startLineDisplay = startLine + 1;
        if (startLine >= allLines.length) {
            throw new IOException("Offset %d is beyond end of file (%d lines total)".formatted(request.offset(), allLines.length));
        }

        String selectedContent;
        Integer userLimitedLines = null;
        if (request.limit() != null) {
            var endLine = Math.min(startLine + request.limit(), allLines.length);
            selectedContent = String.join("\n", java.util.Arrays.copyOfRange(allLines, startLine, endLine));
            userLimitedLines = endLine - startLine;
        } else {
            selectedContent = String.join("\n", java.util.Arrays.copyOfRange(allLines, startLine, allLines.length));
        }

        var truncation = TextTruncator.truncateHead(selectedContent);
        String outputText;
        ReadToolDetails details = null;

        if (truncation.firstLineExceedsLimit()) {
            var firstLineSize = TextTruncator.formatSize(allLines[startLine].getBytes(StandardCharsets.UTF_8).length);
            outputText = "[Line %d is %s, exceeds %s limit. Use bash: sed -n '%dp' %s | head -c %d]"
                .formatted(
                    startLineDisplay,
                    firstLineSize,
                    TextTruncator.formatSize(TextTruncator.DEFAULT_MAX_BYTES),
                    startLineDisplay,
                    request.path(),
                    TextTruncator.DEFAULT_MAX_BYTES
                );
            details = new ReadToolDetails(truncation);
        } else if (truncation.truncated()) {
            var endLineDisplay = startLineDisplay + truncation.outputLines() - 1;
            var nextOffset = endLineDisplay + 1;
            outputText = truncation.content();
            if (truncation.truncatedBy() == TruncationLimit.LINES) {
                outputText += "\n\n[Showing lines %d-%d of %d. Use offset=%d to continue.]"
                    .formatted(startLineDisplay, endLineDisplay, totalFileLines, nextOffset);
            } else {
                outputText += "\n\n[Showing lines %d-%d of %d (%s limit). Use offset=%d to continue.]"
                    .formatted(
                        startLineDisplay,
                        endLineDisplay,
                        totalFileLines,
                        TextTruncator.formatSize(TextTruncator.DEFAULT_MAX_BYTES),
                        nextOffset
                    );
            }
            details = new ReadToolDetails(truncation);
        } else if (userLimitedLines != null && startLine + userLimitedLines < allLines.length) {
            var remaining = allLines.length - (startLine + userLimitedLines);
            var nextOffset = startLine + userLimitedLines + 1;
            outputText = truncation.content() + "\n\n[%d more lines in file. Use offset=%d to continue.]".formatted(remaining, nextOffset);
        } else {
            outputText = truncation.content();
        }

        return new AgentToolResult<>(List.of(new TextContent(outputText, null)), details);
    }

    private static ReadRequest parseRequest(JsonNode arguments) {
        var path = arguments.path("path").asText(null);
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("read.path must be a non-empty string");
        }

        Integer offset = arguments.has("offset") ? arguments.path("offset").intValue() : null;
        Integer limit = arguments.has("limit") ? arguments.path("limit").intValue() : null;
        return new ReadRequest(path, offset, limit);
    }

    private static ObjectNode createParametersSchema() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        var properties = schema.putObject("properties");
        properties.putObject("path")
            .put("type", "string")
            .put("description", "Path to the file to read (relative or absolute)");
        properties.putObject("offset")
            .put("type", "number")
            .put("description", "Line number to start reading from (1-indexed)");
        properties.putObject("limit")
            .put("type", "number")
            .put("description", "Maximum number of lines to read");
        schema.putArray("required").add("path");
        return schema;
    }

    private record ReadRequest(
        String path,
        Integer offset,
        Integer limit
    ) {
    }
}
