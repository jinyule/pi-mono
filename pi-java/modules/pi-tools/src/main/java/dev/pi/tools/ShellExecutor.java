package dev.pi.tools;

import java.io.IOException;

public interface ShellExecutor {
    ShellExecutionResult execute(String command, ShellConfig shellConfig, ShellExecutionOptions options)
        throws IOException, InterruptedException;
}
