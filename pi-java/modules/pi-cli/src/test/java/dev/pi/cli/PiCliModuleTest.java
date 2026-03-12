package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import dev.pi.session.Settings;
import dev.pi.session.SettingsManager;
import dev.pi.session.SessionManager;
import dev.pi.tui.EditorAction;
import dev.pi.tui.EditorKeybindings;
import dev.pi.tui.Terminal;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
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

    @Test
    void wiresRealHelpAndVersionHandlers() {
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

        module.run("--help").toCompletableFuture().join();
        module.run("--version").toCompletableFuture().join();

        assertThat(stdout.toString())
            .contains("Usage:")
            .contains("--provider <name>")
            .contains("pi-java 0.1.0-SNAPSHOT");
    }

    @Test
    void wiresFileArgsIntoPrintPrompt(@TempDir Path tempDir) throws Exception {
        var file = tempDir.resolve("prompt.md");
        Files.writeString(file, "Context line", StandardCharsets.UTF_8);
        var session = new CapturingPromptSession();
        var stdout = new StringBuilder();
        var module = new PiCliModule(
            tempDir,
            new StringReader(""),
            stdout,
            new StringBuilder(),
            new PiCliParser(),
            aiClientWithModels(model("claude-3-7-sonnet", "anthropic")),
            args -> session,
            NoOpTerminal::new
        );

        module.run("--print", "@" + file, "Summarize").toCompletableFuture().join();

        assertThat(session.prompts).hasSize(1);
        assertThat(PiMessageRenderer.renderUserContent(session.prompts.getFirst().content()))
            .contains("<file name=\"" + file + "\">\nContext line\n</file>")
            .contains("Summarize");
        assertThat(stdout.toString()).contains("Ack:");
    }

    @Test
    void wiresInitialInteractivePrompt(@TempDir Path tempDir) throws Exception {
        var file = tempDir.resolve("prompt.md");
        Files.writeString(file, "Context line", StandardCharsets.UTF_8);
        var session = new CapturingPromptSession();
        var module = new PiCliModule(
            tempDir,
            new StringReader(""),
            new StringBuilder(),
            new StringBuilder(),
            new PiCliParser(),
            aiClientWithModels(model("claude-3-7-sonnet", "anthropic")),
            args -> session,
            NoOpTerminal::new
        );

        module.run("@" + file, "Summarize");

        waitFor(() -> !session.prompts.isEmpty());
        assertThat(PiMessageRenderer.renderUserContent(session.prompts.getFirst().content()))
            .contains("<file name=\"" + file + "\">\nContext line\n</file>")
            .contains("Summarize");
    }

    @Test
    void rejectsFileArgsInRpcMode(@TempDir Path tempDir) throws Exception {
        var file = tempDir.resolve("prompt.md");
        Files.writeString(file, "Context line", StandardCharsets.UTF_8);
        var module = new PiCliModule(
            tempDir,
            new StringReader(""),
            new StringBuilder(),
            new StringBuilder(),
            new PiCliParser(),
            aiClientWithModels(model("claude-3-7-sonnet", "anthropic")),
            args -> new CapturingPromptSession(),
            NoOpTerminal::new
        );

        assertThatThrownBy(() -> module.run("--mode", "rpc", "@" + file).toCompletableFuture().join())
            .hasRootCauseMessage("@file arguments are not supported in RPC mode");
    }

    @Test
    void interactiveRunCompletesWhenUserExits() {
        var terminal = new ControllableTerminal();
        var module = new PiCliModule(
            Path.of("."),
            new StringReader(""),
            new StringBuilder(),
            new StringBuilder(),
            new PiCliParser(),
            aiClientWithModels(model("claude-3-7-sonnet", "anthropic")),
            args -> new CapturingPromptSession(),
            () -> terminal
        );

        var run = module.run().toCompletableFuture();

        assertThat(run).isNotDone();
        terminal.sendInput("\u0004");
        run.join();

        assertThat(terminal.stopped).isTrue();
    }

    @Test
    void loadsKeybindingsFromAgentDir(@TempDir Path tempDir) throws Exception {
        var agentDir = tempDir.resolve(".pi").resolve("agent");
        Files.createDirectories(agentDir);
        Files.writeString(
            agentDir.resolve("keybindings.json"),
            """
            {
              "interrupt": "alt+x",
              "clear": "alt+c",
              "exit": "alt+q",
              "resume": "alt+u",
              "cycleModelForward": "alt+p",
              "cycleModelBackward": "alt+o",
              "cycleThinkingLevel": "alt+i",
              "selectModel": "alt+l",
              "expandTools": "alt+h",
              "toggleThinking": "ctrl+t",
              "followUp": "alt+enter",
              "dequeue": "alt+up",
              "pasteImage": "alt+z",
              "newSession": "alt+m",
              "tree": "alt+t",
              "fork": "alt+f",
              "toggleSessionSort": "ctrl+g",
              "toggleSessionNamedFilter": ["alt+n"],
              "tab": "shift+tab"
            }
            """,
            StandardCharsets.UTF_8
        );

        var previous = EditorKeybindings.global();
        var previousApp = PiAppKeybindings.global();
        try {
            EditorKeybindings.setGlobal(new EditorKeybindings());
            PiAppKeybindings.setGlobal(new PiAppKeybindings());
            var module = new PiCliModule(
                tempDir,
                new StringReader(""),
                new StringBuilder(),
                new StringBuilder(),
                new PiCliParser(),
                aiClientWithModels(model("claude-3-7-sonnet", "anthropic")),
                args -> new FakePrintSession(),
                NoOpTerminal::new,
                new PiCliKeybindingsLoader(agentDir)
            );

            module.run("--help").toCompletableFuture().join();

            assertThat(EditorKeybindings.global().getKeys(EditorAction.SESSION_SORT_TOGGLE)).containsExactly("ctrl+g");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.INTERRUPT)).containsExactly("alt+x");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.CLEAR)).containsExactly("alt+c");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.EXIT)).containsExactly("alt+q");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.RESUME)).containsExactly("alt+u");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.CYCLE_MODEL_FORWARD)).containsExactly("alt+p");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.CYCLE_MODEL_BACKWARD)).containsExactly("alt+o");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.CYCLE_THINKING_LEVEL)).containsExactly("alt+i");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.SELECT_MODEL)).containsExactly("alt+l");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.EXPAND_TOOLS)).containsExactly("alt+h");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.TOGGLE_THINKING)).containsExactly("ctrl+t");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.FOLLOW_UP)).containsExactly("alt+enter");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.DEQUEUE)).containsExactly("alt+up");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.PASTE_IMAGE)).containsExactly("alt+z");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.NEW_SESSION)).containsExactly("alt+m");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.TREE)).containsExactly("alt+t");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.FORK)).containsExactly("alt+f");
            assertThat(PiAppKeybindings.global().getKeys(PiAppAction.TOGGLE_SESSION_NAMED_FILTER)).containsExactly("alt+n");
            assertThat(EditorKeybindings.global().getKeys(EditorAction.SESSION_SCOPE_TOGGLE)).containsExactly("shift+tab");
        } finally {
            EditorKeybindings.setGlobal(previous);
            PiAppKeybindings.setGlobal(previousApp);
        }
    }

    @Test
    void resolvesScopedCycleModelsFromPatterns() {
        var registry = new ModelRegistry();
        registry.registerAll(List.of(
            model("claude-3-7-sonnet", "anthropic"),
            model("claude-3-5-haiku", "anthropic"),
            model("gpt-4o", "openai")
        ));

        var scoped = PiCliModule.resolveScopedCycleModels(List.of("anthropic/*:high", "gpt-4o"), registry);

        assertThat(scoped)
            .extracting(cycleModel -> cycleModel.model().provider() + "/" + cycleModel.model().id())
            .containsExactly("anthropic/claude-3-7-sonnet", "anthropic/claude-3-5-haiku", "openai/gpt-4o");
        assertThat(scoped.getFirst().thinkingLevel()).isEqualTo(dev.pi.ai.model.ThinkingLevel.HIGH);
        assertThat(scoped.getLast().thinkingLevel()).isNull();
    }

    @Test
    void resolveModelUsesSavedDefaultProviderAndModel() {
        var registry = new ModelRegistry();
        registry.registerAll(List.of(
            model("claude-3-7-sonnet", "anthropic"),
            model("gpt-4o", "openai")
        ));
        var settingsManager = SettingsManager.inMemory(
            Settings.empty().withMutations(root -> {
                root.put("defaultProvider", "openai");
                root.put("defaultModel", "gpt-4o");
            }),
            Settings.empty()
        );

        var resolved = PiCliModule.resolveModel(defaultArgs(), registry, List.of(), settingsManager);

        assertThat(resolved.provider()).isEqualTo("openai");
        assertThat(resolved.id()).isEqualTo("gpt-4o");
    }

    @Test
    void resolveModelUsesUniqueSavedDefaultModelWithoutProvider() {
        var registry = new ModelRegistry();
        registry.registerAll(List.of(
            model("claude-3-7-sonnet", "anthropic"),
            model("gpt-4o", "openai")
        ));
        var settingsManager = SettingsManager.inMemory(
            Settings.empty().withMutations(root -> root.put("defaultModel", "gpt-4o")),
            Settings.empty()
        );

        var resolved = PiCliModule.resolveModel(defaultArgs(), registry, List.of(), settingsManager);

        assertThat(resolved.provider()).isEqualTo("openai");
        assertThat(resolved.id()).isEqualTo("gpt-4o");
    }

    @Test
    void resolveModelFallsBackWhenSavedDefaultNoLongerExists() {
        var registry = new ModelRegistry();
        registry.registerAll(List.of(model("claude-3-7-sonnet", "anthropic")));
        var settingsManager = SettingsManager.inMemory(
            Settings.empty().withMutations(root -> {
                root.put("defaultProvider", "openai");
                root.put("defaultModel", "gpt-4o");
            }),
            Settings.empty()
        );

        var resolved = PiCliModule.resolveModel(defaultArgs(), registry, List.of(), settingsManager);

        assertThat(resolved.provider()).isEqualTo("anthropic");
        assertThat(resolved.id()).isEqualTo("claude-3-7-sonnet");
    }

    private static PiAiClient aiClientWithModels(Model... models) {
        var registry = new ModelRegistry();
        registry.registerAll(List.of(models));
        return new PiAiClient(new ApiProviderRegistry(), registry, CredentialResolver.defaultResolver());
    }

    private static PiCliArgs defaultArgs() {
        return new PiCliArgs(
            PiCliMode.INTERACTIVE,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
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

    private static final class ControllableTerminal implements Terminal {
        private dev.pi.tui.InputHandler inputHandler;
        private boolean stopped;

        @Override
        public void start(dev.pi.tui.InputHandler onInput, Runnable onResize) {
            this.inputHandler = onInput;
        }

        @Override
        public void stop() {
            stopped = true;
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

        private void sendInput(String data) {
            inputHandler.onInput(data);
        }
    }

    private static final class CapturingPromptSession implements PiInteractiveSession {
        private final java.util.concurrent.CopyOnWriteArrayList<AgentMessage.UserMessage> prompts = new java.util.concurrent.CopyOnWriteArrayList<>();
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
            return prompt(new AgentMessage.UserMessage(List.of(new TextContent(text, null)), 1L));
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> prompt(AgentMessage.UserMessage message) {
            prompts.add(message);
            var rendered = PiMessageRenderer.renderUserContent(message.content());
            state = state.withMessages(List.of(
                message,
                new AgentMessage.AssistantMessage(
                    List.of(new TextContent("Ack: " + rendered, null)),
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

    private static void waitFor(java.util.function.BooleanSupplier condition) {
        var deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
        throw new AssertionError("condition not met");
    }
}
