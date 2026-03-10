package dev.pi.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.TextContent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WriteToolTest {
    @TempDir
    Path tempDir;

    @Test
    void writesFileContents() throws Exception {
        var file = tempDir.resolve("write-test.txt");
        var content = "Test content";

        var result = new WriteTool(tempDir).execute("call-1", args(file.toString(), content), ignored -> {
        }).toCompletableFuture().join();

        assertThat(textOutput(result)).contains("Successfully wrote 12 bytes");
        assertThat(textOutput(result)).contains(file.toString());
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(content);
        assertThat(result.details()).isNull();
    }

    @Test
    void createsParentDirectories() throws Exception {
        var file = tempDir.resolve("nested").resolve("dir").resolve("test.txt");

        var result = new WriteTool(tempDir).execute("call-2", args(file.toString(), "Nested content"), ignored -> {
        }).toCompletableFuture().join();

        assertThat(textOutput(result)).contains("Successfully wrote");
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("Nested content");
    }

    @Test
    void resolvesRelativePathsAgainstCwd() throws Exception {
        var cwd = tempDir.resolve("workspace");
        Files.createDirectories(cwd);

        new WriteTool(cwd).execute("call-3", args("relative.txt", "hello"), ignored -> {
        }).toCompletableFuture().join();

        assertThat(Files.readString(cwd.resolve("relative.txt"), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    private static dev.pi.agent.runtime.AgentToolResult<Void> invokeResult(WriteTool tool, Path path, String content) {
        return tool.execute("call", args(path.toString(), content), ignored -> {
        }).toCompletableFuture().join();
    }

    private static String textOutput(dev.pi.agent.runtime.AgentToolResult<Void> result) {
        return result.content().stream()
            .filter(TextContent.class::isInstance)
            .map(TextContent.class::cast)
            .map(TextContent::text)
            .collect(Collectors.joining("\n"));
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode args(String path, String content) {
        var objectNode = JsonNodeFactory.instance.objectNode();
        objectNode.put("path", path);
        objectNode.put("content", content);
        return objectNode;
    }
}
