package dev.pi.extension.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.session.SessionManager;
import dev.pi.session.SettingsManager;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionResourceDiscoveryTest {
    @TempDir
    Path tempDir;

    @Test
    void discoversAndNormalizesPathsRelativeToExtensionSource() throws Exception {
        var jarPath = ExtensionTestJars.compileJar(tempDir, "fixture.extension.ResourcePlugin", """
            package fixture.extension;

            import dev.pi.extension.spi.ExtensionApi;
            import dev.pi.extension.spi.PiExtension;
            import dev.pi.extension.spi.ResourcesDiscoverEvent;
            import dev.pi.extension.spi.ResourcesDiscoverResult;
            import java.util.List;
            import java.util.concurrent.CompletableFuture;

            public final class ResourcePlugin implements PiExtension {
                @Override
                public String id() {
                    return "resource-plugin";
                }

                @Override
                public void register(ExtensionApi api) {
                    api.on(ResourcesDiscoverEvent.class, (event, context) -> CompletableFuture.completedFuture(
                        new ResourcesDiscoverResult(
                            List.of("skills", "./skills"),
                            List.of("prompts/templates"),
                            List.of("../shared-themes")
                        )
                    ));
                }
            }
            """);

        try (var loadResult = new ExtensionLoader().load(jarPath)) {
            var discovery = new ExtensionResourceDiscovery(loadResult.extensions());
            var discovered = discovery.discover(
                new ResourcesDiscoverEvent(tempDir.resolve("project"), ResourcesDiscoverEvent.Reason.STARTUP),
                new TestExtensionContext(tempDir.resolve("project"))
            ).toCompletableFuture().join();

            assertThat(discovered.failures()).isEmpty();
            assertThat(discovered.skillPaths()).singleElement().satisfies(path -> {
                assertThat(path.declaredPath()).isEqualTo("skills");
                assertThat(path.resolvedPath()).isEqualTo(jarPath.getParent().resolve("skills").toAbsolutePath().normalize());
                assertThat(path.extensionId()).isEqualTo("resource-plugin");
                assertThat(path.baseDir()).isEqualTo(jarPath.getParent().toAbsolutePath().normalize());
            });
            assertThat(discovered.promptPaths()).singleElement().satisfies(path -> {
                assertThat(path.resolvedPath()).isEqualTo(jarPath.getParent().resolve("prompts/templates").toAbsolutePath().normalize());
            });
            assertThat(discovered.themePaths()).singleElement().satisfies(path -> {
                assertThat(path.resolvedPath()).isEqualTo(jarPath.getParent().resolve("../shared-themes").toAbsolutePath().normalize());
            });
        }
    }

    @Test
    void capturesHandlerFailuresAndInvalidReturnedPaths() throws Exception {
        var jarPath = ExtensionTestJars.compileJar(tempDir, "fixture.extension.BrokenResourcePlugin", """
            package fixture.extension;

            import dev.pi.extension.spi.ExtensionApi;
            import dev.pi.extension.spi.PiExtension;
            import dev.pi.extension.spi.ResourcesDiscoverEvent;
            import dev.pi.extension.spi.ResourcesDiscoverResult;
            import java.util.List;
            import java.util.concurrent.CompletableFuture;

            public final class BrokenResourcePlugin implements PiExtension {
                @Override
                public String id() {
                    return "broken-resource-plugin";
                }

                @Override
                public void register(ExtensionApi api) {
                    api.on(ResourcesDiscoverEvent.class, (event, context) -> CompletableFuture.completedFuture(
                        new ResourcesDiscoverResult(List.of(" "), List.of(), List.of())
                    ));
                    api.on(ResourcesDiscoverEvent.class, (event, context) -> {
                        throw new IllegalStateException("boom");
                    });
                }
            }
            """);

        try (var loadResult = new ExtensionLoader().load(jarPath)) {
            var discovery = new ExtensionResourceDiscovery(loadResult.extensions());
            var discovered = discovery.discover(
                new ResourcesDiscoverEvent(tempDir.resolve("project"), ResourcesDiscoverEvent.Reason.RELOAD),
                new TestExtensionContext(tempDir.resolve("project"))
            ).toCompletableFuture().join();

            assertThat(discovered.skillPaths()).isEmpty();
            assertThat(discovered.promptPaths()).isEmpty();
            assertThat(discovered.themePaths()).isEmpty();
            assertThat(discovered.failures()).hasSize(2);
            assertThat(discovered.failures().get(0).cause()).hasMessageContaining("resource path must be a non-empty string");
            assertThat(discovered.failures().get(1).cause()).hasMessage("boom");
        }
    }

    private static final class TestExtensionContext implements ExtensionContext {
        private final Path cwd;

        private TestExtensionContext(Path cwd) {
            this.cwd = cwd;
        }

        @Override
        public Path cwd() {
            return cwd;
        }

        @Override
        public SessionManager sessionManager() {
            return null;
        }

        @Override
        public SettingsManager settingsManager() {
            return null;
        }

        @Override
        public Optional<ExtensionUiContext> ui() {
            return Optional.empty();
        }
    }
}
