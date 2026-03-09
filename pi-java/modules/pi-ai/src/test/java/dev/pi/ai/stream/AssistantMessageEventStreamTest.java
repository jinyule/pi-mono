package dev.pi.ai.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.AssistantMessageEvent;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.ToolCall;
import dev.pi.ai.model.Usage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AssistantMessageEventStreamTest {
    @Test
    void completesWithDoneMessageAndNotifiesSubscribers() throws Exception {
        var partial = partialMessage();
        var finalMessage = finalMessage();
        var stream = new AssistantMessageEventStream();
        var events = new java.util.ArrayList<AssistantMessageEvent>();

        try (var ignored = stream.subscribe(events::add)) {
            stream.push(new AssistantMessageEvent.Start(partial));
            stream.push(new AssistantMessageEvent.TextStart(0, partial));
            stream.push(new AssistantMessageEvent.Done(StopReason.STOP, finalMessage));
        }

        assertThat(stream.result().get(1, TimeUnit.SECONDS)).isEqualTo(finalMessage);
        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isInstanceOf(AssistantMessageEvent.Start.class);
        assertThat(events.get(2)).isInstanceOf(AssistantMessageEvent.Done.class);
    }

    @Test
    void completesWithErrorMessageOnErrorEvent() throws Exception {
        var errorMessage = new Message.AssistantMessage(
            List.of(new TextContent("Provider request failed.", null)),
            "openai-responses",
            "openai",
            "gpt-5.1-codex",
            new Usage(10, 0, 0, 0, 10, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
            StopReason.ERROR,
            "upstream failure",
            1_741_398_400_000L
        );
        var stream = new AssistantMessageEventStream();

        stream.push(new AssistantMessageEvent.Error(StopReason.ERROR, errorMessage));

        assertThat(stream.result()).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(errorMessage);
    }

    private static Message.AssistantMessage partialMessage() {
        return new Message.AssistantMessage(
            List.of(new TextContent("Reading ", null)),
            "openai-responses",
            "openai",
            "gpt-5.1-codex",
            new Usage(10, 5, 0, 0, 15, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
            StopReason.TOOL_USE,
            null,
            1_741_398_400_000L
        );
    }

    private static Message.AssistantMessage finalMessage() {
        return new Message.AssistantMessage(
            List.of(
                new TextContent("Reading README.md", null),
                new ToolCall(
                    "call-1",
                    "read",
                    JsonNodeFactory.instance.objectNode().put("path", "README.md"),
                    "thought-1"
                )
            ),
            "openai-responses",
            "openai",
            "gpt-5.1-codex",
            new Usage(10, 20, 0, 0, 30, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
            StopReason.STOP,
            null,
            1_741_398_400_000L
        );
    }
}
