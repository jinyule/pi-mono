package dev.pi.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.AssistantContent;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.ToolCall;
import dev.pi.ai.model.Usage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageHistoryCompatTest {
    @Test
    void transformMessagesRemapsExplicitToolResults() {
        var model = model();
        var transformed = MessageHistoryCompat.transformMessages(
            List.of(
                assistantWithToolCall("bad id!"),
                new Message.ToolResultMessage(
                    "bad id!",
                    "read",
                    List.of(new TextContent("README contents", null)),
                    JsonNodeFactory.instance.nullNode(),
                    false,
                    20L
                )
            ),
            model,
            99L,
            MessageHistoryCompatTest::normalizeToolCallIds
        );

        assertThat(transformed).hasSize(2);
        assertThat(((ToolCall) ((Message.AssistantMessage) transformed.get(0)).content().get(0)).id()).isEqualTo("bad_id_");
        assertThat(((Message.ToolResultMessage) transformed.get(1)).toolCallId()).isEqualTo("bad_id_");
    }

    @Test
    void transformMessagesSynthesizesMissingToolResultsAfterIdRemap() {
        var model = model();
        var transformed = MessageHistoryCompat.transformMessages(
            List.of(
                assistantWithToolCall("bad id!"),
                new Message.UserMessage(List.of(new TextContent("continue", null)), 20L)
            ),
            model,
            99L,
            MessageHistoryCompatTest::normalizeToolCallIds
        );

        assertThat(transformed).hasSize(3);
        assertThat(((ToolCall) ((Message.AssistantMessage) transformed.get(0)).content().get(0)).id()).isEqualTo("bad_id_");
        assertThat(((Message.ToolResultMessage) transformed.get(1)).toolCallId()).isEqualTo("bad_id_");
        assertThat(((Message.ToolResultMessage) transformed.get(1)).isError()).isTrue();
        assertThat(transformed.get(2)).isInstanceOf(Message.UserMessage.class);
    }

    @Test
    void transformMessagesSkipsErroredAssistantTurns() {
        var model = model();
        var transformed = MessageHistoryCompat.transformMessages(
            List.of(
                new Message.AssistantMessage(
                    List.of(new TextContent("failed", null)),
                    model.api(),
                    model.provider(),
                    model.id(),
                    zeroUsage(),
                    StopReason.ERROR,
                    "boom",
                    10L
                ),
                new Message.UserMessage(List.of(new TextContent("retry", null)), 20L)
            ),
            model,
            99L,
            MessageHistoryCompatTest::normalizeToolCallIds
        );

        assertThat(transformed).hasSize(1);
        assertThat(transformed.get(0)).isInstanceOf(Message.UserMessage.class);
    }

    private static Message.AssistantMessage normalizeToolCallIds(
        Message.AssistantMessage assistantMessage,
        Model model,
        MessageHistoryCompat.CompatContext compatContext
    ) {
        var transformedContent = new ArrayList<AssistantContent>(assistantMessage.content().size());
        for (AssistantContent block : assistantMessage.content()) {
            if (block instanceof ToolCall toolCall) {
                var normalizedId = compatContext.normalizeToolCallId(toolCall.id());
                compatContext.remapToolCallId(toolCall.id(), normalizedId);
                transformedContent.add(new ToolCall(normalizedId, toolCall.name(), toolCall.arguments(), toolCall.thoughtSignature()));
            } else {
                transformedContent.add(block);
            }
        }
        return MessageHistoryCompat.rebuildAssistantMessage(assistantMessage, transformedContent);
    }

    private static Message.AssistantMessage assistantWithToolCall(String id) {
        var model = model();
        return new Message.AssistantMessage(
            List.of(new ToolCall(id, "read", JsonNodeFactory.instance.objectNode().put("path", "README.md"), null)),
            model.api(),
            model.provider(),
            model.id(),
            zeroUsage(),
            StopReason.TOOL_USE,
            null,
            10L
        );
    }

    private static Model model() {
        return new Model(
            "claude",
            "claude",
            "anthropic-messages",
            "anthropic",
            "https://api.anthropic.com/v1",
            true,
            List.of("text"),
            zeroUsage().cost(),
            200_000,
            16_000,
            java.util.Map.of(),
            JsonNodeFactory.instance.objectNode()
        );
    }

    private static Usage zeroUsage() {
        return new Usage(0, 0, 0, 0, 0, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0));
    }
}
