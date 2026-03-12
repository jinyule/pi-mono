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

class PiCliModuleThemeReloadTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void resetThemes() {
        PiCliAnsi.resetThemes();
    }

    @Test
    void reloadRefreshesThemeRegistryAndAvailableThemes() throws Exception {
        var cwd = tempDir.resolve("workspace");
        Files.createDirectories(cwd.resolve(".pi").resolve("themes"));
        Files.writeString(cwd.resolve(".pi").resolve("themes").resolve("ocean.json"), """
            {
              "name": "ocean",
              "colors": {
                "accent": "#112233",
                "muted": 244,
                "warning": "#ffcc00",
                "success": 46,
                "error": 196
              }
            }
            """);

        var session = createSession(cwd, List.of("--print"));
        assertThat(session.settingsSelection().availableThemes()).contains("ocean", "dark", "light");

        Files.writeString(cwd.resolve(".pi").resolve("themes").resolve("forest.json"), """
            {
              "name": "forest",
              "colors": {
                "accent": "#335577",
                "muted": 243,
                "warning": "#ffaa00",
                "success": 40,
                "error": 160
              }
            }
            """);

        var result = session.reload();

        assertThat(result.themeWarnings()).isEmpty();
        assertThat(session.settingsSelection().availableThemes()).contains("forest", "ocean", "dark", "light");
        PiCliAnsi.setTheme("forest");
        assertThat(PiCliAnsi.accent("x")).isEqualTo("\u001b[38;2;51;85;119mx\u001b[0m");
    }

    @Test
    void reloadReturnsThemeWarningsWhenDiscoveryFails() throws Exception {
        var cwd = tempDir.resolve("workspace");
        Files.createDirectories(cwd.resolve(".pi").resolve("themes"));
        var session = createSession(cwd, List.of("--print"));

        Files.writeString(cwd.resolve(".pi").resolve("themes").resolve("broken.json"), """
            {
              "name": "broken",
              "colors": {
                "accent": "#112233"
              }
            }
            """);

        var result = session.reload();

        assertThat(result.themeWarnings()).singleElement().satisfies(warning -> assertThat(warning).contains("broken.json"));
    }

    private PiInteractiveSession createSession(Path cwd, List<String> argv) throws Exception {
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
        Method method = PiCliModule.class.getDeclaredMethod("createDefaultSession", PiCliArgs.class);
        method.setAccessible(true);
        return (PiInteractiveSession) method.invoke(module, new PiCliParser().parse(argv));
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
