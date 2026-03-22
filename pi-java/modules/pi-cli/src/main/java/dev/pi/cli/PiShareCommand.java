package dev.pi.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class PiShareCommand {
    private static final String DEFAULT_SHARE_VIEWER_URL = "https://pi.dev/session/";

    private final PiInteractiveSession session;
    private final GhCli ghCli;

    public PiShareCommand(PiInteractiveSession session) {
        this(session, new SystemGhCli());
    }

    PiShareCommand(PiInteractiveSession session, GhCli ghCli) {
        this.session = Objects.requireNonNull(session, "session");
        this.ghCli = Objects.requireNonNull(ghCli, "ghCli");
    }

    public String shareSession() {
        ghCli.requireAuthenticated();

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("pi-session-", ".html");
            session.exportToHtml(tempFile.toString());

            var gistUrl = ghCli.createSecretGist(tempFile).trim();
            var gistId = parseGistId(gistUrl);
            return "Share URL: " + shareViewerUrl(gistId) + "\nGist: " + gistUrl;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create temporary export file", exception);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static String parseGistId(String gistUrl) {
        if (gistUrl == null || gistUrl.isBlank()) {
            throw new IllegalStateException("Failed to parse gist ID from gh output");
        }
        var trimmed = gistUrl.trim();
        var slashIndex = trimmed.lastIndexOf('/');
        var gistId = slashIndex >= 0 ? trimmed.substring(slashIndex + 1) : trimmed;
        if (gistId.isBlank()) {
            throw new IllegalStateException("Failed to parse gist ID from gh output");
        }
        return gistId;
    }

    private static String shareViewerUrl(String gistId) {
        var configured = System.getenv("PI_SHARE_VIEWER_URL");
        var baseUrl = configured == null || configured.isBlank() ? DEFAULT_SHARE_VIEWER_URL : configured.trim();
        return baseUrl + "#" + gistId;
    }

    interface GhCli {
        void requireAuthenticated();

        String createSecretGist(Path file);
    }

    private static final class SystemGhCli implements GhCli {
        @Override
        public void requireAuthenticated() {
            CommandResult result;
            try {
                result = run(List.of("gh", "auth", "status"));
            } catch (IOException exception) {
                throw new IllegalStateException("GitHub CLI (gh) is not installed. Install it from https://cli.github.com/", exception);
            }
            if (result.exitCode() != 0) {
                throw new IllegalStateException("GitHub CLI is not logged in. Run 'gh auth login' first.");
            }
        }

        @Override
        public String createSecretGist(Path file) {
            CommandResult result;
            try {
                result = run(List.of("gh", "gist", "create", "--public=false", file.toString()));
            } catch (IOException exception) {
                throw new IllegalStateException("GitHub CLI (gh) is not installed. Install it from https://cli.github.com/", exception);
            }
            if (result.exitCode() != 0) {
                var error = result.stderr() == null || result.stderr().isBlank()
                    ? "Unknown error"
                    : result.stderr().trim();
                throw new IllegalStateException("Failed to create gist: " + error);
            }
            return result.stdout();
        }

        private static CommandResult run(List<String> command) throws IOException {
            var processBuilder = new ProcessBuilder(command);
            try {
                var process = processBuilder.start();
                var exitCode = process.waitFor();
                var stdout = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                var stderr = new String(process.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                return new CommandResult(exitCode, stdout, stderr);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("GitHub CLI invocation was interrupted", exception);
            }
        }
    }

    private record CommandResult(
        int exitCode,
        String stdout,
        String stderr
    ) {
    }
}
