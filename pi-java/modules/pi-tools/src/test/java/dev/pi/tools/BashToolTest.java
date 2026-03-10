package dev.pi.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.TextContent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BashToolTest {
    @TempDir
    Path tempDir;

    @Test
    void executesSimpleCommands() throws Exception {
        var result = new BashTool(tempDir, new BashToolOptions(
            new DefaultShellExecutor(),
            testShellConfig(),
            null,
            null
        )).execute("call-1", args(testCommand("echo test output", "echo test output"), null), ignored -> {
        }).toCompletableFuture().join();

        assertThat(textOutput(result)).contains("test output");
        assertThat(result.details()).isNull();
    }

    @Test
    void appendsExitCodeToFailingCommands() {
        var tool = new BashTool(tempDir, new BashToolOptions(
            new DefaultShellExecutor(),
            testShellConfig(),
            null,
            null
        ));

        assertThatThrownBy(() -> tool.execute("call-2", args(testCommand("exit /b 1", "exit 1"), null), ignored -> {
        }).toCompletableFuture().join())
            .rootCause()
            .hasMessageContaining("Command exited with code 1");
    }

    @Test
    void respectsTimeout() {
        var tool = new BashTool(tempDir, new BashToolOptions(
            new DefaultShellExecutor(),
            testShellConfig(),
            null,
            null
        ));

        assertThatThrownBy(() -> tool.execute("call-3", args(testCommand("ping -n 6 127.0.0.1 >nul", "sleep 5"), 1.0), ignored -> {
        }).toCompletableFuture().join())
            .rootCause()
            .hasMessageContaining("Command timed out after 1 seconds");
    }

    @Test
    void failsWhenWorkingDirectoryDoesNotExist() {
        var missing = tempDir.resolve("missing-dir");
        var tool = new BashTool(missing, new BashToolOptions(
            new DefaultShellExecutor(),
            testShellConfig(),
            null,
            null
        ));

        assertThatThrownBy(() -> tool.execute("call-4", args(testCommand("echo test", "echo test"), null), ignored -> {
        }).toCompletableFuture().join())
            .hasRootCauseMessage("Working directory does not exist: " + missing + "\nCannot execute bash commands.");
    }

    @Test
    void prependsCommandPrefix() throws Exception {
        var capturedCommand = new java.util.concurrent.atomic.AtomicReference<String>();
        var result = new BashTool(tempDir, new BashToolOptions(
            (command, shellConfig, options) -> {
                capturedCommand.set(command);
                return new ShellExecutionResult("prefix-output\ncommand-output\n", 0, false, false, false, null, null);
            },
            testShellConfig(),
            "echo prefix-output",
            null
        )).execute("call-5", args(testCommand("echo command-output", "echo command-output"), null), ignored -> {
        }).toCompletableFuture().join();

        assertThat(capturedCommand.get()).isEqualTo("echo prefix-output\necho command-output");
        assertThat(textOutput(result)).contains("prefix-output");
        assertThat(textOutput(result)).contains("command-output");
    }

    @Test
    void streamsPartialUpdates() throws Exception {
        var updates = new ArrayList<dev.pi.agent.runtime.AgentToolResult<BashToolDetails>>();
        var tool = new BashTool(tempDir, new BashToolOptions(
            (command, shellConfig, options) -> {
                options.onChunk().accept("first\n");
                options.onChunk().accept("second\n");
                return new ShellExecutionResult("first\nsecond\n", 0, false, false, false, null, null);
            },
            testShellConfig(),
            null,
            null
        ));

        var result = tool.execute("call-6", args("ignored", null), updates::add).toCompletableFuture().join();

        assertThat(textOutput(result)).contains("first");
        assertThat(updates).hasSize(2);
        assertThat(textOutput(updates.getLast())).contains("second");
    }

    @Test
    void returnsTruncationDetailsAndFullOutputPath() throws Exception {
        var tempOutput = Files.createTempFile(tempDir, "bash-tool-", ".log");
        Files.writeString(tempOutput, "full output", StandardCharsets.UTF_8);
        var truncation = new TruncationResult(
            "tail",
            true,
            TruncationLimit.BYTES,
            10,
            5000,
            2,
            4,
            false,
            false,
            TextTruncator.DEFAULT_MAX_LINES,
            TextTruncator.DEFAULT_MAX_BYTES
        );
        var tool = new BashTool(tempDir, new BashToolOptions(
            (command, shellConfig, options) -> new ShellExecutionResult(
                "tail",
                0,
                false,
                false,
                true,
                tempOutput,
                truncation
            ),
            testShellConfig(),
            null,
            null
        ));

        var result = tool.execute("call-7", args("ignored", null), ignored -> {
        }).toCompletableFuture().join();

        assertThat(result.details()).isNotNull();
        assertThat(result.details().truncation()).isEqualTo(truncation);
        assertThat(result.details().fullOutputPath()).isEqualTo(tempOutput.toString());
        assertThat(textOutput(result)).contains("Full output: " + tempOutput);
    }

    @Test
    void abortsWhenCancelled() throws Exception {
        var cancelled = new AtomicBoolean(false);
        var executorEntered = new CountDownLatch(1);
        var tool = new BashTool(tempDir, new BashToolOptions(
            (command, shellConfig, options) -> {
                executorEntered.countDown();
                while (!options.cancelled().getAsBoolean()) {
                    Thread.sleep(10);
                }
                return new ShellExecutionResult("partial output", null, true, false, false, null, null);
            },
            testShellConfig(),
            null,
            null
        ));

        var future = tool.execute("call-8", args("ignored", null), ignored -> {
        }, cancelled::get).toCompletableFuture();

        assertThat(executorEntered.await(1, TimeUnit.SECONDS)).isTrue();
        cancelled.set(true);

        assertThatThrownBy(future::join)
            .hasRootCauseMessage("partial output\n\nCommand aborted");
    }

    private static String textOutput(dev.pi.agent.runtime.AgentToolResult<BashToolDetails> result) {
        return result.content().stream()
            .filter(TextContent.class::isInstance)
            .map(TextContent.class::cast)
            .map(TextContent::text)
            .collect(Collectors.joining("\n"));
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode args(String command, Double timeout) {
        var objectNode = JsonNodeFactory.instance.objectNode();
        objectNode.put("command", command);
        if (timeout != null) {
            objectNode.put("timeout", timeout);
        }
        return objectNode;
    }

    private static ShellConfig testShellConfig() {
        if (Shells.isWindows()) {
            var comSpec = System.getenv().getOrDefault("ComSpec", "cmd.exe");
            return new ShellConfig(Path.of(comSpec), List.of("/d", "/s", "/c"));
        }
        return new ShellConfig(Path.of("/bin/sh"), List.of("-lc"));
    }

    private static String testCommand(String windowsCommand, String unixCommand) {
        return Shells.isWindows() ? windowsCommand : unixCommand;
    }
}
