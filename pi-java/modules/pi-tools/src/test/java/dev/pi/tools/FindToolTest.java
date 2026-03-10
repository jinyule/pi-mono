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

class FindToolTest {
    @TempDir
    Path tempDir;

    @Test
    void includesHiddenFilesThatAreNotGitignored() throws Exception {
        Files.createDirectories(tempDir.resolve(".secret"));
        Files.writeString(tempDir.resolve(".secret").resolve("hidden.txt"), "hidden", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("visible.txt"), "visible", StandardCharsets.UTF_8);

        var result = new FindTool(tempDir).execute("call-1", args("**/*.txt", tempDir.toString(), null), ignored -> {
        }).toCompletableFuture().join();

        var outputLines = textOutput(result).lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .toList();
        assertThat(outputLines).contains("visible.txt");
        assertThat(outputLines).contains(".secret/hidden.txt");
    }

    @Test
    void respectsRootGitignore() throws Exception {
        Files.writeString(tempDir.resolve(".gitignore"), "ignored.txt\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("ignored.txt"), "ignored", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("kept.txt"), "kept", StandardCharsets.UTF_8);

        var result = new FindTool(tempDir).execute("call-2", args("**/*.txt", tempDir.toString(), null), ignored -> {
        }).toCompletableFuture().join();

        var output = textOutput(result);
        assertThat(output).contains("kept.txt");
        assertThat(output).doesNotContain("ignored.txt");
    }

    @Test
    void reportsResultLimitReached() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "a", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("b.txt"), "b", StandardCharsets.UTF_8);

        var result = new FindTool(tempDir).execute("call-3", args("**/*.txt", tempDir.toString(), 1), ignored -> {
        }).toCompletableFuture().join();

        assertThat(textOutput(result)).contains("[1 results limit reached. Use limit=2 for more, or refine pattern]");
        assertThat(result.details()).isNotNull();
        assertThat(result.details().resultLimitReached()).isEqualTo(1);
    }

    private static String textOutput(dev.pi.agent.runtime.AgentToolResult<FindToolDetails> result) {
        return result.content().stream()
            .filter(TextContent.class::isInstance)
            .map(TextContent.class::cast)
            .map(TextContent::text)
            .collect(Collectors.joining("\n"));
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode args(String pattern, String path, Integer limit) {
        var objectNode = JsonNodeFactory.instance.objectNode();
        objectNode.put("pattern", pattern);
        if (path != null) {
            objectNode.put("path", path);
        }
        if (limit != null) {
            objectNode.put("limit", limit);
        }
        return objectNode;
    }
}
