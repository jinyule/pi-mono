package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TerminalTextTest {
    @Test
    void truncatesMiddleToWidth() {
        var result = TerminalText.truncateMiddleToWidth("/workspace/projects/pi-mono • Scratch", 24);

        assertThat(TerminalText.visibleWidth(result)).isLessThanOrEqualTo(24);
        assertThat(result)
            .startsWith("/workspace/")
            .contains("...")
            .endsWith("Scratch");
    }

    @Test
    void truncatesMiddleToWidthWithAnsiText() {
        var result = TerminalText.truncateMiddleToWidth("\u001b[90m/workspace/projects/pi-mono • Scratch\u001b[0m", 24);

        assertThat(TerminalText.visibleWidth(result)).isLessThanOrEqualTo(24);
        assertThat(result)
            .contains("...")
            .contains("\u001b[90m")
            .contains("\u001b[0m");
    }
}
