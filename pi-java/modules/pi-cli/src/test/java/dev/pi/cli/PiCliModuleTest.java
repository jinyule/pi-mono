package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentMessage;
import dev.pi.agent.runtime.AgentState;
import dev.pi.ai.PiAiClient;
import dev.pi.ai.auth.CredentialResolver;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.ai.registry.ApiProviderRegistry;
import dev.pi.ai.registry.ModelRegistry;
import dev.pi.ai.stream.Subscription;
import dev.pi.session.SessionManager;
import dev.pi.tui.Terminal;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiCliModuleTest {
    @Test
    void exposesStableModuleMetadata() {
        var module = new PiCliModule();

        assertThat(module.id()).isEqualTo("pi-cli");
        assertThat(module.description()).contains("CLI entrypoint");
        assertThat(module.parser()).isNotNull();
        assertThat(module.application()).isNotNull();
    }

    @Test
    void wiresRealListModelsHandler() {
        var stdout = new StringBuilder();
        var module = new PiCliModule(
            Path.of("."),
            new StringReader(""),
            stdout,
            new StringBuilder(),
            new PiCliParser(),
            aiClientWithModels(model("claude-3-7-sonnet", "anthropic")),
            args -> new FakePrintSession(),
            NoOpTerminal::new
        );

        module.run("--list-models").toCompletableFuture().join();

        assertThat(stdout.toString())
            .contains("provider")
            .contains("anthropic")
            .contains("claude-3-7-sonnet");
    }

    @Test
    void wiresRealExportHandler(@TempDir Path tempDir) throws Exception {
        var sessionFile = tempDir.resolve("session.jsonl");
        var outputFile = tempDir.resolve("session.html");
        var session = SessionManager.create(sessionFile, tempDir.toString());
        session.appendMessage(new Message.UserMessage(List.of(new TextContent("export me", null)), 1L));
        session.appendMessage(assistantMessage("done"));

        var stdout = new StringBuilder();
        var module = new PiCliModule(
            tempDir,
            new StringReader(""),
            stdout,
            new StringBuilder(),
            new PiCliParser(),
            aiClientWithModels(model("claude-3-7-sonnet", "anthropic")),
            args -> new FakePrintSession(),
            NoOpTerminal::new
        );

        module.run("--export", sessionFile.toString(), outputFile.toString()).toCompletableFuture().join();

        assertThat(Files.exists(outputFile)).isTrue();
        assertThat(stdout.toString()).contains("Exported to:");
    }

    @Test
    void wiresRealPrintModeHandler() {
        var stdout = new StringBuilder();
        var module = new PiCliModule(
            Path.of("."),
            new StringReader(""),
            stdout,
            new StringBuilder(),
            new PiCliParser(),
            aiClientWithModels(model("claude-3-7-sonnet", "anthropic")),
            args -> new FakePrintSession(),
            NoOpTerminal::new
        );

        module.run("--print", "hello", "world").toCompletableFuture().join();

        assertThat(stdout.toString()).contains("Ack: hello").contains("world");
    }

    private static PiAiClient aiClientWithModels(Model... models) {
        var registry = new ModelRegistry();
        registry.registerAll(List.of(models));
        return new PiAiClient(new ApiProviderRegistry(), registry, CredentialResolver.defaultResolver());
    }

    private static Model model(String id, String provider) {
        return new Model(
            id,
            id,
            "anthropic-messages",
            provider,
            "https://example.com",
            true,
            List.of("text"),
            new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
            200_000,
            8_192,
            null,
            null
        );
    }

    private static Message.AssistantMessage assistantMessage(String text) {
        return new Message.AssistantMessage(
            List.of(new TextContent(text, null)),
            "anthropic-messages",
            "anthropic",
            "claude-3-7-sonnet",
            new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
            StopReason.STOP,
            null,
            2L
        );
    }

    private static final class FakePrintSession implements PiInteractiveSession {
        private AgentState state = new AgentState(
            "",
            model("claude-3-7-sonnet", "anthropic"),
            null,
            List.of(),
            List.of(),
            false,
            null,
            Set.of(),
            null
        );

        @Override
        public String sessionId() {
            return "session";
        }

        @Override
        public AgentState state() {
            return state;
        }

        @Override
        public Subscription subscribe(java.util.function.Consumer<AgentEvent> listener) {
            return () -> {
            };
        }

        @Override
        public Subscription subscribeState(java.util.function.Consumer<AgentState> listener) {
            listener.accept(state);
            return () -> {
            };
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> prompt(String text) {
            state = state.withMessages(List.of(
                new AgentMessage.UserMessage(List.of(new TextContent(text, null)), 1L),
                new AgentMessage.AssistantMessage(
                    List.of(new TextContent("Ack: " + text, null)),
                    "anthropic-messages",
                    "anthropic",
                    "claude-3-7-sonnet",
                    new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                    StopReason.STOP,
                    null,
                    2L
                )
            ));
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> resume() {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> waitForIdle() {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public void abort() {
        }
    }

    private static final class NoOpTerminal implements Terminal {
        @Override
        public void start(dev.pi.tui.InputHandler onInput, Runnable onResize) {
        }

        @Override
        public void stop() {
        }

        @Override
        public void write(String data) {
        }

        @Override
        public int columns() {
            return 80;
        }

        @Override
        public int rows() {
            return 24;
        }
    }
}
