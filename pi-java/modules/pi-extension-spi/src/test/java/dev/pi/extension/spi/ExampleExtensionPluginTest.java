package dev.pi.extension.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExampleExtensionPluginTest {
    @TempDir
    Path tempDir;

    @Test
    void checkedInExamplePluginLoadsAndHotReloads() throws Exception {
        var exampleProject = copyExampleProject();
        var jarPath = ExtensionTestJars.compileProjectJar(tempDir, exampleProject, "minimal-extension-example");

        try (var runtime = new ExtensionRuntime(java.util.List.of(jarPath))) {
            var initial = runtime.snapshot();
            var initialExtension = initial.extensions().getFirst();
            var initialClassLoader = initialExtension.classLoader();

            assertThat(initial.extensions()).hasSize(1);
            assertThat(initialExtension.id()).isEqualTo("minimal-extension");
            assertThat(initialExtension.commandDefinitions()).containsKey("example.command");
            assertThat(initialExtension.flagDefinitions()).containsKey("example.enabled");
            assertThat(initialExtension.shortcutDefinitions()).containsKey("ctrl+alt+e");
            assertThat(initial.eventBus().hasHandlers(SessionStartEvent.class)).isTrue();

            var sourceFile = exampleProject.resolve("src/main/java/dev/pi/examples/MinimalExtension.java");
            var updatedSource = Files.readString(sourceFile, StandardCharsets.UTF_8)
                .replace("\"example.command\"", "\"example.command.reloaded\"");
            Files.writeString(sourceFile, updatedSource, StandardCharsets.UTF_8);

            ExtensionTestJars.compileProjectJar(tempDir, exampleProject, "minimal-extension-example");
            var reloaded = runtime.reload();
            var reloadedExtension = reloaded.extensions().getFirst();

            assertThat(initialClassLoader.isClosed()).isTrue();
            assertThat(reloadedExtension.classLoader()).isNotSameAs(initialClassLoader);
            assertThat(reloadedExtension.commandDefinitions()).containsKey("example.command.reloaded");
            assertThat(reloadedExtension.commandDefinitions()).doesNotContainKey("example.command");
        }
    }

    private Path copyExampleProject() throws Exception {
        var source = Path.of(System.getProperty("user.dir")).resolve("../../examples/minimal-extension").toAbsolutePath().normalize();
        var target = tempDir.resolve("minimal-extension");

        try (var paths = Files.walk(source)) {
            for (var path : paths.sorted(Comparator.naturalOrder()).toList()) {
                var relative = source.relativize(path);
                var destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
        }

        return target;
    }
}
