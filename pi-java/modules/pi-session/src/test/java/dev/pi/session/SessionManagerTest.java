package dev.pi.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionManagerTest {
    @Test
    void supportsInMemoryAppendNavigationAndTreeReplay() throws IOException {
        var manager = SessionManager.inMemory("/workspace");
        var userId = manager.appendMessage(new Message.UserMessage(List.of(new TextContent("hello", null)), 1L));
        var assistantId = manager.appendMessage(assistantMessage("hi"));

        manager.navigate(userId);
        var branchUserId = manager.appendMessage(new Message.UserMessage(List.of(new TextContent("branch", null)), 2L));
        manager.appendLabelChange(branchUserId, "branch-point");

        var context = manager.buildSessionContext();
        var tree = manager.tree();

        assertThat(manager.sessionId()).isNotBlank();
        assertThat(manager.leafId()).isNotEqualTo(assistantId);
        assertThat(context.messages()).hasSize(2);
        assertThat(((TextContent) ((Message.UserMessage) context.messages().get(1)).content().getFirst()).text()).isEqualTo("branch");
        assertThat(manager.label(branchUserId)).isEqualTo("branch-point");
        assertThat(tree).hasSize(1);
        assertThat(tree.getFirst().children()).hasSize(2);
    }

    @Test
    void delaysPersistentFlushUntilFirstAssistant(@TempDir java.nio.file.Path tempDir) throws IOException {
        var sessionFile = tempDir.resolve("session.jsonl");
        var manager = SessionManager.create(sessionFile, "/workspace");

        manager.appendMessage(new Message.UserMessage(List.of(new TextContent("hello", null)), 1L));
        assertThat(Files.exists(sessionFile)).isFalse();

        manager.appendMessage(assistantMessage("hi"));

        assertThat(Files.exists(sessionFile)).isTrue();
        var lines = Files.readAllLines(sessionFile, StandardCharsets.UTF_8);
        assertThat(lines).hasSize(3);
        assertThat(lines.getFirst()).contains("\"type\":\"session\"");
        assertThat(lines.get(2)).contains("\"role\":\"assistant\"");
    }

    @Test
    void opensLegacySessionAndRewritesMigratedFile(@TempDir java.nio.file.Path tempDir) throws IOException {
        var sessionFile = tempDir.resolve("legacy.jsonl");
        Files.writeString(
            sessionFile,
            """
            {"type":"session","id":"sess-1","timestamp":"2025-01-01T00:00:00Z","cwd":"/tmp"}
            {"type":"message","timestamp":"2025-01-01T00:00:01Z","message":{"role":"user","content":[{"type":"text","text":"hello"}],"timestamp":1}}
            {"type":"message","timestamp":"2025-01-01T00:00:02Z","message":{"role":"assistant","content":[{"type":"text","text":"hi"}],"api":"anthropic-messages","provider":"anthropic","model":"claude-test","usage":{"input":1,"output":1,"cacheRead":0,"cacheWrite":0},"stopReason":"stop","timestamp":2}}
            """,
            StandardCharsets.UTF_8
        );

        var manager = SessionManager.open(sessionFile);
        var fileContent = Files.readString(sessionFile, StandardCharsets.UTF_8);

        assertThat(manager.header().version()).isEqualTo(3);
        assertThat(manager.entries()).allMatch(entry -> entry.id() != null);
        assertThat(fileContent).contains("\"version\":3");
        assertThat(fileContent).contains("\"parentId\"");
    }

    @Test
    void rejectsUnknownNavigationTarget() {
        var manager = SessionManager.inMemory("/workspace");

        assertThatThrownBy(() -> manager.navigate("missing"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown session entry");
    }

    private Message.AssistantMessage assistantMessage(String text) {
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
