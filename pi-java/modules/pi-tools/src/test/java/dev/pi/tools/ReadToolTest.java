package dev.pi.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.ImageContent;
import dev.pi.ai.model.TextContent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadToolTest {
    private static final String TINY_PNG_BASE64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";

    @TempDir
    Path tempDir;

    @Test
    void readsTextFileThatFitsWithinLimits() throws Exception {
        var file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello, world!\nLine 2\nLine 3");

        var result = new ReadTool(tempDir).execute("call-1", args(file.toString(), null, null), ignored -> {
        }).toCompletableFuture().join();

        assertThat(textOutput(result)).isEqualTo("Hello, world!\nLine 2\nLine 3");
        assertThat(textOutput(result)).doesNotContain("Use offset=");
        assertThat(result.details()).isNull();
    }

    @Test
    void failsForMissingFile() {
        var tool = new ReadTool(tempDir);

        assertThatThrownBy(() -> tool.execute("call-2", args("missing.txt", null, null), ignored -> {
        }).toCompletableFuture().join())
            .hasMessageContaining("File not found: missing.txt");
    }

    @Test
    void truncatesWhenLineLimitIsExceeded() throws Exception {
        var file = tempDir.resolve("large.txt");
        Files.writeString(file, lines(2500, "Line "));

        var result = new ReadTool(tempDir).execute("call-3", args(file.toString(), null, null), ignored -> {
        }).toCompletableFuture().join();
        var output = textOutput(result);

        assertThat(output).contains("Line 1");
        assertThat(output).contains("Line 2000");
        assertThat(output).doesNotContain("Line 2001");
        assertThat(output).contains("[Showing lines 1-2000 of 2500. Use offset=2001 to continue.]");
        assertThat(result.details()).isNotNull();
        assertThat(result.details().truncation().truncated()).isTrue();
        assertThat(result.details().truncation().truncatedBy()).isEqualTo(TruncationLimit.LINES);
    }

    @Test
    void truncatesWhenByteLimitIsExceeded() throws Exception {
        var file = tempDir.resolve("large-bytes.txt");
        Files.writeString(file, lines(500, "Line ", "x".repeat(200)));

        var result = new ReadTool(tempDir).execute("call-4", args(file.toString(), null, null), ignored -> {
        }).toCompletableFuture().join();

        assertThat(textOutput(result)).containsPattern("\\[Showing lines 1-\\d+ of 500 \\(50\\.0KB limit\\)\\. Use offset=\\d+ to continue\\.\\]");
        assertThat(result.details()).isNotNull();
        assertThat(result.details().truncation().truncatedBy()).isEqualTo(TruncationLimit.BYTES);
    }

    @Test
    void appliesOffsetAndLimitTogether() throws Exception {
        var file = tempDir.resolve("offset-limit.txt");
        Files.writeString(file, lines(100, "Line "));

        var result = new ReadTool(tempDir).execute("call-5", args(file.toString(), 41, 20), ignored -> {
        }).toCompletableFuture().join();
        var output = textOutput(result);

        assertThat(output).doesNotContain("Line 40");
        assertThat(output).contains("Line 41");
        assertThat(output).contains("Line 60");
        assertThat(output).doesNotContain("Line 61");
        assertThat(output).contains("[40 more lines in file. Use offset=61 to continue.]");
        assertThat(result.details()).isNull();
    }

    @Test
    void rejectsOffsetPastEndOfFile() throws Exception {
        var file = tempDir.resolve("short.txt");
        Files.writeString(file, "Line 1\nLine 2\nLine 3");
        var tool = new ReadTool(tempDir);

        assertThatThrownBy(() -> tool.execute("call-6", args(file.toString(), 100, null), ignored -> {
        }).toCompletableFuture().join())
            .hasRootCauseMessage("Offset 100 is beyond end of file (3 lines total)");
    }

    @Test
    void reportsOversizedSingleLineWithBashHint() throws Exception {
        var file = tempDir.resolve("single-line.txt");
        Files.writeString(file, "x".repeat(TextTruncator.DEFAULT_MAX_BYTES + 32));

        var result = new ReadTool(tempDir).execute("call-7", args(file.toString(), null, null), ignored -> {
        }).toCompletableFuture().join();

        assertThat(textOutput(result)).contains("exceeds 50.0KB limit");
        assertThat(textOutput(result)).contains("Use bash: sed -n '1p'");
        assertThat(result.details()).isNotNull();
        assertThat(result.details().truncation().firstLineExceedsLimit()).isTrue();
    }

    @Test
    void detectsImageMimeTypeFromMagicBytes() throws Exception {
        var file = tempDir.resolve("image.txt");
        Files.write(file, Base64.getDecoder().decode(TINY_PNG_BASE64));

        var result = new ReadTool(tempDir).execute("call-8", args(file.toString(), null, null), ignored -> {
        }).toCompletableFuture().join();

        assertThat(result.content().getFirst()).isInstanceOf(TextContent.class);
        assertThat(textOutput(result)).contains("Read image file [image/png]");
        assertThat(result.content().stream().anyMatch(ImageContent.class::isInstance)).isTrue();
    }

    @Test
    void treatsImageExtensionWithTextPayloadAsText() throws Exception {
        var file = tempDir.resolve("not-an-image.png");
        Files.writeString(file, "definitely not a png");

        var result = new ReadTool(tempDir).execute("call-9", args(file.toString(), null, null), ignored -> {
        }).toCompletableFuture().join();

        assertThat(textOutput(result)).contains("definitely not a png");
        assertThat(result.content().stream().anyMatch(ImageContent.class::isInstance)).isFalse();
    }

    @Test
    void canDisableAutoResizeForImages() throws Exception {
        var file = tempDir.resolve("image.png");
        Files.write(file, Base64.getDecoder().decode(TINY_PNG_BASE64));

        var result = new ReadTool(tempDir, new ReadToolOptions(false, null)).execute("call-10", args(file.toString(), null, null), ignored -> {
        }).toCompletableFuture().join();

        assertThat(textOutput(result)).contains("Read image file [image/png]");
        assertThat(textOutput(result)).doesNotContain("Multiply coordinates");
    }

    private static String textOutput(dev.pi.agent.runtime.AgentToolResult<ReadToolDetails> result) {
        return result.content().stream()
            .filter(TextContent.class::isInstance)
            .map(TextContent.class::cast)
            .map(TextContent::text)
            .collect(Collectors.joining("\n"));
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode args(String path, Integer offset, Integer limit) {
        var objectNode = JsonNodeFactory.instance.objectNode();
        objectNode.put("path", path);
        if (offset != null) {
            objectNode.put("offset", offset);
        }
        if (limit != null) {
            objectNode.put("limit", limit);
        }
        return objectNode;
    }

    private static String lines(int count, String prefix) {
        return lines(count, prefix, "");
    }

    private static String lines(int count, String prefix, String suffix) {
        var builder = new StringBuilder();
        for (var index = 0; index < count; index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(prefix).append(index + 1).append(suffix);
        }
        return builder.toString();
    }
}
