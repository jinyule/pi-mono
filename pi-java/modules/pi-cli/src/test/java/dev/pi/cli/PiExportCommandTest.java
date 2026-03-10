package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.pi.ai.model.Message;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.session.SessionManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiExportCommandTest {
    @Test
    void exportsSessionToCustomOutputFile(@TempDir Path tempDir) throws Exception {
        var sessionFile = tempDir.resolve("session.jsonl");
        var outputFile = tempDir.resolve("export.html");
        var session = SessionManager.create(sessionFile, tempDir.toString());
        session.appendMessage(userMessage("Explain export", 1L));
        session.appendMessage(assistantMessage("Exported <ok>"));

        var outputPath = new PiExportCommand().export(sessionFile, outputFile);

        assertThat(outputPath).isEqualTo(outputFile.toAbsolutePath().normalize());
        var html = Files.readString(outputFile, StandardCharsets.UTF_8);
        assertThat(html).contains("pi-java session export");
        assertThat(html).contains("You: Explain export");
        assertThat(html).contains("Assistant: Exported &lt;ok&gt;");
    }

    @Test
    void usesDefaultOutputNameNextToCurrentDirectory(@TempDir Path tempDir) throws Exception {
        var sessionFile = tempDir.resolve("named-session.jsonl");
        var session = SessionManager.create(sessionFile, tempDir.toString());
        session.appendMessage(userMessage("Hello", 1L));
        session.appendMessage(assistantMessage("World"));

        var outputPath = new PiExportCommand().export(sessionFile, null);

        assertThat(outputPath.getFileName().toString()).isEqualTo("pi-java-session-named-session.html");
        assertThat(Files.exists(outputPath)).isTrue();
    }

    @Test
    void rejectsMissingSessionFile(@TempDir Path tempDir) {
        assertThatThrownBy(() -> new PiExportCommand().export(tempDir.resolve("missing.jsonl"), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Session file not found");
    }

    private static Message.UserMessage userMessage(String text, long timestamp) {
        return new Message.UserMessage(List.of(new TextContent(text, null)), timestamp);
    }

    private static Message.AssistantMessage assistantMessage(String text) {
        return new Message.AssistantMessage(
            List.of(new TextContent(text, null)),
            "anthropic-messages",
            "anthropic",
            "claude-test",
            new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
            StopReason.STOP,
            null,
            2L
        );
    }
}
