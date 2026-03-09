package dev.pi.ai.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantMessageJsonTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripsAssistantMessagesWithTypedContent() throws Exception {
        var arguments = JsonNodeFactory.instance.objectNode().put("path", "README.md");

        var message = new Message.AssistantMessage(
            List.of(
                new TextContent("Read the repository root README.", "msg-1"),
                new ThinkingContent("Need the top-level package summary first.", "think-1", false),
                new ToolCall("call-1", "read", arguments, "thought-1")
            ),
            "openai-responses",
            "openai",
            "gpt-5.1-codex",
            new Usage(
                128,
                64,
                0,
                0,
                192,
                new Usage.Cost(0.10, 0.20, 0.0, 0.0, 0.30)
            ),
            StopReason.TOOL_USE,
            null,
            1_741_398_400_000L
        );

        var json = mapper.writeValueAsString(message);

        assertThat(json).contains("\"api\":\"openai-responses\"");
        assertThat(json).contains("\"provider\":\"openai\"");
        assertThat(json).contains("\"stopReason\":\"toolUse\"");
        assertThat(json).contains("\"type\":\"thinking\"");
        assertThat(json).contains("\"type\":\"toolCall\"");

        var restored = mapper.readValue(json, Message.AssistantMessage.class);

        assertThat(restored.api()).isEqualTo("openai-responses");
        assertThat(restored.provider()).isEqualTo("openai");
        assertThat(restored.stopReason()).isEqualTo(StopReason.TOOL_USE);
        assertThat(restored.content()).hasSize(3);
        assertThat(restored.content().get(0)).isInstanceOf(TextContent.class);
        assertThat(restored.content().get(1)).isInstanceOf(ThinkingContent.class);
        assertThat(restored.content().get(2)).isInstanceOf(ToolCall.class);
    }
}
