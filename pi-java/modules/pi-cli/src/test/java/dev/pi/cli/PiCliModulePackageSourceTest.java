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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiCliModulePackageSourceTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void resetThemes() {
        PiCliAnsi.resetThemes();
    }

    @Test
    void createDefaultSessionLoadsThemesFromProjectPackageSources() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var packageRoot = tempDir.resolve("theme-pack");
        Files.createDirectories(cwd.resolve(".pi"));
        Files.createDirectories(packageRoot.resolve("dist").resolve("themes"));
        Files.writeString(packageRoot.resolve("dist").resolve("themes").resolve("package-theme.json"), """
            {
              "name": "package-theme",
              "colors": {
                "accent": "#112233",
                "muted": 244,
                "warning": "#ffcc00",
                "success": 46,
                "error": 196
              }
            }
            """);
        Files.writeString(
            cwd.resolve(".pi").resolve("settings.json"),
            """
            {
              "packages": [
                {
                  "source": "%s",
                  "themes": ["dist/themes"]
                }
              ]
            }
            """.formatted(escapeJson(packageRoot.toString())),
            StandardCharsets.UTF_8
        );

        var session = createSession(cwd, List.of("--print"));

        assertThat(session.settingsSelection().availableThemes()).contains("package-theme", "dark", "light");
    }

    @Test
    void createDefaultSessionExposesExtensionPathsFromProjectPackageSources() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var packageRoot = tempDir.resolve("extension-pack");
        Files.createDirectories(cwd.resolve(".pi"));
        Files.createDirectories(packageRoot.resolve("dist").resolve("extensions"));
        Files.writeString(packageRoot.resolve("dist").resolve("extensions").resolve("demo.jar"), "");
        Files.writeString(
            cwd.resolve(".pi").resolve("settings.json"),
            """
            {
              "packages": [
                {
                  "source": "%s",
                  "extensions": ["dist/extensions/demo.jar"]
                }
              ]
            }
            """.formatted(escapeJson(packageRoot.toString())),
            StandardCharsets.UTF_8
        );

        var session = createSession(cwd, List.of("--print"));

        assertThat(session.startupResources().extensionPaths()).containsExactly(
            packageRoot.resolve("dist").resolve("extensions").resolve("demo.jar").toAbsolutePath().normalize().toString()
        );
    }

    @Test
    void createDefaultSessionLoadsThemesFromInstalledProjectNpmPackageSources() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var installedPackage = cwd.resolve(".pi").resolve("npm").resolve("node_modules").resolve("@acme").resolve("theme-pack");
        Files.createDirectories(installedPackage.resolve("themes"));
        Files.writeString(installedPackage.resolve("themes").resolve("npm-theme.json"), """
            {
              "name": "npm-theme",
              "colors": {
                "accent": "#112233",
                "muted": 244,
                "warning": "#ffcc00",
                "success": 46,
                "error": 196
              }
            }
            """);
        Files.createDirectories(cwd.resolve(".pi"));
        Files.writeString(
            cwd.resolve(".pi").resolve("settings.json"),
            """
            {
              "packages": [
                "npm:@acme/theme-pack"
              ]
            }
            """,
            StandardCharsets.UTF_8
        );

        var session = createSession(cwd, List.of("--print"));

        assertThat(session.settingsSelection().availableThemes()).contains("npm-theme", "dark", "light");
    }

    @Test
    void createDefaultSessionExposesExtensionPathsFromInstalledProjectGitPackageSources() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var installedPackage = cwd.resolve(".pi").resolve("git").resolve("github.com").resolve("acme").resolve("extension-pack");
        Files.createDirectories(installedPackage.resolve("dist").resolve("extensions"));
        Files.writeString(installedPackage.resolve("dist").resolve("extensions").resolve("git-demo.jar"), "");
        Files.createDirectories(cwd.resolve(".pi"));
        Files.writeString(
            cwd.resolve(".pi").resolve("settings.json"),
            """
            {
              "packages": [
                {
                  "source": "git:https://github.com/acme/extension-pack.git",
                  "extensions": ["dist/extensions/git-demo.jar"]
                }
              ]
            }
            """,
            StandardCharsets.UTF_8
        );

        var session = createSession(cwd, List.of("--print"));

        assertThat(session.startupResources().extensionPaths()).containsExactly(
            installedPackage.resolve("dist").resolve("extensions").resolve("git-demo.jar").toAbsolutePath().normalize().toString()
        );
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

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\");
    }
}
