package dev.pi.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.TextContent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LsToolTest {
    @TempDir
    Path tempDir;

    @Test
    void listsDotfilesAndDirectories() throws Exception {
        Files.writeString(tempDir.resolve(".hidden-file"), "secret", StandardCharsets.UTF_8);
        Files.createDirectories(tempDir.resolve(".hidden-dir"));

        var result = new LsTool(tempDir).execute("call-1", args(tempDir.toString(), null), ignored -> {
        }).toCompletableFuture().join();

        assertThat(textOutput(result)).contains(".hidden-file");
        assertThat(textOutput(result)).contains(".hidden-dir/");
    }

    @Test
    void rejectsMissingPath() {
        var tool = new LsTool(tempDir);

        assertThatThrownBy(() -> tool.execute("call-2", args(tempDir.resolve("missing").toString(), null), ignored -> {
        }).toCompletableFuture().join())
            .hasRootCauseMessage("Path not found: " + tempDir.resolve("missing"));
    }

    @Test
    void rejectsNonDirectoryPath() throws Exception {
        var file = tempDir.resolve("file.txt");
        Files.writeString(file, "text", StandardCharsets.UTF_8);
        var tool = new LsTool(tempDir);

        assertThatThrownBy(() -> tool.execute("call-3", args(file.toString(), null), ignored -> {
        }).toCompletableFuture().join())
            .hasRootCauseMessage("Not a directory: " + file);
    }

    @Test
    void reportsEntryLimitReached() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "a", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("b.txt"), "b", StandardCharsets.UTF_8);

        var result = new LsTool(tempDir).execute("call-4", args(tempDir.toString(), 1), ignored -> {
        }).toCompletableFuture().join();

        assertThat(textOutput(result)).contains("[1 entries limit reached. Use limit=2 for more]");
        assertThat(result.details()).isNotNull();
        assertThat(result.details().entryLimitReached()).isEqualTo(1);
    }

    @Test
    void returnsEmptyDirectoryMessage() throws Exception {
        var empty = tempDir.resolve("empty");
        Files.createDirectories(empty);

        var result = new LsTool(tempDir).execute("call-5", args(empty.toString(), null), ignored -> {
        }).toCompletableFuture().join();

        assertThat(textOutput(result)).isEqualTo("(empty directory)");
        assertThat(result.details()).isNull();
    }

    private static String textOutput(dev.pi.agent.runtime.AgentToolResult<LsToolDetails> result) {
        return result.content().stream()
            .filter(TextContent.class::isInstance)
            .map(TextContent.class::cast)
            .map(TextContent::text)
            .collect(Collectors.joining("\n"));
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode args(String path, Integer limit) {
        var objectNode = JsonNodeFactory.instance.objectNode();
        if (path != null) {
            objectNode.put("path", path);
        }
        if (limit != null) {
            objectNode.put("limit", limit);
        }
        return objectNode;
    }
}
