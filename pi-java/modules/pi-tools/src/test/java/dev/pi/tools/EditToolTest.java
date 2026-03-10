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

class EditToolTest {
    @TempDir
    Path tempDir;

    @Test
    void replacesTextInFile() throws Exception {
        var file = tempDir.resolve("edit-test.txt");
        Files.writeString(file, "Hello, world!", StandardCharsets.UTF_8);

        var result = new EditTool(tempDir).execute("call-1", args(file.toString(), "world", "testing"), ignored -> {
        }).toCompletableFuture().join();

        assertThat(textOutput(result)).contains("Successfully replaced text in " + file);
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("Hello, testing!");
        assertThat(result.details()).isNotNull();
        assertThat(result.details().diff()).contains("+1 Hello, testing!");
        assertThat(result.details().firstChangedLine()).isEqualTo(1);
    }

    @Test
    void failsWhenTextIsNotFound() throws Exception {
        var file = tempDir.resolve("edit-test.txt");
        Files.writeString(file, "Hello, world!", StandardCharsets.UTF_8);
        var tool = new EditTool(tempDir);

        assertThatThrownBy(() -> tool.execute("call-2", args(file.toString(), "missing", "testing"), ignored -> {
        }).toCompletableFuture().join())
            .hasRootCauseMessage(
                "Could not find the exact text in %s. The old text must match exactly including all whitespace and newlines."
                    .formatted(file)
            );
    }

    @Test
    void failsWhenTextAppearsMultipleTimes() throws Exception {
        var file = tempDir.resolve("edit-test.txt");
        Files.writeString(file, "foo foo foo", StandardCharsets.UTF_8);
        var tool = new EditTool(tempDir);

        assertThatThrownBy(() -> tool.execute("call-3", args(file.toString(), "foo", "bar"), ignored -> {
        }).toCompletableFuture().join())
            .hasRootCauseMessage(
                "Found 3 occurrences of the text in %s. The text must be unique. Please provide more context to make it unique."
                    .formatted(file)
            );
    }

    @Test
    void fuzzyMatchesTrailingWhitespace() throws Exception {
        var file = tempDir.resolve("trailing-ws.txt");
        Files.writeString(file, "line one   \nline two  \nline three\n", StandardCharsets.UTF_8);

        new EditTool(tempDir).execute("call-4", args(file.toString(), "line one\nline two\n", "replaced\n"), ignored -> {
        }).toCompletableFuture().join();

        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("replaced\nline three\n");
    }

    @Test
    void preservesCrLfLineEndings() throws Exception {
        var file = tempDir.resolve("crlf.txt");
        Files.writeString(file, "first\r\nsecond\r\nthird\r\n", StandardCharsets.UTF_8);

        new EditTool(tempDir).execute("call-5", args(file.toString(), "second\n", "REPLACED\n"), ignored -> {
        }).toCompletableFuture().join();

        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("first\r\nREPLACED\r\nthird\r\n");
    }

    @Test
    void preservesUtf8Bom() throws Exception {
        var file = tempDir.resolve("bom.txt");
        Files.writeString(file, "\uFEFFfirst\r\nsecond\r\nthird\r\n", StandardCharsets.UTF_8);

        new EditTool(tempDir).execute("call-6", args(file.toString(), "second\n", "REPLACED\n"), ignored -> {
        }).toCompletableFuture().join();

        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("\uFEFFfirst\r\nREPLACED\r\nthird\r\n");
    }

    private static String textOutput(dev.pi.agent.runtime.AgentToolResult<EditToolDetails> result) {
        return result.content().stream()
            .filter(TextContent.class::isInstance)
            .map(TextContent.class::cast)
            .map(TextContent::text)
            .collect(Collectors.joining("\n"));
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode args(String path, String oldText, String newText) {
        var objectNode = JsonNodeFactory.instance.objectNode();
        objectNode.put("path", path);
        objectNode.put("oldText", oldText);
        objectNode.put("newText", newText);
        return objectNode;
    }
}
