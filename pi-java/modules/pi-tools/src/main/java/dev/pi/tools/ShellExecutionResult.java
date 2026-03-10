package dev.pi.tools;

import java.nio.file.Path;

public record ShellExecutionResult(
    String output,
    Integer exitCode,
    boolean cancelled,
    boolean timedOut,
    boolean truncated,
    Path fullOutputPath,
    TruncationResult truncation
) {
}
