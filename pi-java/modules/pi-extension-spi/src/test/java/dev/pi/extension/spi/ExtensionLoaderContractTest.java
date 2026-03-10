package dev.pi.extension.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionLoaderContractTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsServiceExtensionFromJarAndCapturesRegistrations() throws Exception {
        var jarPath = ExtensionTestJars.compileJar(tempDir, "fixture.extension.TestPlugin", """
            package fixture.extension;

            import com.fasterxml.jackson.databind.JsonNode;
            import com.fasterxml.jackson.databind.node.JsonNodeFactory;
            import dev.pi.agent.runtime.AgentTool;
            import dev.pi.agent.runtime.AgentToolResult;
            import dev.pi.ai.model.TextContent;
            import dev.pi.extension.spi.CommandDefinition;
            import dev.pi.extension.spi.ExtensionApi;
            import dev.pi.extension.spi.SessionStartEvent;
            import dev.pi.extension.spi.MessageRenderContext;
            import dev.pi.extension.spi.MessageRenderer;
            import dev.pi.extension.spi.PiExtension;
            import dev.pi.extension.spi.ToolDefinition;
            import java.util.List;
            import java.util.concurrent.CompletableFuture;
            import java.util.concurrent.CompletionStage;
            import java.util.function.Consumer;

            public final class TestPlugin implements PiExtension {
                @Override
                public String id() {
                    return "test-extension";
                }

                @Override
                public String version() {
                    return "1.2.3";
                }

                @Override
                public void register(ExtensionApi api) {
                    api.on(SessionStartEvent.class, (event, context) -> CompletableFuture.completedFuture(null));
                    api.registerTool(new ToolDefinition<>(new EchoTool()));
                    api.registerCommand(new CommandDefinition("sample", "Sample command", (arguments, context) -> CompletableFuture.completedFuture(null)));
                    api.registerMessageRenderer("test.custom", new PlainRenderer());
                }

                static final class EchoTool implements AgentTool<Void> {
                    @Override
                    public String name() {
                        return "echo";
                    }

                    @Override
                    public String label() {
                        return "Echo";
                    }

                    @Override
                    public String description() {
                        return "Echo tool";
                    }

                    @Override
                    public JsonNode parametersSchema() {
                        return JsonNodeFactory.instance.objectNode().put("type", "object");
                    }

                    @Override
                    public CompletionStage<AgentToolResult<Void>> execute(String toolCallId, JsonNode arguments, Consumer<AgentToolResult<Void>> onUpdate) {
                        return CompletableFuture.completedFuture(new AgentToolResult<>(List.of(new TextContent("echo", null)), null));
                    }
                }

                static final class PlainRenderer implements MessageRenderer<String, String> {
                    @Override
                    public String render(String message, MessageRenderContext context) {
                        return "rendered:" + message + ":" + context.expanded();
                    }
                }
            }
            """);

        try (var result = new ExtensionLoader().load(jarPath)) {
            assertThat(result.failures()).isEmpty();
            assertThat(result.extensions()).hasSize(1);

            var loaded = result.extensions().getFirst();
            assertThat(loaded.id()).isEqualTo("test-extension");
            assertThat(loaded.version()).isEqualTo("1.2.3");
            assertThat(loaded.source()).isEqualTo(jarPath);
            assertThat(loaded.classLoader()).isInstanceOf(URLClassLoader.class);
            assertThat(loaded.classLoader()).isNotSameAs(TestPluginMarker.class.getClassLoader());
            assertThat(loaded.classLoader().isClosed()).isFalse();
            assertThat(loaded.eventHandlers()).containsKey(SessionStartEvent.class);
            assertThat(loaded.toolDefinitions()).containsOnlyKeys("echo");
            assertThat(loaded.toolDefinitions().get("echo").label()).isEqualTo("Echo");
            assertThat(loaded.commandDefinitions()).containsOnlyKeys("sample");
            assertThat(loaded.commandDefinitions().get("sample").description()).isEqualTo("Sample command");
            assertThat(loaded.messageRenderers()).containsOnlyKeys("test.custom");

            @SuppressWarnings("unchecked")
            var renderer = (MessageRenderer<String, String>) loaded.messageRenderers().get("test.custom");
            assertThat(renderer.render("payload", new MessageRenderContext(true))).isEqualTo("rendered:payload:true");
        }
    }

    @Test
    void recordsFailureWhenJarHasNoServiceEntry() throws Exception {
        var jarPath = tempDir.resolve("empty-extension.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            // Intentionally empty.
        }

        try (var result = new ExtensionLoader().load(jarPath)) {
            assertThat(result.extensions()).isEmpty();
            assertThat(result.failures()).singleElement().satisfies(failure -> {
                assertThat(failure.source()).isEqualTo(jarPath);
                assertThat(failure.message()).contains("No PiExtension services found");
            });
        }
    }

    private static final class TestPluginMarker {
        private TestPluginMarker() {
        }
    }
}
