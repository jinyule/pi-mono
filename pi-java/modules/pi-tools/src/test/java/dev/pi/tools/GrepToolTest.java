package dev.pi.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.TextContent;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GrepToolTest {
    @TempDir
    Path tempDir;

    @Test
    void includesFilenameWhenSearchingSingleFile() throws Exception {
        var file = tempDir.resolve("example.txt");
        var tool = new GrepTool(tempDir, new GrepToolOptions(
            operations(List.of(
                file, "first line\nmatch line\nlast line"
            )),
            (request, cancelled) -> new RipgrepRunner.SearchResult(
                List.of(new RipgrepRunner.Match(file, 2)),
                false
            )
        ));

        var result = tool.execute("call-1", args("match", file.toString(), null, null, null, null, null), ignored -> {
        }).toCompletableFuture().join();

        assertThat(textOutput(result)).contains("example.txt:2: match line");
        assertThat(result.details()).isNull();
    }

    @Test
    void respectsGlobalLimitAndIncludesContextLines() throws Exception {
        var file = tempDir.resolve("context.txt");
        var tool = new GrepTool(tempDir, new GrepToolOptions(
            operations(List.of(
                file, "before\nmatch one\nafter\nmiddle\nmatch two\nafter two"
            )),
            (request, cancelled) -> new RipgrepRunner.SearchResult(
                List.of(new RipgrepRunner.Match(file, 2)),
                true
            )
        ));

        var result = tool.execute("call-2", args("match", tempDir.toString(), null, null, null, 1, 1), ignored -> {
        }).toCompletableFuture().join();

        var output = textOutput(result);
        assertThat(output).contains("context.txt-1- before");
        assertThat(output).contains("context.txt:2: match one");
        assertThat(output).contains("context.txt-3- after");
        assertThat(output).contains("[1 matches limit reached. Use limit=2 for more, or refine pattern]");
        assertThat(result.details()).isNotNull();
        assertThat(result.details().matchLimitReached()).isEqualTo(1);
    }

    @Test
    void returnsNoMatchesFound() throws Exception {
        var tool = new GrepTool(tempDir, new GrepToolOptions(
            operations(List.of()),
            (request, cancelled) -> new RipgrepRunner.SearchResult(List.of(), false)
        ));

        var result = tool.execute("call-3", args("missing", tempDir.toString(), null, null, null, null, null), ignored -> {
        }).toCompletableFuture().join();

        assertThat(textOutput(result)).isEqualTo("No matches found");
        assertThat(result.details()).isNull();
    }

    @Test
    void reportsLineTruncationNotice() throws Exception {
        var file = tempDir.resolve("long.txt");
        var longLine = "prefix " + "x".repeat(TextTruncator.GREP_MAX_LINE_LENGTH + 50);
        var tool = new GrepTool(tempDir, new GrepToolOptions(
            operations(List.of(file, longLine)),
            (request, cancelled) -> new RipgrepRunner.SearchResult(
                List.of(new RipgrepRunner.Match(file, 1)),
                false
            )
        ));

        var result = tool.execute("call-4", args("prefix", file.toString(), null, null, null, null, null), ignored -> {
        }).toCompletableFuture().join();

        assertThat(textOutput(result)).contains("Some lines truncated to 500 chars. Use read tool to see full lines");
        assertThat(result.details()).isNotNull();
        assertThat(result.details().linesTruncated()).isTrue();
    }

    private static String textOutput(dev.pi.agent.runtime.AgentToolResult<GrepToolDetails> result) {
        return result.content().stream()
            .filter(TextContent.class::isInstance)
            .map(TextContent.class::cast)
            .map(TextContent::text)
            .collect(Collectors.joining("\n"));
    }

    private static GrepOperations operations(List<Object> entries) {
        var contents = new java.util.HashMap<Path, String>();
        for (int index = 0; index < entries.size(); index += 2) {
            contents.put((Path) entries.get(index), (String) entries.get(index + 1));
        }
        return new GrepOperations() {
            @Override
            public boolean isDirectory(Path absolutePath) {
                return !contents.containsKey(absolutePath);
            }

            @Override
            public String readFile(Path absolutePath) {
                return contents.get(absolutePath);
            }
        };
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode args(
        String pattern,
        String path,
        String glob,
        Boolean ignoreCase,
        Boolean literal,
        Integer context,
        Integer limit
    ) {
        var objectNode = JsonNodeFactory.instance.objectNode();
        objectNode.put("pattern", pattern);
        if (path != null) {
            objectNode.put("path", path);
        }
        if (glob != null) {
            objectNode.put("glob", glob);
        }
        if (ignoreCase != null) {
            objectNode.put("ignoreCase", ignoreCase);
        }
        if (literal != null) {
            objectNode.put("literal", literal);
        }
        if (context != null) {
            objectNode.put("context", context);
        }
        if (limit != null) {
            objectNode.put("limit", limit);
        }
        return objectNode;
    }
}
