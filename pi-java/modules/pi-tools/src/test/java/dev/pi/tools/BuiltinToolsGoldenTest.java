package dev.pi.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.agent.runtime.AgentToolResult;
import dev.pi.ai.model.TextContent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuiltinToolsGoldenTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void readToolMatchesGoldenForOversizedFirstLine() throws Exception {
        var content = "a".repeat(60 * 1024);
        var tool = new ReadTool(Path.of("."), new ReadToolOptions(true, new ReadOperations() {
            @Override
            public byte[] readFile(Path absolutePath) {
                return content.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public void access(Path absolutePath) {
            }

            @Override
            public String detectImageMimeType(Path absolutePath) {
                return null;
            }
        }));

        var result = tool.execute("call-read", args(arguments -> arguments.put("path", "read-big-line.txt")), ignored -> {
        }).toCompletableFuture().join();

        assertGolden(result, golden("read", "bigLine"));
    }

    @Test
    void writeToolMatchesGolden() {
        var tool = new WriteTool(Path.of("."), new WriteToolOptions(new WriteOperations() {
            @Override
            public void writeFile(Path absolutePath, String content) {
            }

            @Override
            public void mkdir(Path dir) {
            }
        }));

        var result = tool.execute("call-write", args(arguments -> {
            arguments.put("path", "nested/out.txt");
            arguments.put("content", "hello");
        }), ignored -> {
        }).toCompletableFuture().join();

        assertGolden(result, golden("write", "basic"));
    }

    @Test
    void editToolMatchesGolden() {
        var original = "alpha\nbeta\ngamma\n";
        var tool = new EditTool(Path.of("."), new EditToolOptions(new EditOperations() {
            @Override
            public String readFile(Path absolutePath) {
                return original;
            }

            @Override
            public void writeFile(Path absolutePath, String content) {
            }

            @Override
            public void access(Path absolutePath) {
            }
        }));

        var result = tool.execute("call-edit", args(arguments -> {
            arguments.put("path", "edit.txt");
            arguments.put("oldText", "beta\n");
            arguments.put("newText", "BETA\n");
        }), ignored -> {
        }).toCompletableFuture().join();

        assertGolden(result, golden("edit", "basic"));
    }

    @Test
    void bashToolMatchesGolden() {
        var tool = new BashTool(Path.of("."), new BashToolOptions(
            (command, shellConfig, options) -> {
                if (options.onChunk() != null) {
                    options.onChunk().accept("stdout line 1\n");
                    options.onChunk().accept("stdout line 2");
                }
                return new ShellExecutionResult(
                    "stdout line 1\nstdout line 2",
                    0,
                    false,
                    false,
                    false,
                    null,
                    null
                );
            },
            null,
            null,
            null
        ));

        var result = tool.execute("call-bash", args(arguments -> arguments.put("command", "echo ignored")), ignored -> {
        }).toCompletableFuture().join();

        assertGolden(result, golden("bash", "basic"));
    }

    @Test
    void grepToolMatchesGolden() {
        var tool = new GrepTool(Path.of("."), new GrepToolOptions(
            new GrepOperations() {
                @Override
                public boolean isDirectory(Path absolutePath) {
                    return false;
                }

                @Override
                public String readFile(Path absolutePath) {
                    return "before\nmatch one\nafter\nmiddle\nmatch two\nafter two";
                }
            },
            (request, cancelled) -> new RipgrepRunner.SearchResult(
                List.of(new RipgrepRunner.Match(Path.of("context.txt"), 2)),
                true
            )
        ));

        var result = tool.execute("call-grep", args(arguments -> {
            arguments.put("pattern", "match");
            arguments.put("path", "context.txt");
            arguments.put("limit", 1);
            arguments.put("context", 1);
        }), ignored -> {
        }).toCompletableFuture().join();

        assertGolden(result, golden("grep", "contextLimit"));
    }

    @Test
    void findToolMatchesGolden() {
        var tool = new FindTool(Path.of("."), new FindToolOptions(new FindOperations() {
            @Override
            public boolean exists(Path absolutePath) {
                return true;
            }

            @Override
            public List<String> glob(String pattern, Path searchPath, SearchOptions options) {
                return List.of("a.txt");
            }
        }));

        var result = tool.execute("call-find", args(arguments -> {
            arguments.put("pattern", "**/*.txt");
            arguments.put("limit", 1);
        }), ignored -> {
        }).toCompletableFuture().join();

        assertGolden(result, golden("find", "limit"));
    }

    @Test
    void lsToolMatchesGolden() {
        var tool = new LsTool(Path.of("."), new LsToolOptions(new LsOperations() {
            @Override
            public boolean exists(Path absolutePath) {
                return true;
            }

            @Override
            public boolean isDirectory(Path absolutePath) {
                var fileName = absolutePath.getFileName();
                if (fileName == null) {
                    return true;
                }
                var name = fileName.toString();
                return !"z.txt".equals(name) && !".hidden".equals(name);
            }

            @Override
            public List<String> readdir(Path absolutePath) {
                return List.of("folder", "z.txt", ".hidden");
            }
        }));

        var result = tool.execute("call-ls", args(arguments -> arguments.put("limit", 2)), ignored -> {
        }).toCompletableFuture().join();

        assertGolden(result, golden("ls", "limit"));
    }

    private static void assertGolden(AgentToolResult<?> result, JsonNode expected) {
        assertThat(textOutput(result)).isEqualTo(expected.path("text").asText());
        if (expected.path("details").isNull()) {
            assertThat(result.details()).isNull();
            return;
        }
        assertThat((JsonNode) OBJECT_MAPPER.valueToTree(result.details())).isEqualTo(expected.path("details"));
    }

    private static String textOutput(AgentToolResult<?> result) {
        return result.content().stream()
            .filter(TextContent.class::isInstance)
            .map(TextContent.class::cast)
            .map(TextContent::text)
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
    }

    private static JsonNode golden(String tool, String scenario) {
        try (var resource = BuiltinToolsGoldenTest.class.getResourceAsStream("/golden/tools/builtin-tools.json")) {
            assertThat(resource).isNotNull();
            return OBJECT_MAPPER.readTree(resource).path(tool).path(scenario);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode args(
        java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode> consumer
    ) {
        var arguments = JsonNodeFactory.instance.objectNode();
        consumer.accept(arguments);
        return arguments;
    }
}
