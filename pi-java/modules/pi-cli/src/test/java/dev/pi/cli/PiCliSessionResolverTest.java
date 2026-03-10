package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.pi.ai.model.Message;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.session.SessionManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiCliSessionResolverTest {
    @Test
    void returnsInMemorySessionWhenDisabled(@TempDir Path tempDir) throws Exception {
        var resolver = new PiCliSessionResolver(tempDir);

        var session = resolver.resolve(new PiCliParser().parse("--no-session"));

        assertThat(session.persistent()).isFalse();
        assertThat(session.sessionFile()).isNull();
    }

    @Test
    void opensExistingExplicitSessionPath(@TempDir Path tempDir) throws Exception {
        var sessionFile = tempDir.resolve("existing.jsonl");
        var existing = SessionManager.create(sessionFile, tempDir.toString());
        existing.appendMessage(userMessage("hello", 1L));
        existing.appendMessage(assistantMessage("hi"));

        var resolver = new PiCliSessionResolver(tempDir);
        var session = resolver.resolve(new PiCliParser().parse("--session", sessionFile.toString()));

        assertThat(session.sessionFile()).isEqualTo(sessionFile);
        assertThat(session.buildSessionContext().messages())
            .extracting(Message::role)
            .containsExactly("user", "assistant");
    }

    @Test
    void createsMissingExplicitSessionPath(@TempDir Path tempDir) throws Exception {
        var sessionFile = tempDir.resolve("missing.jsonl");
        var resolver = new PiCliSessionResolver(tempDir);

        var session = resolver.resolve(new PiCliParser().parse("--session", sessionFile.toString()));

        assertThat(session.sessionFile()).isEqualTo(sessionFile);
        assertThat(session.persistent()).isTrue();
        assertThat(session.entries()).isEmpty();
    }

    @Test
    void continuesMostRecentSessionFromDirectory(@TempDir Path tempDir) throws Exception {
        var sessionsDir = tempDir.resolve("sessions");
        var older = SessionManager.create(sessionsDir.resolve("older.jsonl"), tempDir.toString());
        older.appendMessage(userMessage("older", 1L));
        older.appendMessage(assistantMessage("done"));

        Thread.sleep(10);

        var newer = SessionManager.create(sessionsDir.resolve("newer.jsonl"), tempDir.toString());
        newer.appendMessage(userMessage("newer", 2L));
        newer.appendMessage(assistantMessage("done"));

        var resolver = new PiCliSessionResolver(tempDir);
        var session = resolver.resolve(new PiCliParser().parse("--continue", "--session-dir", sessionsDir.toString()));

        assertThat(session.sessionFile()).isEqualTo(newer.sessionFile());
    }

    @Test
    void createsNewPersistentSessionInSessionDirectory(@TempDir Path tempDir) throws Exception {
        var sessionsDir = tempDir.resolve("sessions");
        var resolver = new PiCliSessionResolver(tempDir);

        var session = resolver.resolve(new PiCliParser().parse("--session-dir", sessionsDir.toString()));

        assertThat(session.persistent()).isTrue();
        assertThat(session.sessionFile().getParent()).isEqualTo(sessionsDir);
    }

    @Test
    void rejectsResumePickerFlagForNow(@TempDir Path tempDir) {
        var resolver = new PiCliSessionResolver(tempDir);

        assertThatThrownBy(() -> resolver.resolve(new PiCliParser().parse("--resume")))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("--resume");
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
