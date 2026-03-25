package dev.pi.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class PiHostCliAuth {
    private final CommandRunner commandRunner;

    PiHostCliAuth() {
        this(new SystemCommandRunner());
    }

    PiHostCliAuth(CommandRunner commandRunner) {
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
    }

    ImportedCredential importFor(String provider) {
        var cli = cliFor(provider);
        if (cli == null) {
            return null;
        }

        CommandResult result;
        try {
            result = commandRunner.run(cli.command());
        } catch (IOException exception) {
            return null;
        }
        if (result.exitCode() != 0) {
            return null;
        }

        var token = firstNonBlankLine(result.stdout());
        if (token == null) {
            return null;
        }
        return new ImportedCredential(token, cli.sourceDisplay());
    }

    private static HostCli cliFor(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        return switch (provider.trim().toLowerCase(Locale.ROOT)) {
            case "github" -> new HostCli("github", List.of("gh", "auth", "token"), "gh auth token");
            case "gitlab" -> new HostCli("gitlab", List.of("glab", "auth", "token"), "glab auth token");
            default -> null;
        };
    }

    private static String firstNonBlankLine(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (var line : value.split("\\R")) {
            var trimmed = line.trim();
            if (!trimmed.isBlank()) {
                return trimmed;
            }
        }
        return null;
    }

    @FunctionalInterface
    interface CommandRunner {
        CommandResult run(List<String> command) throws IOException;
    }

    record ImportedCredential(
        String secret,
        String sourceDisplay
    ) {
        ImportedCredential {
            secret = secret == null ? "" : secret.trim();
            sourceDisplay = sourceDisplay == null ? "" : sourceDisplay.trim();
        }
    }

    record CommandResult(
        int exitCode,
        String stdout,
        String stderr
    ) {
    }

    private record HostCli(
        String providerId,
        List<String> command,
        String sourceDisplay
    ) {
    }

    private static final class SystemCommandRunner implements CommandRunner {
        @Override
        public CommandResult run(List<String> command) throws IOException {
            var processBuilder = new ProcessBuilder(command);
            try {
                var process = processBuilder.start();
                var exitCode = process.waitFor();
                var stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                return new CommandResult(exitCode, stdout, stderr);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Host CLI auth import was interrupted", exception);
            }
        }
    }
}
