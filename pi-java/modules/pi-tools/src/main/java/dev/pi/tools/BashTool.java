package dev.pi.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.pi.agent.runtime.AgentTool;
import dev.pi.agent.runtime.AgentToolResult;
import dev.pi.ai.model.TextContent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class BashTool implements AgentTool<BashToolDetails> {
    private static final ObjectNode PARAMETERS_SCHEMA = createParametersSchema();

    private final Path cwd;
    private final ShellExecutor executor;
    private final ShellConfig shellConfig;
    private final String commandPrefix;
    private final BashSpawnHook spawnHook;

    public BashTool(Path cwd) {
        this(cwd, BashToolOptions.defaults());
    }

    public BashTool(Path cwd, BashToolOptions options) {
        this.cwd = Objects.requireNonNull(cwd, "cwd");
        var appliedOptions = options == null ? BashToolOptions.defaults() : options;
        this.executor = appliedOptions.executor();
        this.shellConfig = appliedOptions.shellConfig();
        this.commandPrefix = appliedOptions.commandPrefix();
        this.spawnHook = appliedOptions.spawnHook();
    }

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public String label() {
        return "bash";
    }

    @Override
    public String description() {
        return "Execute a bash command in the current working directory. Returns stdout and stderr. "
            + "Output is truncated to last %d lines or %dKB (whichever is hit first). "
            + "If truncated, full output is saved to a temp file. Optionally provide a timeout in seconds."
            .formatted(TextTruncator.DEFAULT_MAX_LINES, TextTruncator.DEFAULT_MAX_BYTES / 1024);
    }

    @Override
    public JsonNode parametersSchema() {
        return PARAMETERS_SCHEMA.deepCopy();
    }

    @Override
    public CompletionStage<AgentToolResult<BashToolDetails>> execute(
        String toolCallId,
        JsonNode arguments,
        Consumer<AgentToolResult<BashToolDetails>> onUpdate
    ) {
        return execute(toolCallId, arguments, onUpdate, () -> false);
    }

    @Override
    public CompletionStage<AgentToolResult<BashToolDetails>> execute(
        String toolCallId,
        JsonNode arguments,
        Consumer<AgentToolResult<BashToolDetails>> onUpdate,
        BooleanSupplier cancelled
    ) {
        Objects.requireNonNull(toolCallId, "toolCallId");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(onUpdate, "onUpdate");
        Objects.requireNonNull(cancelled, "cancelled");

        var future = new CompletableFuture<AgentToolResult<BashToolDetails>>();
        Thread.ofVirtual().name("pi-tools-bash").start(() -> {
            try {
                var request = parseRequest(arguments);
                var command = commandPrefix == null || commandPrefix.isBlank()
                    ? request.command()
                    : commandPrefix + "\n" + request.command();
                var spawnContext = resolveSpawnContext(command);
                var rollingOutput = new RollingOutput(TextTruncator.DEFAULT_MAX_BYTES * 2);
                var timeout = request.timeoutSeconds() == null || request.timeoutSeconds() <= 0
                    ? null
                    : Duration.ofMillis(Math.round(request.timeoutSeconds() * 1000));

                var result = executor.execute(
                    spawnContext.command(),
                    shellConfig,
                    new ShellExecutionOptions(
                        spawnContext.cwd(),
                        timeout,
                        spawnContext.environment(),
                        chunk -> {
                            rollingOutput.append(chunk);
                            var truncation = TextTruncator.truncateTail(rollingOutput.content());
                            onUpdate.accept(new AgentToolResult<>(
                                List.of(new TextContent(truncation.content(), null)),
                                new BashToolDetails(truncation.truncated() ? truncation : null, null)
                            ));
                        },
                        cancelled,
                        TextTruncator.DEFAULT_MAX_BYTES
                    )
                );

                if (result.timedOut()) {
                    throw new IOException(composeErrorMessage(
                        result.output(),
                        "Command timed out after %s seconds".formatted(formatTimeout(request.timeoutSeconds()))
                    ));
                }
                if (result.cancelled()) {
                    throw new IOException(composeErrorMessage(result.output(), "Command aborted"));
                }

                var outputText = result.output() == null || result.output().isBlank() ? "(no output)" : result.output();
                BashToolDetails details = null;
                if (result.truncation() != null && result.truncation().truncated()) {
                    details = new BashToolDetails(
                        result.truncation(),
                        result.fullOutputPath() == null ? null : result.fullOutputPath().toString()
                    );
                    outputText += buildTruncationNotice(result.truncation(), result.fullOutputPath());
                }

                if (result.exitCode() != null && result.exitCode() != 0) {
                    throw new IOException(composeErrorMessage(outputText, "Command exited with code " + result.exitCode()));
                }

                future.complete(new AgentToolResult<>(
                    List.of(new TextContent(outputText, null)),
                    details
                ));
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private BashSpawnContext resolveSpawnContext(String command) {
        var baseContext = new BashSpawnContext(
            command,
            cwd,
            new HashMap<>(Shells.defaultEnvironment(null))
        );
        return spawnHook == null ? baseContext : Objects.requireNonNull(spawnHook.apply(baseContext), "spawnHook.apply(...)");
    }

    private static String buildTruncationNotice(TruncationResult truncation, Path fullOutputPath) {
        if (fullOutputPath == null) {
            return "";
        }
        var startLine = truncation.totalLines() - truncation.outputLines() + 1;
        var endLine = truncation.totalLines();
        if (truncation.lastLinePartial()) {
            return "\n\n[Showing last %s of line %d (line is %s). Full output: %s]".formatted(
                TextTruncator.formatSize(truncation.outputBytes()),
                endLine,
                TextTruncator.formatSize(truncation.totalBytes()),
                fullOutputPath
            );
        }
        if (truncation.truncatedBy() == TruncationLimit.LINES) {
            return "\n\n[Showing lines %d-%d of %d. Full output: %s]".formatted(
                startLine,
                endLine,
                truncation.totalLines(),
                fullOutputPath
            );
        }
        return "\n\n[Showing lines %d-%d of %d (%s limit). Full output: %s]".formatted(
            startLine,
            endLine,
            truncation.totalLines(),
            TextTruncator.formatSize(TextTruncator.DEFAULT_MAX_BYTES),
            fullOutputPath
        );
    }

    private static String composeErrorMessage(String output, String suffix) {
        if (output == null || output.isBlank()) {
            return suffix;
        }
        return output + "\n\n" + suffix;
    }

    private static String formatTimeout(Double timeoutSeconds) {
        if (timeoutSeconds == null) {
            return "unknown";
        }
        if (timeoutSeconds == Math.rint(timeoutSeconds)) {
            return Long.toString(timeoutSeconds.longValue());
        }
        return Double.toString(timeoutSeconds);
    }

    private static BashRequest parseRequest(JsonNode arguments) {
        var command = arguments.path("command").asText(null);
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("bash.command must be a non-empty string");
        }
        Double timeout = arguments.has("timeout") ? arguments.path("timeout").doubleValue() : null;
        return new BashRequest(command, timeout);
    }

    private static ObjectNode createParametersSchema() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        var properties = schema.putObject("properties");
        properties.putObject("command")
            .put("type", "string")
            .put("description", "Bash command to execute");
        properties.putObject("timeout")
            .put("type", "number")
            .put("description", "Timeout in seconds (optional, no default timeout)");
        schema.putArray("required").add("command");
        return schema;
    }

    private record BashRequest(
        String command,
        Double timeoutSeconds
    ) {
    }

    private static final class RollingOutput {
        private final ArrayDeque<String> chunks = new ArrayDeque<>();
        private final int maxBytes;
        private int totalBytes;

        private RollingOutput(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        private void append(String chunk) {
            chunks.addLast(chunk);
            totalBytes += chunk.getBytes(StandardCharsets.UTF_8).length;
            while (totalBytes > maxBytes && chunks.size() > 1) {
                var removed = chunks.removeFirst();
                totalBytes -= removed.getBytes(StandardCharsets.UTF_8).length;
            }
        }

        private String content() {
            return String.join("", chunks);
        }
    }
}
