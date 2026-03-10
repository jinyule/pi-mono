package dev.pi.tools;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class DefaultShellExecutor implements ShellExecutor {
    @Override
    public ShellExecutionResult execute(String command, ShellConfig shellConfig, ShellExecutionOptions options)
        throws IOException, InterruptedException {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(shellConfig, "shellConfig");
        var appliedOptions = options == null ? ShellExecutionOptions.defaults() : options;
        var maxBytes = appliedOptions.maxBytes() == null ? TextTruncator.DEFAULT_MAX_BYTES : appliedOptions.maxBytes();
        if (appliedOptions.cwd() != null && !Files.exists(appliedOptions.cwd())) {
            throw new IOException("Working directory does not exist: " + appliedOptions.cwd() + "\nCannot execute bash commands.");
        }

        var processBuilder = new ProcessBuilder(buildCommand(shellConfig, command));
        processBuilder.redirectErrorStream(true);
        if (appliedOptions.cwd() != null) {
            processBuilder.directory(appliedOptions.cwd().toFile());
        }
        processBuilder.environment().clear();
        processBuilder.environment().putAll(Shells.defaultEnvironment(appliedOptions.environment()));

        var process = processBuilder.start();
        var collector = new OutputCollector(maxBytes, appliedOptions.onChunk());
        var readerThread = Thread.ofVirtual().name("pi-tools-shell-reader").start(() -> collector.read(process));

        var cancelled = false;
        var timedOut = false;

        try {
            var timeout = appliedOptions.timeout();
            var deadline = timeout == null ? Long.MAX_VALUE : System.nanoTime() + timeout.toNanos();
            while (true) {
                if (process.waitFor(50, TimeUnit.MILLISECONDS)) {
                    break;
                }
                if (appliedOptions.cancelled().getAsBoolean()) {
                    cancelled = true;
                    Shells.killProcessTree(process.pid());
                    process.waitFor();
                    break;
                }
                if (timeout != null && System.nanoTime() >= deadline) {
                    cancelled = true;
                    timedOut = true;
                    Shells.killProcessTree(process.pid());
                    process.waitFor();
                    break;
                }
            }
        } finally {
            readerThread.join();
            collector.close();
        }

        var rollingOutput = collector.rollingOutput();
        var truncation = TextTruncator.truncateTail(rollingOutput, new TruncationOptions(TextTruncator.DEFAULT_MAX_LINES, maxBytes));
        var output = truncation.truncated() ? truncation.content() : rollingOutput;

        return new ShellExecutionResult(
            output,
            cancelled ? null : process.exitValue(),
            cancelled,
            timedOut,
            truncation.truncated(),
            collector.fullOutputPath(),
            truncation
        );
    }

    private static List<String> buildCommand(ShellConfig shellConfig, String command) {
        var commandLine = new ArrayList<String>();
        commandLine.add(shellConfig.shell().toString());
        commandLine.addAll(shellConfig.args());
        commandLine.add(command);
        return commandLine;
    }

    private static final class OutputCollector {
        private final int maxBytes;
        private final int rollingMaxBytes;
        private final Consumer<String> onChunk;
        private final ArrayDeque<String> rollingChunks = new ArrayDeque<>();
        private int rollingBytes;
        private int totalBytes;
        private Path fullOutputPath;
        private BufferedWriter fullOutputWriter;

        private OutputCollector(int maxBytes, Consumer<String> onChunk) {
            this.maxBytes = maxBytes;
            this.rollingMaxBytes = maxBytes * 2;
            this.onChunk = onChunk;
        }

        private void read(Process process) {
            try (var reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                var buffer = new char[2048];
                int read;
                while ((read = reader.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    accept(new String(buffer, 0, read));
                }
            } catch (IOException ignored) {
            }
        }

        private synchronized void accept(String rawChunk) throws IOException {
            var sanitized = Shells.sanitizeBinaryOutput(rawChunk).replace("\r", "");
            if (sanitized.isEmpty()) {
                return;
            }

            var chunkBytes = sanitized.getBytes(StandardCharsets.UTF_8).length;
            totalBytes += chunkBytes;
            if (totalBytes > maxBytes && fullOutputWriter == null) {
                fullOutputPath = Files.createTempFile("pi-bash-", ".log");
                fullOutputWriter = Files.newBufferedWriter(fullOutputPath, StandardCharsets.UTF_8);
                for (var chunk : rollingChunks) {
                    fullOutputWriter.write(chunk);
                }
            }
            if (fullOutputWriter != null) {
                fullOutputWriter.write(sanitized);
                fullOutputWriter.flush();
            }

            rollingChunks.addLast(sanitized);
            rollingBytes += chunkBytes;
            while (rollingBytes > rollingMaxBytes && rollingChunks.size() > 1) {
                var removed = rollingChunks.removeFirst();
                rollingBytes -= removed.getBytes(StandardCharsets.UTF_8).length;
            }

            if (onChunk != null) {
                onChunk.accept(sanitized);
            }
        }

        private synchronized String rollingOutput() {
            return String.join("", rollingChunks);
        }

        private synchronized Path fullOutputPath() {
            return fullOutputPath;
        }

        private synchronized void close() throws IOException {
            if (fullOutputWriter != null) {
                fullOutputWriter.close();
            }
        }
    }
}
