package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TerminalInputBufferTest {
    @Test
    void splitsPlainTextAndEscapeSequences() {
        var buffer = new TerminalInputBuffer();

        var events = buffer.feed("a\u001b[A好");

        assertThat(events).containsExactly("a", "\u001b[A", "好");
    }

    @Test
    void keepsPartialCsiSequenceBufferedUntilComplete() {
        var buffer = new TerminalInputBuffer();

        var first = buffer.feed("\u001b[");
        var second = buffer.feed("1;2A");

        assertThat(first).isEmpty();
        assertThat(second).containsExactly("\u001b[1;2A");
    }

    @Test
    void rewritesBracketedPasteIntoSingleEventAcrossChunks() {
        var buffer = new TerminalInputBuffer();

        var first = buffer.feed("\u001b[200~hello");
        var second = buffer.feed(" world\u001b[201~x");

        assertThat(first).isEmpty();
        assertThat(second).containsExactly("\u001b[200~hello world\u001b[201~", "x");
    }

    @Test
    void flushesIncompleteEscapeSequence() {
        var buffer = new TerminalInputBuffer();
        buffer.feed("\u001b[12");

        var flushed = buffer.flush();

        assertThat(flushed).containsExactly("\u001b[12");
    }
}
