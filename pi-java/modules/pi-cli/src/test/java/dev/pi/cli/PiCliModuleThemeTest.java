package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.ai.PiAiClient;
import dev.pi.ai.auth.CredentialResolver;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.Usage;
import dev.pi.ai.registry.ApiProviderRegistry;
import dev.pi.ai.registry.ModelRegistry;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiCliModuleThemeTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void resetThemes() {
        PiCliAnsi.resetThemes();
    }

    @Test
    void createDefaultSessionLoadsDiscoveredProjectThemes() throws Exception {
        var cwd = tempDir.resolve("workspace");
        Files.createDirectories(cwd.resolve(".pi").resolve("themes"));
        Files.writeString(cwd.resolve(".pi").resolve("themes").resolve("ocean.json"), """
            {
              "name": "ocean",
              "vars": {
                "brand": "#112233",
                "mutedTone": 244
              },
              "colors": {
                "accent": "brand",
                "muted": "mutedTone",
                "warning": "#ffcc00",
                "success": 46,
                "error": 196
              }
            }
            """);

        var module = new PiCliModule(
            cwd,
            new StringReader(""),
            new StringBuilder(),
            new StringBuilder(),
            new PiCliParser(),
            testClient(),
            null,
            () -> {
                throw new AssertionError("terminal should not be created");
            }
        );
        var session = createSession(module, new PiCliParser().parse(List.of("--print")));

        assertThat(session.settingsSelection().availableThemes()).contains("ocean", "dark", "light");
        PiCliAnsi.setTheme("ocean");
        assertThat(PiCliAnsi.accent("x")).isEqualTo("\u001b[38;2;17;34;51mx\u001b[0m");
    }

    @Test
    void noThemesSkipsDiscoveryButExplicitThemeStillLoads() throws Exception {
        var cwd = tempDir.resolve("workspace");
        Files.createDirectories(cwd.resolve(".pi").resolve("themes"));
        Files.writeString(cwd.resolve(".pi").resolve("themes").resolve("project.json"), """
            {
              "name": "project",
              "colors": {
                "accent": "#112233",
                "muted": 244,
                "warning": "#ffcc00",
                "success": 46,
                "error": 196
              }
            }
            """);
        var explicitTheme = tempDir.resolve("explicit.json");
        Files.writeString(explicitTheme, """
            {
              "name": "explicit",
              "colors": {
                "accent": "#335577",
                "muted": 243,
                "warning": "#ffaa00",
                "success": 40,
                "error": 160
              }
            }
            """);

        var module = new PiCliModule(
            cwd,
            new StringReader(""),
            new StringBuilder(),
            new StringBuilder(),
            new PiCliParser(),
            testClient(),
            null,
            () -> {
                throw new AssertionError("terminal should not be created");
            }
        );
        var session = createSession(module, new PiCliParser().parse(List.of("--print", "--no-themes", "--theme", explicitTheme.toString())));

        assertThat(session.settingsSelection().availableThemes()).contains("explicit", "dark", "light");
        assertThat(session.settingsSelection().availableThemes()).doesNotContain("project");
    }

    private static PiInteractiveSession createSession(PiCliModule module, PiCliArgs args) throws Exception {
        Method method = PiCliModule.class.getDeclaredMethod("createDefaultSession", PiCliArgs.class);
        method.setAccessible(true);
        return (PiInteractiveSession) method.invoke(module, args);
    }

    private static PiAiClient testClient() {
        var models = new ModelRegistry();
        models.register(new Model(
            "test-model",
            "Test Model",
            "openai-responses",
            "openai",
            "https://example.com",
            false,
            List.of("text"),
            new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
            128_000,
            8_192,
            null,
            null
        ));
        return new PiAiClient(new ApiProviderRegistry(), models, CredentialResolver.defaultResolver());
    }
}
