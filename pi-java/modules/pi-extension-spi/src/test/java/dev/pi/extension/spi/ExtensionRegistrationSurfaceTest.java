package dev.pi.extension.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class ExtensionRegistrationSurfaceTest {
    @TempDir
    Path tempDir;

    @Test
    void capturesShortcutAndFlagRegistrationsAndExposesFlagDefaultsToExtension() throws Exception {
        var jarPath = ExtensionTestJars.compileJar(tempDir, "fixture.extension.RegistrationPlugin", """
            package fixture.extension;

            import dev.pi.extension.spi.CommandDefinition;
            import dev.pi.extension.spi.ExtensionApi;
            import dev.pi.extension.spi.FlagDefinition;
            import dev.pi.extension.spi.PiExtension;
            import dev.pi.extension.spi.ShortcutDefinition;
            import java.util.concurrent.CompletableFuture;

            public final class RegistrationPlugin implements PiExtension {
                @Override
                public String id() {
                    return "registration-plugin";
                }

                @Override
                public void register(ExtensionApi api) {
                    api.registerFlag(new FlagDefinition("sample.mode", "Sample mode", FlagDefinition.Type.STRING, "safe"));
                    api.registerShortcut(new ShortcutDefinition("ctrl+k", "Open widget", context -> CompletableFuture.completedFuture(null)));

                    var mode = api.getFlag("sample.mode").orElse("missing");
                    api.registerCommand(new CommandDefinition(
                        "mode-" + mode,
                        "Command derived from flag default",
                        (arguments, context) -> CompletableFuture.completedFuture(null)
                    ));
                }
            }
            """);

        try (var result = new ExtensionLoader().load(jarPath)) {
            assertThat(result.failures()).isEmpty();
            var loaded = result.extensions().getFirst();

            assertThat(loaded.shortcutDefinitions()).containsOnlyKeys("ctrl+k");
            assertThat(loaded.shortcutDefinitions().get("ctrl+k").description()).isEqualTo("Open widget");

            assertThat(loaded.flagDefinitions()).containsOnlyKeys("sample.mode");
            assertThat(loaded.flagDefinitions().get("sample.mode").type()).isEqualTo(FlagDefinition.Type.STRING);
            assertThat(loaded.flagDefaults()).containsEntry("sample.mode", "safe");

            assertThat(loaded.commandDefinitions()).containsKey("mode-safe");
        }
    }

    @Test
    void recordsFailureForDuplicateShortcutRegistrationWithinExtension() throws Exception {
        var jarPath = ExtensionTestJars.compileJar(tempDir, "fixture.extension.DuplicateShortcutPlugin", """
            package fixture.extension;

            import dev.pi.extension.spi.ExtensionApi;
            import dev.pi.extension.spi.PiExtension;
            import dev.pi.extension.spi.ShortcutDefinition;
            import java.util.concurrent.CompletableFuture;

            public final class DuplicateShortcutPlugin implements PiExtension {
                @Override
                public String id() {
                    return "duplicate-shortcut-plugin";
                }

                @Override
                public void register(ExtensionApi api) {
                    api.registerShortcut(new ShortcutDefinition("ctrl+k", "First", context -> CompletableFuture.completedFuture(null)));
                    api.registerShortcut(new ShortcutDefinition("ctrl+k", "Second", context -> CompletableFuture.completedFuture(null)));
                }
            }
            """);

        try (var result = new ExtensionLoader().load(jarPath)) {
            assertThat(result.extensions()).isEmpty();
            assertThat(result.failures()).singleElement().satisfies(failure -> {
                assertThat(failure.source()).isEqualTo(jarPath);
                assertThat(failure.message()).contains("Duplicate shortcut registration");
            });
        }
    }
}
