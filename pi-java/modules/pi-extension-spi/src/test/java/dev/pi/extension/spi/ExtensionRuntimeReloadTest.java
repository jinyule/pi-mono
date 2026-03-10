package dev.pi.extension.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.session.SessionManager;
import dev.pi.session.SettingsManager;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionRuntimeReloadTest {
    @TempDir
    Path tempDir;

    @Test
    void reloadRebuildsExtensionRuntimeAndClosesPreviousClassLoaders() throws Exception {
        var jarPath = compileReloadPlugin("""
            package fixture.extension;

            import dev.pi.extension.spi.CommandDefinition;
            import dev.pi.extension.spi.ExtensionApi;
            import dev.pi.extension.spi.PiExtension;
            import dev.pi.extension.spi.SessionShutdownEvent;
            import java.util.concurrent.CompletableFuture;

            public final class ReloadPlugin implements PiExtension {
                @Override
                public String id() {
                    return "reload-plugin";
                }

                @Override
                public void register(ExtensionApi api) {
                    api.on(SessionShutdownEvent.class, (event, context) -> CompletableFuture.completedFuture(null));
                    api.registerCommand(new CommandDefinition("alpha", "Initial command", (arguments, context) -> CompletableFuture.completedFuture(null)));
                }
            }
            """);

        try (var runtime = new ExtensionRuntime(java.util.List.of(jarPath))) {
            var initial = runtime.snapshot();
            var initialExtension = initial.extensions().getFirst();
            var initialClassLoader = initialExtension.classLoader();

            assertThat(initial.extensions()).hasSize(1);
            assertThat(initialExtension.commandDefinitions()).containsKey("alpha");
            assertThat(initial.eventBus().hasHandlers(SessionShutdownEvent.class)).isTrue();
            assertThat(initial.resourceDiscovery().hasHandlers()).isFalse();
            assertThat(initialClassLoader.isClosed()).isFalse();

            compileReloadPlugin("""
                package fixture.extension;

                import dev.pi.extension.spi.CommandDefinition;
                import dev.pi.extension.spi.ExtensionApi;
                import dev.pi.extension.spi.PiExtension;
                import dev.pi.extension.spi.ResourcesDiscoverEvent;
                import dev.pi.extension.spi.ResourcesDiscoverResult;
                import java.util.List;
                import java.util.concurrent.CompletableFuture;

                public final class ReloadPlugin implements PiExtension {
                    @Override
                    public String id() {
                        return "reload-plugin";
                    }

                    @Override
                    public void register(ExtensionApi api) {
                        api.registerCommand(new CommandDefinition("beta", "Reloaded command", (arguments, context) -> CompletableFuture.completedFuture(null)));
                        api.on(ResourcesDiscoverEvent.class, (event, context) -> CompletableFuture.completedFuture(
                            new ResourcesDiscoverResult(List.of("skills"), List.of(), List.of())
                        ));
                    }
                }
                """);

            var reloaded = runtime.reload();
            var reloadedExtension = reloaded.extensions().getFirst();

            assertThat(initialClassLoader.isClosed()).isTrue();
            assertThat(reloadedExtension.classLoader()).isNotSameAs(initialClassLoader);
            assertThat(reloadedExtension.classLoader().isClosed()).isFalse();
            assertThat(reloadedExtension.commandDefinitions()).containsKey("beta");
            assertThat(reloadedExtension.commandDefinitions()).doesNotContainKey("alpha");
            assertThat(reloaded.eventBus().hasHandlers(SessionShutdownEvent.class)).isFalse();
            assertThat(reloaded.resourceDiscovery().hasHandlers()).isTrue();

            var discovered = reloaded.resourceDiscovery().discover(
                new ResourcesDiscoverEvent(tempDir.resolve("project"), ResourcesDiscoverEvent.Reason.RELOAD),
                new TestExtensionContext(tempDir.resolve("project"))
            ).toCompletableFuture().join();
            assertThat(discovered.skillPaths()).singleElement().satisfies(path -> {
                assertThat(path.resolvedPath()).isEqualTo(jarPath.getParent().resolve("skills").toAbsolutePath().normalize());
            });
        }
    }

    @Test
    void closeClosesCurrentClassLoadersAndClearsSnapshot() throws Exception {
        var jarPath = compileReloadPlugin("""
            package fixture.extension;

            import dev.pi.extension.spi.ExtensionApi;
            import dev.pi.extension.spi.PiExtension;

            public final class ReloadPlugin implements PiExtension {
                @Override
                public String id() {
                    return "reload-plugin";
                }

                @Override
                public void register(ExtensionApi api) {
                }
            }
            """);

        var runtime = new ExtensionRuntime(java.util.List.of(jarPath));
        var classLoader = runtime.snapshot().extensions().getFirst().classLoader();

        runtime.close();

        assertThat(classLoader.isClosed()).isTrue();
        assertThat(runtime.snapshot().extensions()).isEmpty();
        assertThat(runtime.snapshot().failures()).isEmpty();
    }

    private Path compileReloadPlugin(String source) throws Exception {
        return ExtensionTestJars.compileJar(tempDir, "fixture.extension.ReloadPlugin", source);
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
