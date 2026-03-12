package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PiCliAnsiTest {
    @AfterEach
    void resetTheme() {
        PiCliAnsi.setTheme("dark");
    }

    @Test
    void usesDarkPaletteByDefault() {
        PiCliAnsi.setTheme("dark");

        assertThat(PiCliAnsi.accent("x")).isEqualTo("\u001b[36mx\u001b[0m");
        assertThat(PiCliAnsi.muted("x")).isEqualTo("\u001b[90mx\u001b[0m");
    }

    @Test
    void switchesToLightPalette() {
        PiCliAnsi.setTheme("light");

        assertThat(PiCliAnsi.accent("x")).isEqualTo("\u001b[34mx\u001b[0m");
        assertThat(PiCliAnsi.muted("x")).isEqualTo("\u001b[2;30mx\u001b[0m");
        assertThat(PiCliAnsi.accentBold("x")).isEqualTo("\u001b[1;34mx\u001b[0m");
    }

    @Test
    void fallsBackToDarkPaletteForUnknownTheme() {
        PiCliAnsi.setTheme("__missing__");

        assertThat(PiCliAnsi.accent("x")).isEqualTo("\u001b[36mx\u001b[0m");
        assertThat(PiCliAnsi.muted("x")).isEqualTo("\u001b[90mx\u001b[0m");
    }
}
