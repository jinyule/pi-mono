package dev.pi.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultShellExecutorTest {
    private final DefaultShellExecutor executor = new DefaultShellExecutor();

    @Test
    void executeCollectsOutputAndStreamsChunks() throws Exception {
        var chunks = new ArrayList<String>();
        var result = executor.execute(testCommand("echo first && echo second", "printf 'first\\nsecond\\n'"), testShellConfig(), new ShellExecutionOptions(
            null,
            Duration.ofSeconds(5),
            null,
            chunks::add,
            null,
            1024
        ));

        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.cancelled()).isFalse();
        assertThat(result.timedOut()).isFalse();
        assertThat(result.truncated()).isFalse();
        assertThat(result.output()).contains("first");
        assertThat(result.output()).contains("second");
        assertThat(chunks).isNotEmpty();
    }

    @Test
    void executeSpillsLargeOutputToTempFileAndTruncatesTail() throws Exception {
        var command = testCommand(
            "for /L %i in (1,1,200) do @echo line-%i-abcdefghijklmnopqrstuvwxyz",
            "i=1; while [ $i -le 200 ]; do echo line-$i-abcdefghijklmnopqrstuvwxyz; i=$((i+1)); done"
        );
        var result = executor.execute(command, testShellConfig(), new ShellExecutionOptions(
            null,
            Duration.ofSeconds(10),
            null,
            null,
            null,
            256
        ));

        assertThat(result.truncated()).isTrue();
        assertThat(result.fullOutputPath()).isNotNull();
        assertThat(Files.exists(result.fullOutputPath())).isTrue();
        assertThat(Files.readString(result.fullOutputPath(), StandardCharsets.UTF_8)).contains("line-1-abcdefghijklmnopqrstuvwxyz");
        assertThat(result.output()).contains("line-200-abcdefghijklmnopqrstuvwxyz");
    }

    @Test
    void executeMarksTimeoutAndKillsProcess() throws Exception {
        var command = testCommand("ping -n 6 127.0.0.1 >nul", "sleep 5");

        var result = executor.execute(command, testShellConfig(), new ShellExecutionOptions(
            null,
            Duration.ofMillis(200),
            null,
            null,
            null,
            1024
        ));

        assertThat(result.cancelled()).isTrue();
        assertThat(result.timedOut()).isTrue();
        assertThat(result.exitCode()).isNull();
    }

    @Test
    void sanitizeBinaryOutputRemovesUnsafeControlCharacters() {
        var result = Shells.sanitizeBinaryOutput("ok\u0000\u0008\t\n\r\uFFFAdone");

        assertThat(result).isEqualTo("ok\t\n\rdone");
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
