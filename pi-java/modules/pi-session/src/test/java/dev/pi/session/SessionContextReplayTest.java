package dev.pi.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SessionContextReplayTest {
    private final SessionJsonlCodec codec = new SessionJsonlCodec();

    @Test
    void returnsEmptyContextBeforeFirstEntry() {
        var context = SessionContexts.buildSessionContext(List.of(), null);

        assertThat(context.messages()).isEmpty();
        assertThat(context.thinkingLevel()).isEqualTo("off");
        assertThat(context.model()).isNull();
    }

    @Test
    void followsPathToSpecifiedLeaf() {
        List<SessionEntry> entries = List.of(
            user("1", null, "start"),
            assistant("2", "1", "response"),
            user("3", "2", "branch A"),
            user("4", "2", "branch B")
        );

        var contextA = SessionContexts.buildSessionContext(entries, "3");
        var contextB = SessionContexts.buildSessionContext(entries, "4");

        assertThat(text(contextA.messages().get(2))).isEqualTo("branch A");
        assertThat(text(contextB.messages().get(2))).isEqualTo("branch B");
    }

    @Test
    void emitsCompactionSummaryBeforeKeptMessages() {
        List<SessionEntry> entries = List.of(
            user("1", null, "first"),
            assistant("2", "1", "response1"),
            user("3", "2", "second"),
            assistant("4", "3", "response2"),
            new SessionEntry.CompactionEntry("5", "4", "2025-01-01T00:00:00Z", "Summary of first two turns", "3", 1000, null, null),
            user("6", "5", "third"),
            assistant("7", "6", "response3")
        );

        var context = SessionContexts.buildSessionContext(entries);

        assertThat(context.messages()).hasSize(5);
        assertThat(text(context.messages().get(0))).contains("Summary of first two turns");
        assertThat(text(context.messages().get(1))).isEqualTo("second");
        assertThat(text(context.messages().get(2))).isEqualTo("response2");
        assertThat(text(context.messages().get(3))).isEqualTo("third");
        assertThat(text(context.messages().get(4))).isEqualTo("response3");
    }

    @Test
    void includesBranchSummaryAndCustomMessageInReplay() {
        List<SessionEntry> entries = List.of(
            user("1", null, "start"),
            assistant("2", "1", "response"),
            new SessionEntry.BranchSummaryEntry("3", "2", "2025-01-01T00:00:00Z", "2", "Tried wrong path", null, null),
            new SessionEntry.CustomMessageEntry(
                "4",
                "3",
                "2025-01-01T00:00:01Z",
                "artifact",
                JsonNodeFactory.instance.textNode("artifact note"),
                null,
                true
            )
        );

        var context = SessionContexts.buildSessionContext(entries);

        assertThat(context.messages()).hasSize(4);
        assertThat(text(context.messages().get(2))).contains("Tried wrong path");
        assertThat(text(context.messages().get(3))).isEqualTo("artifact note");
    }

    @Test
    void tracksThinkingLevelAndModelAcrossPath() {
        var document = new SessionDocument(
            new SessionHeader("session", 3, "sess-1", "2025-01-01T00:00:00Z", "/tmp", null, "anthropic", "claude-sonnet-4-5", "off"),
            List.of(
                user("1", null, "hello"),
                new SessionEntry.ThinkingLevelChangeEntry("2", "1", "2025-01-01T00:00:01Z", "high"),
                new SessionEntry.ModelChangeEntry("3", "2", "2025-01-01T00:00:02Z", "openai", "gpt-4"),
                assistant("4", "3", "hi")
            )
        );

        var context = SessionContexts.buildSessionContext(document);

        assertThat(context.thinkingLevel()).isEqualTo("high");
        assertThat(context.model()).isEqualTo(new SessionContext.ModelSelection("anthropic", "claude-test"));
    }

    @Test
    void convertsBashExecutionMessagesToUserContextText() {
        var bashMessage = codec.valueToTree(Map.of(
            "role", "bashExecution",
            "command", "ls -la",
            "output", "file.txt",
            "exitCode", 0,
            "cancelled", false,
            "truncated", false,
            "timestamp", 123L
        ));

        var context = SessionContexts.buildSessionContext(List.of(
            new SessionEntry.MessageEntry("1", null, "2025-01-01T00:00:00Z", bashMessage)
        ));

        assertThat(text(context.messages().getFirst())).contains("Ran `ls -la`");
        assertThat(text(context.messages().getFirst())).contains("file.txt");
    }

    private SessionEntry.MessageEntry user(String id, String parentId, String text) {
        return new SessionEntry.MessageEntry(
            id,
            parentId,
            "2025-01-01T00:00:00Z",
            codec.valueToTree(new Message.UserMessage(List.of(new TextContent(text, null)), 1L))
        );
    }

    private SessionEntry.MessageEntry assistant(String id, String parentId, String text) {
        return new SessionEntry.MessageEntry(
            id,
            parentId,
            "2025-01-01T00:00:00Z",
            codec.valueToTree(new Message.AssistantMessage(
                List.of(new TextContent(text, null)),
                "anthropic-messages",
                "anthropic",
                "claude-test",
                new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                StopReason.STOP,
                null,
                2L
            ))
        );
    }

    private String text(Message message) {
        return switch (message) {
            case Message.UserMessage userMessage -> ((TextContent) userMessage.content().getFirst()).text();
            case Message.AssistantMessage assistantMessage -> ((TextContent) assistantMessage.content().getFirst()).text();
            case Message.ToolResultMessage toolResultMessage -> ((TextContent) toolResultMessage.content().getFirst()).text();
        };
    }
}
