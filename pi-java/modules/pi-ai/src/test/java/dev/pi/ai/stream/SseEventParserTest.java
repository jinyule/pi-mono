package dev.pi.ai.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SseEventParserTest {
    @Test
    void parsesChunkedEventsWithCommentsIdsRetryAndMultilineData() {
        var parser = new SseEventParser();

        var firstChunk = parser.append("\uFEFFid: evt-1\r\nevent: response.delta\r\ndata: {\"delta\":\"Hel");
        var secondChunk = parser.append("lo\"}\r\ndata: {\"delta\":\"!\"}\r\n\r\n:keepalive\r\nretry: 1500\r\ndata: {\"done\":true}\r\n\r\n");

        assertThat(firstChunk).isEmpty();
        assertThat(secondChunk).containsExactly(
            new SseEvent("response.delta", "{\"delta\":\"Hello\"}\n{\"delta\":\"!\"}", "evt-1", null),
            new SseEvent("message", "{\"done\":true}", "evt-1", 1500L)
        );
    }

    @Test
    void flushesPendingEventAtEndOfInput() {
        var parser = new SseEventParser();

        var events = parser.append("event: done\ndata: {\"ok\":true}");
        var flushed = parser.finish();

        assertThat(events).isEmpty();
        assertThat(flushed).containsExactly(new SseEvent("done", "{\"ok\":true}", null, null));
    }

    @Test
    void ignoresInvalidRetryAndStripsSingleLeadingSpace() {
        var parser = new SseEventParser();

        var events = parser.append("retry: nope\nid: abc\ndata:  hello\n\n");

        assertThat(events).containsExactly(new SseEvent("message", " hello", "abc", null));
    }
}
