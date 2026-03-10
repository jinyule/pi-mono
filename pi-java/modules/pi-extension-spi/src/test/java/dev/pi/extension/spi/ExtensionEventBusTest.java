package dev.pi.extension.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.session.SessionManager;
import dev.pi.session.SettingsManager;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionEventBusTest {
    @TempDir
    Path tempDir;

    @Test
    void emitsEventsAcrossLoadedExtensionsAndCollectsFailures() throws Exception {
        var jarPath = ExtensionTestJars.compileJar(tempDir, "fixture.extension.EventPlugin", """
            package fixture.extension;

            import dev.pi.extension.spi.ExtensionApi;
            import dev.pi.extension.spi.PiExtension;
            import dev.pi.extension.spi.ResourcesDiscoverEvent;
            import dev.pi.extension.spi.ResourcesDiscoverResult;
            import java.util.List;
            import java.util.concurrent.CompletableFuture;

            public final class EventPlugin implements PiExtension {
                @Override
                public String id() {
                    return "event-plugin";
                }

                @Override
                public void register(ExtensionApi api) {
                    api.on(ResourcesDiscoverEvent.class, (event, context) -> CompletableFuture.completedFuture(
                        new ResourcesDiscoverResult(List.of("skills"), List.of("prompts"), List.of())
                    ));
                    api.on(ResourcesDiscoverEvent.class, (event, context) -> {
                        throw new IllegalStateException("boom");
                    });
                }
            }
            """);

        try (var loadResult = new ExtensionLoader().load(jarPath)) {
            assertThat(loadResult.failures()).isEmpty();

            var eventBus = new ExtensionEventBus(loadResult.extensions());
            assertThat(eventBus.hasHandlers(ResourcesDiscoverEvent.class)).isTrue();

            var dispatch = eventBus.emit(
                new ResourcesDiscoverEvent(Path.of("."), ResourcesDiscoverEvent.Reason.STARTUP),
                new TestExtensionContext()
            ).toCompletableFuture().join();

            assertThat(dispatch.results()).hasSize(1);
            var result = (ResourcesDiscoverResult) dispatch.results().getFirst();
            assertThat(result.skillPaths()).containsExactly("skills");
            assertThat(result.promptPaths()).containsExactly("prompts");
            assertThat(result.themePaths()).isEmpty();
            assertThat(dispatch.failures()).singleElement().satisfies(failure -> {
                assertThat(failure.extensionId()).isEqualTo("event-plugin");
                assertThat(failure.eventType()).isEqualTo("resources_discover");
                assertThat(failure.cause()).hasMessage("boom");
            });
        }
    }

    private static final class TestExtensionContext implements ExtensionContext {
        @Override
        public Path cwd() {
            return Path.of(".");
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
