package dev.pi.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionJsonlCodecTest {
    private final SessionJsonlCodec codec = new SessionJsonlCodec();

    @Test
    void parsesTypeScriptSeedFixtureIntoTypedHeaderAndEntries() throws IOException {
        var fixtureUrl = getClass().getResource("/fixtures/ts/large-session.jsonl");
        assertThat(fixtureUrl).isNotNull();

        var fixtureContent = new String(fixtureUrl.openStream().readAllBytes(), StandardCharsets.UTF_8);
        var document = codec.parseDocument(fixtureContent);

        assertThat(document).isPresent();
        assertThat(document.orElseThrow().header().version()).isNull();
        assertThat(document.orElseThrow().header().provider()).isEqualTo("anthropic");
        assertThat(document.orElseThrow().header().modelId()).isEqualTo("claude-sonnet-4-5");
        assertThat(document.orElseThrow().header().thinkingLevel()).isEqualTo("off");
        assertThat(document.orElseThrow().entries()).hasSize(1018);
        assertThat(document.orElseThrow().entries())
            .filteredOn(entry -> entry instanceof SessionEntry.MessageEntry)
            .hasSize(914);
        assertThat(document.orElseThrow().entries())
            .filteredOn(entry -> entry instanceof SessionEntry.ThinkingLevelChangeEntry)
            .hasSize(103);
        assertThat(document.orElseThrow().entries())
            .filteredOn(entry -> entry instanceof SessionEntry.ModelChangeEntry)
            .hasSize(1);

        var firstEntry = document.orElseThrow().entries().getFirst();
        assertThat(firstEntry).isInstanceOf(SessionEntry.MessageEntry.class);
        var firstMessage = ((SessionEntry.MessageEntry) firstEntry).message();
        assertThat(firstMessage.get("role").asText()).isEqualTo("user");

        var abortedAssistant = ((SessionEntry.MessageEntry) document.orElseThrow().entries().get(1)).message();
        assertThat(abortedAssistant.get("role").asText()).isEqualTo("assistant");
        assertThat(abortedAssistant.get("stopReason").asText()).isEqualTo("aborted");
    }

    @Test
    void skipsMalformedAndUnknownLinesWhileKeepingValidEntries() {
        var jsonl = """
            {"type":"session","version":3,"id":"sess-1","timestamp":"2025-01-01T00:00:00Z","cwd":"/tmp"}
            not-json
            {"type":"nope","id":"ignored"}
            {"type":"message","timestamp":"2025-01-01T00:00:01Z","message":{"role":"user","content":[{"type":"text","text":"hello"}],"timestamp":1}}
            """;

        var parsedEntries = codec.parseLines(jsonl);

        assertThat(parsedEntries).hasSize(2);
        assertThat(parsedEntries.getFirst()).isInstanceOf(SessionHeader.class);
        assertThat(parsedEntries.get(1)).isInstanceOf(SessionEntry.MessageEntry.class);
    }

    @Test
    void writesAndReadsModernSessionDocumentRoundTrip(@TempDir java.nio.file.Path tempDir) throws IOException {
        var header = new SessionHeader(3, "sess-1", "2025-01-01T00:00:00Z", "/workspace", null);
        var userMessage = new Message.UserMessage(
            java.util.List.of(new TextContent("hello", null)),
            1L
        );
        var assistantMessage = new Message.AssistantMessage(
            java.util.List.of(new TextContent("hi", null)),
            "openai-responses",
            "openai",
            "gpt-5.1",
            new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
            StopReason.STOP,
            null,
            2L
        );
        var document = new SessionDocument(
            header,
            java.util.List.of(
                new SessionEntry.MessageEntry("u1", null, "2025-01-01T00:00:01Z", codec.valueToTree(userMessage)),
                new SessionEntry.ThinkingLevelChangeEntry("t1", "u1", "2025-01-01T00:00:02Z", "high"),
                new SessionEntry.MessageEntry("a1", "t1", "2025-01-01T00:00:03Z", codec.valueToTree(assistantMessage)),
                new SessionEntry.CustomEntry(
                    "c1",
                    "a1",
                    "2025-01-01T00:00:04Z",
                    "artifact-index",
                    JsonNodeFactory.instance.objectNode().put("count", 3)
                )
            )
        );

        var outputPath = tempDir.resolve("session.jsonl");
        codec.writeDocument(outputPath, document);
        var reparsed = codec.readDocument(outputPath);

        assertThat(reparsed).isPresent();
        var writtenJsonl = Files.readString(outputPath, StandardCharsets.UTF_8);
        assertThat(codec.writeDocument(reparsed.orElseThrow())).isEqualTo(writtenJsonl);
        assertThat(writtenJsonl).endsWith("\n");
    }
}
