package dev.pi.ai.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.AssistantContent;
import dev.pi.ai.model.AssistantMessageEvent;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.ThinkingContent;
import dev.pi.ai.model.ToolCall;
import dev.pi.ai.model.Usage;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantMessageAssemblerTest {
    @Test
    void replaysTextThinkingAndToolcallBlocksIntoPartialAssistantMessage() {
        var assembler = new AssistantMessageAssembler(model(), 1_741_398_400_000L);

        var start = assembler.start();
        var textStart = assembler.startText(0);
        var textDeltaA = assembler.appendText(0, "Read ");
        var textDeltaB = assembler.appendText(0, "README.md");
        var textEnd = assembler.endText(0, "msg-1");
        var thinkingStart = assembler.startThinking(1);
        var thinkingDelta = assembler.appendThinking(1, "Need the repository summary first.");
        var thinkingEnd = assembler.endThinking(1, "think-1");
        var toolStart = assembler.startToolCall(2, "call-1", "read", "thought-1");
        var toolDeltaA = assembler.appendToolCallArguments(2, "{\"path\":\"README");
        var toolDeltaB = assembler.appendToolCallArguments(2, ".md\",\"offset\":0}");
        var toolEnd = assembler.endToolCall(2);
        var done = assembler.done(StopReason.TOOL_USE, usage(128, 64, 192));

        assertThat(start.partial().content()).isEmpty();
        assertThat(textStart.partial().content()).hasSize(1);
        assertThat(textDeltaA.delta()).isEqualTo("Read ");
        assertThat(((TextContent) textDeltaA.partial().content().get(0)).text()).isEqualTo("Read ");
        assertThat(((TextContent) textDeltaB.partial().content().get(0)).text()).isEqualTo("Read README.md");
        assertThat(((TextContent) textEnd.partial().content().get(0)).textSignature()).isEqualTo("msg-1");
        assertThat(thinkingStart.partial().content()).hasSize(2);
        assertThat(((ThinkingContent) thinkingDelta.partial().content().get(1)).thinking())
            .isEqualTo("Need the repository summary first.");
        assertThat(((ThinkingContent) thinkingEnd.partial().content().get(1)).thinkingSignature()).isEqualTo("think-1");
        assertThat(toolStart.partial().content()).hasSize(3);
        assertThat(((ToolCall) toolDeltaA.partial().content().get(2)).arguments().isEmpty()).isTrue();
        assertThat(((ToolCall) toolDeltaB.partial().content().get(2)).arguments())
            .isEqualTo(JsonNodeFactory.instance.objectNode().put("path", "README.md").put("offset", 0));
        assertThat(toolEnd.toolCall().thoughtSignature()).isEqualTo("thought-1");

        assertThat(done.reason()).isEqualTo(StopReason.TOOL_USE);
        assertThat(done.message().stopReason()).isEqualTo(StopReason.TOOL_USE);
        assertThat(done.message().usage().totalTokens()).isEqualTo(192);
        assertThat(done.message().content()).hasSize(3);
        assertThat(done.message().content()).extracting(AssistantContent::type)
            .containsExactly("text", "thinking", "toolCall");
    }

    @Test
    void emitsErrorTerminalEventWithCurrentPartialMessage() {
        var assembler = new AssistantMessageAssembler(model(), 1_741_398_400_000L);

        assembler.start();
        assembler.startText(0);
        assembler.appendText(0, "Provider failed after partial output.");

        var error = assembler.error(StopReason.ERROR, usage(32, 4, 36), "upstream failure");

        assertThat(error.reason()).isEqualTo(StopReason.ERROR);
        assertThat(error.error().errorMessage()).isEqualTo("upstream failure");
        assertThat(error.error().stopReason()).isEqualTo(StopReason.ERROR);
        assertThat(((TextContent) error.error().content().get(0)).text())
            .isEqualTo("Provider failed after partial output.");
    }

    @Test
    void rejectsDeltasForMismatchedBlockTypes() {
        var assembler = new AssistantMessageAssembler(model(), 1_741_398_400_000L);

        assembler.startText(0);

        assertThatThrownBy(() -> assembler.appendToolCallArguments(0, "{\"path\":\"README.md\"}"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("non-toolCall");
    }

    private static Model model() {
        return new Model(
            "gpt-5.1-codex",
            "GPT-5.1 Codex",
            "openai-responses",
            "openai",
            "https://api.openai.com/v1",
            true,
            List.of("text"),
            new Usage.Cost(0.1, 0.2, 0.0, 0.0, 0.3),
            200_000,
            32_000,
            java.util.Map.of(),
            JsonNodeFactory.instance.objectNode()
        );
    }

    private static Usage usage(int input, int output, int total) {
        return new Usage(input, output, 0, 0, total, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0));
    }
}
