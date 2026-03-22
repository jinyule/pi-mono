package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentState;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.Subscription;
import dev.pi.session.InstructionResourceLoader;
import dev.pi.tui.InputHandler;
import dev.pi.tui.Terminal;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class PiInteractiveModeResourceListingTest {
    @Test
    void showsLoadedResourcesWhenQuietStartupIsDisabled() {
        var session = new FakeSession()
            .withStartupResources(new PiInteractiveSession.StartupResources(
                List.of("/workspace/project/AGENTS.md"),
                List.of("/workspace/project/ext/demo.jar"),
                List.of("/workspace/project/.pi/skills/demo"),
                List.of("/workspace/project/.pi/prompts/review.md"),
                List.of("midnight")
            ));
        var terminal = new FakeTerminal(100, 18);
        var mode = new PiInteractiveMode(session, terminal);

        try {
            mode.start();

            waitFor(() -> terminal.output().contains("[Context]"));
            var output = terminal.output();
            assertThat(output).contains("[Context]");
            assertThat(output).contains("AGENTS.md");
            assertThat(output).contains("[Extensions]");
            assertThat(output).contains("demo.jar");
            assertThat(output).contains("[Skills]");
            assertThat(output).contains("skills/demo");
            assertThat(output).contains("[Prompts]");
            assertThat(output).contains("prompts/review.md");
            assertThat(output).contains("[Themes]");
            assertThat(output).contains("midnight");
        } finally {
            mode.stop();
        }
    }

    @Test
    void hidesLoadedResourcesOnQuietStartup() {
        var session = new FakeSession()
            .withQuietStartup(true)
            .withStartupResources(new PiInteractiveSession.StartupResources(
                List.of("/workspace/project/AGENTS.md"),
                List.of("/workspace/project/ext/demo.jar"),
                List.of("/workspace/project/.pi/skills/demo"),
                List.of("/workspace/project/.pi/prompts/review.md"),
                List.of("midnight")
            ));
        var terminal = new FakeTerminal(100, 18);
        var mode = new PiInteractiveMode(session, terminal);

        try {
            mode.start();

            waitFor(() -> terminal.output().contains("Ready"));
            var output = terminal.output();
            assertThat(output).doesNotContain("[Context]");
            assertThat(output).doesNotContain("[Extensions]");
            assertThat(output).doesNotContain("[Skills]");
            assertThat(output).doesNotContain("[Prompts]");
            assertThat(output).doesNotContain("[Themes]");
        } finally {
            mode.stop();
        }
    }

    @Test
    void showsReloadDiagnosticsWithoutVerboseListingOnQuietStartup() {
        var session = new FakeSession()
            .withQuietStartup(true)
            .withStartupResources(new PiInteractiveSession.StartupResources(
                List.of("/workspace/project/AGENTS.md"),
                List.of("/workspace/project/ext/demo.jar"),
                List.of("/workspace/project/.pi/skills/demo"),
                List.of("/workspace/project/.pi/prompts/review.md"),
                List.of("midnight")
            ))
            .withReloadResult(new PiInteractiveSession.ReloadResult(
                List.of(),
                List.of(new InstructionResourceLoader.ResourceLoadError(
                    java.nio.file.Path.of("/workspace/project/AGENTS.md"),
                    new IOException("duplicate resource")
                )),
                List.of("Theme path not found: /workspace/project/.pi/themes/missing.json"),
                List.of()
            ));
        var terminal = new FakeTerminal(100, 18);
        var mode = new PiInteractiveMode(session, terminal);

        try {
            mode.start();
            terminal.sendInput("/reload");
            terminal.sendInput("\r");

            waitFor(() -> terminal.output().contains("[Reload warnings]"));
            var output = terminal.output();
            assertThat(output).contains("[Reload warnings]");
            assertThat(output).contains("duplicate resource");
            assertThat(output).contains("Theme path not found");
            assertThat(output).doesNotContain("[Context]");
            assertThat(output).doesNotContain("[Extensions]");
            assertThat(output).doesNotContain("[Themes]");
        } finally {
            mode.stop();
        }
    }

    private static final class FakeSession implements PiInteractiveSession {
        private final CopyOnWriteArrayList<Consumer<AgentState>> stateListeners = new CopyOnWriteArrayList<>();
        private AgentState state = new AgentState("", testModel(), null, List.of(), List.of(), false, null, Set.of(), null);
        private PiInteractiveSession.StartupResources startupResources = PiInteractiveSession.StartupResources.empty();
        private PiInteractiveSession.ReloadResult reloadResult = new PiInteractiveSession.ReloadResult(List.of(), List.of(), List.of(), List.of());
        private boolean quietStartup;

        FakeSession withStartupResources(PiInteractiveSession.StartupResources startupResources) {
            this.startupResources = startupResources;
            return this;
        }

        FakeSession withReloadResult(PiInteractiveSession.ReloadResult reloadResult) {
            this.reloadResult = reloadResult;
            return this;
        }

        FakeSession withQuietStartup(boolean quietStartup) {
            this.quietStartup = quietStartup;
            return this;
        }

        @Override
        public String sessionId() {
            return "session-1";
        }

        @Override
        public AgentState state() {
            return state;
        }

        @Override
        public Subscription subscribe(Consumer<AgentEvent> listener) {
            return () -> {
            };
        }

        @Override
        public Subscription subscribeState(Consumer<AgentState> listener) {
            stateListeners.add(listener);
            Thread.ofVirtual().start(() -> listener.accept(state));
            return () -> stateListeners.remove(listener);
        }

        @Override
        public CompletionStage<Void> prompt(String text) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> resume() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> waitForIdle() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void abort() {
        }

        @Override
        public SettingsSelection settingsSelection() {
            return new SettingsSelection(
                false,
                "one-at-a-time",
                "one-at-a-time",
                "auto",
                false,
                quietStartup,
                "tree",
                "dark",
                List.of("dark", "light"),
                0,
                false,
                "off",
                List.of(),
                true,
                true
            );
        }

        @Override
        public StartupResources startupResources() {
            return startupResources;
        }

        @Override
        public ReloadResult reload() {
            return reloadResult;
        }
    }

    private static final class FakeTerminal implements Terminal {
        private final List<String> writes = new ArrayList<>();
        private final int columns;
        private final int rows;
        private InputHandler inputHandler;

        private FakeTerminal(int columns, int rows) {
            this.columns = columns;
            this.rows = rows;
        }

        @Override
        public void start(InputHandler onInput, Runnable onResize) {
            inputHandler = onInput;
        }

        @Override
        public void stop() {
            inputHandler = null;
        }

        @Override
        public void write(String data) {
            writes.add(data);
        }

        @Override
        public int columns() {
            return columns;
        }

        @Override
        public int rows() {
            return rows;
        }

        private void sendInput(String data) {
            if (inputHandler != null) {
                inputHandler.onInput(data);
            }
        }

        private String output() {
            return String.join("", writes);
        }
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting", exception);
            }
        }
        throw new AssertionError("Condition was not met before timeout");
    }

    private static Model testModel() {
        return new Model(
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
        );
    }
}
