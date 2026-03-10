package dev.pi.cli;

import dev.pi.agent.runtime.AgentMessages;
import dev.pi.session.SessionManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class PiExportCommand {
    public Path export(Path inputPath, Path outputPath) throws IOException {
        Objects.requireNonNull(inputPath, "inputPath");
        var normalizedInputPath = inputPath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedInputPath)) {
            throw new IllegalArgumentException("Session file not found: " + normalizedInputPath);
        }

        var session = SessionManager.open(normalizedInputPath);
        var context = session.buildSessionContext();
        var resolvedOutput = resolveOutputPath(normalizedInputPath, outputPath);
        var parent = resolvedOutput.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(resolvedOutput, renderHtml(session, context), StandardCharsets.UTF_8);
        return resolvedOutput;
    }

    private static Path resolveOutputPath(Path inputPath, Path outputPath) {
        if (outputPath != null) {
            return outputPath.toAbsolutePath().normalize();
        }
        var fileName = inputPath.getFileName().toString();
        if (fileName.endsWith(".jsonl")) {
            fileName = fileName.substring(0, fileName.length() - ".jsonl".length());
        }
        return Path.of("pi-java-session-" + fileName + ".html").toAbsolutePath().normalize();
    }

    private static String renderHtml(SessionManager session, dev.pi.session.SessionContext context) {
        var transcript = context.messages().stream()
            .map(AgentMessages::fromLlmMessage)
            .map(PiMessageRenderer::renderMessage)
            .map(PiExportCommand::escapeHtml)
            .map(text -> "<section class=\"message\"><pre>%s</pre></section>".formatted(text))
            .reduce((left, right) -> left + System.lineSeparator() + right)
            .orElse("<section class=\"message\"><pre>(empty session)</pre></section>");

        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>pi-java session export</title>
              <style>
                body { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; margin: 0; background: #111827; color: #f3f4f6; }
                main { max-width: 960px; margin: 0 auto; padding: 32px 24px 48px; }
                header { margin-bottom: 24px; }
                h1 { margin: 0 0 8px; font-size: 24px; }
                .meta { color: #9ca3af; font-size: 14px; }
                .message { background: #1f2937; border: 1px solid #374151; border-radius: 8px; padding: 12px 16px; margin-bottom: 12px; }
                pre { margin: 0; white-space: pre-wrap; word-break: break-word; }
              </style>
            </head>
            <body>
              <main>
                <header>
                  <h1>pi-java session export</h1>
                  <div class="meta">session: %s</div>
                  <div class="meta">cwd: %s</div>
                </header>
                %s
              </main>
            </body>
            </html>
            """.formatted(
            escapeHtml(session.sessionId()),
            escapeHtml(session.header().cwd()),
            transcript
        );
    }

    private static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
