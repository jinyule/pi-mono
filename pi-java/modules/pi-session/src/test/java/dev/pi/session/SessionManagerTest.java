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
import java.nio.file.Path;
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

    @Test
    void supportsNavigationAndBuildsBranchedTree() throws IOException {
        var manager = SessionManager.inMemory("/workspace");
        var id1 = manager.appendMessage(userMessage("1", 1L));
        var id2 = manager.appendMessage(assistantMessage("2"));
        var id3 = manager.appendMessage(userMessage("3", 3L));

        manager.navigate(id2);
        var id4 = manager.appendMessage(userMessage("4-branch-a", 4L));
        manager.navigate(id2);
        var id5 = manager.appendMessage(userMessage("5-branch-b", 5L));

        assertThat(manager.leafId()).isEqualTo(id5);
        assertThat(manager.branch(id4).stream().map(SessionEntry::id).toList())
            .containsExactly(id1, id2, id4);

        var tree = manager.tree();
        assertThat(tree).hasSize(1);
        assertThat(tree.getFirst().entry().id()).isEqualTo(id1);
        assertThat(tree.getFirst().children()).hasSize(1);

        var node2 = tree.getFirst().children().getFirst();
        assertThat(node2.entry().id()).isEqualTo(id2);
        assertThat(node2.children().stream().map(node -> node.entry().id()).toList())
            .containsExactlyInAnyOrder(id3, id4, id5);
    }

    @Test
    void branchWithSummaryAppendsSummaryAtBranchPoint() throws IOException {
        var manager = SessionManager.inMemory("/workspace");
        var id1 = manager.appendMessage(userMessage("1", 1L));
        manager.appendMessage(assistantMessage("2"));
        manager.appendMessage(userMessage("3", 3L));

        var summaryId = manager.branchWithSummary(id1, "Summary of abandoned work", null, false);

        assertThat(manager.leafId()).isEqualTo(summaryId);
        assertThat(manager.leafEntry()).isInstanceOf(SessionEntry.BranchSummaryEntry.class);

        var summaryEntry = (SessionEntry.BranchSummaryEntry) manager.leafEntry();
        assertThat(summaryEntry.parentId()).isEqualTo(id1);
        assertThat(summaryEntry.fromId()).isEqualTo(id1);
        assertThat(summaryEntry.summary()).isEqualTo("Summary of abandoned work");
    }

    @Test
    void createBranchedSessionPreservesPathAndLabelsInMemory() throws IOException {
        var manager = SessionManager.inMemory("/workspace");
        var id1 = manager.appendMessage(userMessage("1", 1L));
        var id2 = manager.appendMessage(assistantMessage("2"));
        var id3 = manager.appendMessage(userMessage("3", 3L));
        manager.appendLabelChange(id1, "root");
        manager.appendLabelChange(id2, "checkpoint");
        manager.appendLabelChange(id3, "discarded");

        manager.navigate(id2);
        var id4 = manager.appendMessage(userMessage("4-branch", 4L));
        var id5 = manager.appendMessage(assistantMessage("5-branch"));
        manager.appendLabelChange(id4, "kept");

        var branchedFile = manager.createBranchedSession(id5);

        assertThat(branchedFile).isNull();
        assertThat(manager.sessionFile()).isNull();
        assertThat(manager.entries().stream().filter(SessionEntry.MessageEntry.class::isInstance).map(SessionEntry::id).toList())
            .containsExactly(id1, id2, id4, id5);
        assertThat(manager.label(id1)).isEqualTo("root");
        assertThat(manager.label(id2)).isEqualTo("checkpoint");
        assertThat(manager.label(id3)).isNull();
        assertThat(manager.label(id4)).isEqualTo("kept");
        assertThat(manager.entries().stream().filter(SessionEntry.LabelEntry.class::isInstance).toList()).hasSize(3);
        assertThat(manager.buildSessionContext().messages()).hasSize(4);
    }

    @Test
    void defersForkFileCreationUntilAssistantExistsOnBranchedPath(@TempDir Path tempDir) throws IOException {
        var originalFile = tempDir.resolve("session.jsonl");
        var manager = SessionManager.create(originalFile, tempDir.toString());

        var id1 = manager.appendMessage(userMessage("first question", 1L));
        manager.appendMessage(assistantMessage("first answer"));
        manager.appendMessage(userMessage("second question", 3L));
        manager.appendMessage(assistantMessage("second answer"));

        var branchedFile = manager.createBranchedSession(id1);

        assertThat(branchedFile).isNotNull();
        assertThat(manager.sessionFile()).isEqualTo(branchedFile);
        assertThat(Files.exists(branchedFile)).isFalse();

        var preset = JsonNodeFactory.instance.objectNode().put("name", "plan");
        manager.appendCustomEntry("preset-state", preset);
        manager.appendMessage(assistantMessage("new answer"));

        assertThat(Files.exists(branchedFile)).isTrue();
        var lines = Files.readAllLines(branchedFile, StandardCharsets.UTF_8);
        assertThat(lines.stream().filter(line -> line.contains("\"type\":\"session\""))).hasSize(1);

        var entryIds = lines.stream()
            .filter(line -> !line.contains("\"type\":\"session\""))
            .map(line -> line.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"))
            .toList();
        assertThat(entryIds).doesNotHaveDuplicates();
        assertThat(lines.getFirst()).contains("\"parentSession\":\"" + originalFile.toString().replace("\\", "\\\\") + "\"");
    }

    @Test
    void writesForkedFileImmediatelyWhenAssistantAlreadyExistsOnPath(@TempDir Path tempDir) throws IOException {
        var originalFile = tempDir.resolve("session.jsonl");
        var manager = SessionManager.create(originalFile, tempDir.toString());

        manager.appendMessage(userMessage("first question", 1L));
        var id2 = manager.appendMessage(assistantMessage("first answer"));
        manager.appendMessage(userMessage("second question", 3L));
        manager.appendMessage(assistantMessage("second answer"));

        var branchedFile = manager.createBranchedSession(id2);

        assertThat(branchedFile).isNotNull();
        assertThat(branchedFile).isNotEqualTo(originalFile);
        assertThat(Files.exists(branchedFile)).isTrue();
        var lines = Files.readAllLines(branchedFile, StandardCharsets.UTF_8);
        assertThat(lines.stream().filter(line -> line.contains("\"type\":\"session\""))).hasSize(1);
        assertThat(lines).hasSize(3);
    }

    private Message.UserMessage userMessage(String text, long timestamp) {
        return new Message.UserMessage(List.of(new TextContent(text, null)), timestamp);
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
