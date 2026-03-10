package dev.pi.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class Shells {
    private Shells() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static ShellConfig resolveShellConfig() {
        return resolveShellConfig(null);
    }

    public static ShellConfig resolveShellConfig(String customShellPath) {
        if (customShellPath != null && !customShellPath.isBlank()) {
            var customPath = Path.of(customShellPath);
            if (!Files.exists(customPath)) {
                throw new IllegalArgumentException("Custom shell path not found: " + customShellPath);
            }
            return new ShellConfig(customPath, List.of("-c"));
        }

        if (isWindows()) {
            var candidates = new ArrayList<Path>();
            var programFiles = System.getenv("ProgramFiles");
            if (programFiles != null) {
                candidates.add(Path.of(programFiles, "Git", "bin", "bash.exe"));
            }
            var programFilesX86 = System.getenv("ProgramFiles(x86)");
            if (programFilesX86 != null) {
                candidates.add(Path.of(programFilesX86, "Git", "bin", "bash.exe"));
            }

            for (var candidate : candidates) {
                if (Files.exists(candidate)) {
                    return new ShellConfig(candidate, List.of("-c"));
                }
            }

            var bashOnPath = findExecutableOnPath("bash.exe");
            if (bashOnPath != null) {
                return new ShellConfig(bashOnPath, List.of("-c"));
            }

            return new ShellConfig(Path.of(System.getenv().getOrDefault("ComSpec", "cmd.exe")), List.of("/d", "/s", "/c"));
        }

        if (Files.exists(Path.of("/bin/bash"))) {
            return new ShellConfig(Path.of("/bin/bash"), List.of("-c"));
        }

        var bashOnPath = findExecutableOnPath("bash");
        if (bashOnPath != null) {
            return new ShellConfig(bashOnPath, List.of("-c"));
        }

        return new ShellConfig(Path.of("sh"), List.of("-c"));
    }

    public static Map<String, String> defaultEnvironment(Map<String, String> overrides) {
        var environment = new HashMap<>(System.getenv());
        if (overrides != null) {
            environment.putAll(overrides);
        }
        return environment;
    }

    public static String sanitizeBinaryOutput(String value) {
        Objects.requireNonNull(value, "value");
        var builder = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (codePoint == 0x09 || codePoint == 0x0A || codePoint == 0x0D) {
                builder.appendCodePoint(codePoint);
                return;
            }
            if (codePoint <= 0x1F) {
                return;
            }
            if (codePoint >= 0xFFF9 && codePoint <= 0xFFFB) {
                return;
            }
            builder.appendCodePoint(codePoint);
        });
        return builder.toString();
    }

    public static void killProcessTree(long pid) {
        if (isWindows()) {
            try {
                new ProcessBuilder("taskkill", "/F", "/T", "/PID", Long.toString(pid))
                    .redirectErrorStream(true)
                    .start();
            } catch (IOException ignored) {
            }
            return;
        }

        ProcessHandle.of(pid).ifPresent(processHandle -> {
            processHandle.descendants().forEach(ProcessHandle::destroyForcibly);
            processHandle.destroyForcibly();
        });
    }

    private static Path findExecutableOnPath(String executable) {
        var path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        var delimiter = isWindows() ? ";" : ":";
        for (var entry : path.split(java.util.regex.Pattern.quote(delimiter))) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            var candidate = Path.of(entry, executable);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
