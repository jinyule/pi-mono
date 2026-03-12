package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiCliThemeLoaderTokenTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void loadsOptionalCoreUiTokensWhenPresent() throws Exception {
        var themeFile = tempDir.resolve("full-theme.json");
        Files.writeString(themeFile, """
            {
              "name": "full-theme",
              "colors": {
                "accent": "#112233",
                "muted": 244,
                "warning": "#ffcc00",
                "success": 46,
                "error": 196,
                "dim": 239,
                "text": "",
                "border": "#334455",
                "borderMuted": 240
              }
            }
            """);

        var result = new PiCliThemeLoader(tempDir, tempDir).load(java.util.List.of(themeFile), true);
        var palette = result.palettes().get("full-theme");

        assertThat(palette.dim()).isEqualTo("38;5;239");
        assertThat(palette.text()).isEqualTo("39");
        assertThat(palette.border()).isEqualTo("38;2;51;68;85");
        assertThat(palette.borderMuted()).isEqualTo("38;5;240");
    }

    @Test
    void fallsBackOptionalCoreUiTokensWhenMissing() throws Exception {
        var themeFile = tempDir.resolve("fallback-theme.json");
        Files.writeString(themeFile, """
            {
              "name": "fallback-theme",
              "colors": {
                "accent": "#112233",
                "muted": 244,
                "warning": "#ffcc00",
                "success": 46,
                "error": 196
              }
            }
            """);

        var result = new PiCliThemeLoader(tempDir, tempDir).load(java.util.List.of(themeFile), true);
        var palette = result.palettes().get("fallback-theme");

        assertThat(palette.dim()).isEqualTo("38;5;244");
        assertThat(palette.text()).isEqualTo("39");
        assertThat(palette.border()).isEqualTo("38;2;17;34;51");
        assertThat(palette.borderMuted()).isEqualTo("38;5;244");
    }
}
