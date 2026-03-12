package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiCliThemeLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsThemesFromGlobalProjectAndExplicitPaths() throws Exception {
        var cwd = tempDir.resolve("project");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd.resolve(".pi").resolve("themes"));
        Files.createDirectories(agentDir.resolve("themes"));
        var explicitDir = tempDir.resolve("cli-themes");
        Files.createDirectories(explicitDir);

        Files.writeString(agentDir.resolve("themes").resolve("dawn.json"), themeJson("dawn", "#112233", 244, "#ffcc00", 46, 196));
        Files.writeString(cwd.resolve(".pi").resolve("themes").resolve("ocean.json"), themeJson("ocean", "brand", "mutedTone", "#ffcc00", "ok", 196));
        Files.writeString(explicitDir.resolve("forest.json"), themeJson("forest", "#005500", 242, "#ffaa00", 34, "#aa0000"));

        var result = new PiCliThemeLoader(cwd, agentDir).load(List.of(explicitDir), false);

        assertThat(result.availableThemes()).containsExactly("dawn", "forest", "ocean");
        assertThat(result.warnings()).isEmpty();
        assertThat(result.palettes().get("ocean").accent()).isEqualTo("38;2;17;34;51");
        assertThat(result.palettes().get("ocean").muted()).isEqualTo("38;5;244");
        assertThat(result.palettes().get("ocean").success()).isEqualTo("38;5;46");
    }

    @Test
    void noThemesDisablesGlobalAndProjectDiscoveryButKeepsExplicitPaths() throws Exception {
        var cwd = tempDir.resolve("project");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd.resolve(".pi").resolve("themes"));
        Files.createDirectories(agentDir.resolve("themes"));

        Files.writeString(agentDir.resolve("themes").resolve("dawn.json"), themeJson("dawn", "#112233", 244, "#ffcc00", 46, 196));
        var explicitTheme = tempDir.resolve("sunset.json");
        Files.writeString(explicitTheme, themeJson("sunset", "#661122", 245, "#ffaa00", 40, 160));

        var result = new PiCliThemeLoader(cwd, agentDir).load(List.of(explicitTheme), true);

        assertThat(result.availableThemes()).containsExactly("sunset");
    }

    @Test
    void skipsInvalidThemesAndCollectsWarnings() throws Exception {
        var cwd = tempDir.resolve("project");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd.resolve(".pi").resolve("themes"));
        Files.writeString(cwd.resolve(".pi").resolve("themes").resolve("broken.json"), """
            {
              "name": "broken",
              "colors": {
                "accent": "#112233"
              }
            }
            """);

        var result = new PiCliThemeLoader(cwd, agentDir).load(List.of(), false);

        assertThat(result.availableThemes()).isEmpty();
        assertThat(result.warnings()).singleElement().satisfies(warning -> assertThat(warning).contains("broken.json"));
    }

    private static String themeJson(
        String name,
        Object accent,
        Object muted,
        Object warning,
        Object success,
        Object error
    ) {
        return """
            {
              "name": "%s",
              "vars": {
                "brand": "#112233",
                "mutedTone": 244,
                "ok": 46
              },
              "colors": {
                "accent": %s,
                "muted": %s,
                "warning": %s,
                "success": %s,
                "error": %s
              }
            }
            """.formatted(
            name,
            jsonValue(accent),
            jsonValue(muted),
            jsonValue(warning),
            jsonValue(success),
            jsonValue(error)
        );
    }

    private static String jsonValue(Object value) {
        return switch (value) {
            case Number number -> number.toString();
            case String text -> "\"" + text + "\"";
            default -> throw new IllegalArgumentException("Unsupported value: " + value);
        };
    }
}
