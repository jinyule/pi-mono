package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PiCliAnsiTest {
    @AfterEach
    void resetTheme() {
        PiCliAnsi.resetThemes();
        PiCliAnsi.setTheme("dark");
    }

    @Test
    void usesDarkPaletteByDefault() {
        PiCliAnsi.setTheme("dark");

        assertThat(PiCliAnsi.accent("x")).isEqualTo("\u001b[36mx\u001b[0m");
        assertThat(PiCliAnsi.muted("x")).isEqualTo("\u001b[90mx\u001b[0m");
        assertThat(PiCliAnsi.dim("x")).isEqualTo("\u001b[2;37mx\u001b[0m");
        assertThat(PiCliAnsi.borderMuted("x")).isEqualTo("\u001b[90mx\u001b[0m");
    }

    @Test
    void switchesToLightPalette() {
        PiCliAnsi.setTheme("light");

        assertThat(PiCliAnsi.accent("x")).isEqualTo("\u001b[34mx\u001b[0m");
        assertThat(PiCliAnsi.muted("x")).isEqualTo("\u001b[2;30mx\u001b[0m");
        assertThat(PiCliAnsi.accentBold("x")).isEqualTo("\u001b[1;34mx\u001b[0m");
        assertThat(PiCliAnsi.dim("x")).isEqualTo("\u001b[2;30mx\u001b[0m");
    }

    @Test
    void fallsBackToDarkPaletteForUnknownTheme() {
        PiCliAnsi.setTheme("__missing__");

        assertThat(PiCliAnsi.accent("x")).isEqualTo("\u001b[36mx\u001b[0m");
        assertThat(PiCliAnsi.muted("x")).isEqualTo("\u001b[90mx\u001b[0m");
    }

    @Test
    void switchesToRegisteredCustomPalette() {
        PiCliAnsi.setRegisteredThemes(Map.of(
            "ocean",
            new PiCliAnsi.Palette(
                "38;2;17;34;51",
                "38;5;244",
                "1;38;2;17;34;51",
                "38;2;255;204;0",
                "38;5;46",
                "38;5;196",
                "38;5;239",
                "39",
                "38;5;111",
                "38;5;240"
            )
        ));

        PiCliAnsi.setTheme("ocean");

        assertThat(PiCliAnsi.accent("x")).isEqualTo("\u001b[38;2;17;34;51mx\u001b[0m");
        assertThat(PiCliAnsi.muted("x")).isEqualTo("\u001b[38;5;244mx\u001b[0m");
        assertThat(PiCliAnsi.accentBold("x")).isEqualTo("\u001b[1;38;2;17;34;51mx\u001b[0m");
        assertThat(PiCliAnsi.dim("x")).isEqualTo("\u001b[38;5;239mx\u001b[0m");
        assertThat(PiCliAnsi.border("x")).isEqualTo("\u001b[38;5;111mx\u001b[0m");
        assertThat(PiCliAnsi.borderMuted("x")).isEqualTo("\u001b[38;5;240mx\u001b[0m");
    }
}
