package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentMessage;
import dev.pi.agent.runtime.AgentState;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.ThinkingLevel;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.Subscription;
import dev.pi.tui.EditorAction;
import dev.pi.tui.EditorKeybindings;
import dev.pi.tui.Terminal;
import dev.pi.tui.VirtualTerminal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class PiSettingsSelectorIntegrationTest {
    @Test
    void settingsSlashCommandOpensOverlayAndTogglesAutoCompaction() {
        var session = new FakeSettingsSession();
        var terminal = new VirtualTerminal(90, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("/settings");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Settings")));
        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Settings")
            .contains("Auto-compact")
            .contains("(auto)");

        terminal.sendInput(" ");
        terminal.sendInput("\u001b");

        waitFor(() -> !String.join("\n", terminal.getViewport()).contains("Settings"));
        waitFor(() -> !String.join("\n", terminal.getViewport()).contains("(auto)"));
        assertThat(session.updatedSettings).containsExactly("autocompact=false");

        mode.stop();
    }

    @Test
    void settingsSelectorShowsThinkingLevelForReasoningModels() {
        var session = new FakeSettingsSession().withReasoningModel();
        var terminal = new VirtualTerminal(90, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("/settings");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Thinking level")));

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Thinking level")
            .contains("minimal");

        mode.stop();
    }

    @Test
    void settingsSelectorTogglesTransport() {
        var session = new FakeSettingsSession();
        var terminal = new VirtualTerminal(90, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("/settings");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Transport")));

        terminal.sendInput("trans");
        terminal.sendInput(" ");
        terminal.sendInput("\u001b");

        waitFor(() -> !String.join("\n", terminal.getViewport()).contains("Settings"));
        assertThat(session.updatedSettings).contains("transport=sse");

        mode.stop();
    }

    @Test
    void settingsSelectorTogglesHideThinking() {
        var session = new FakeSettingsSession();
        var terminal = new VirtualTerminal(90, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("/settings");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Hide thinking")));

        terminal.sendInput("hide");
        terminal.sendInput(" ");
        terminal.sendInput("\u001b");

        waitFor(() -> !String.join("\n", terminal.getViewport()).contains("Settings"));
        assertThat(session.updatedSettings).contains("hide-thinking=true");

        mode.stop();
    }

    @Test
    void settingsSelectorTogglesQuietStartup() {
        var updates = new CopyOnWriteArrayList<String>();
        var selector = new PiSettingsSelector(
            new FakeSettingsSession().settingsSelection(),
            (settingId, value) -> updates.add(settingId + "=" + value),
            () -> {
            }
        );

        selector.handleInput("quiet");
        selector.handleInput(" ");

        assertThat(updates).contains("quiet-startup=true");
    }

    @Test
    void settingsSelectorTogglesTheme() {
        var updates = new CopyOnWriteArrayList<String>();
        var selector = new PiSettingsSelector(
            new FakeSettingsSession().settingsSelection(),
            (settingId, value) -> updates.add(settingId + "=" + value),
            () -> {
            }
        );

        selector.handleInput("theme");
        selector.handleInput(" ");

        assertThat(updates).contains("theme=light");
    }

    @Test
    void settingsSelectorTogglesDoubleEscapeAction() {
        var updates = new CopyOnWriteArrayList<String>();
        var selector = new PiSettingsSelector(
            new FakeSettingsSession().settingsSelection(),
            (settingId, value) -> updates.add(settingId + "=" + value),
            () -> {
            }
        );

        selector.handleInput("double");
        selector.handleInput(" ");

        assertThat(updates).contains("double-escape-action=fork");
    }

    @Test
    void settingsSelectorHintsReflectCustomKeybindings() {
        var previous = EditorKeybindings.global();
        try {
            EditorKeybindings.setGlobal(new EditorKeybindings(Map.of(
                EditorAction.SUBMIT, List.of("ctrl+j"),
                EditorAction.SELECT_CANCEL, List.of("alt+x")
            )));

            var selector = new PiSettingsSelector(
                new FakeSettingsSession().settingsSelection(),
                (settingId, value) -> {
                },
                () -> {
                }
            );

            assertThat(String.join("\n", selector.render(90)))
                .contains("Type to search. ctrl+j or space changes. alt+x cancels.")
                .contains("Type to search · ctrl+j/space to change · alt+x to cancel");
        } finally {
            EditorKeybindings.setGlobal(previous);
        }
    }

    private static final class FakeSettingsSession implements PiInteractiveSession {
        private final CopyOnWriteArrayList<Consumer<AgentState>> stateListeners = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<String> updatedSettings = new CopyOnWriteArrayList<>();
        private boolean autoCompactionEnabled = true;
        private String transport = "auto";
        private boolean hideThinkingBlock;
        private boolean quietStartup;
        private String doubleEscapeAction = "tree";
        private AgentState state = new AgentState(
            "",
            new Model(
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
            ),
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
        public Subscription subscribe(Consumer<AgentEvent> listener) {
            return () -> {
            };
        }

        @Override
        public Subscription subscribeState(Consumer<AgentState> listener) {
            listener.accept(state);
            stateListeners.add(listener);
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
        public boolean autoCompactionEnabled() {
            return autoCompactionEnabled;
        }

        @Override
        public ContextUsage contextUsage() {
            return new ContextUsage(0, 128_000, 0.0);
        }

        @Override
        public SettingsSelection settingsSelection() {
            return new SettingsSelection(
                autoCompactionEnabled,
                "one-at-a-time",
                "one-at-a-time",
                transport,
                hideThinkingBlock,
                quietStartup,
                doubleEscapeAction,
                "dark",
                List.of("dark", "light"),
                state.model().reasoning(),
                state.thinkingLevel() == null ? "off" : state.thinkingLevel().value(),
                List.of("off", "minimal", "low", "medium", "high", "xhigh")
            );
        }

        @Override
        public void updateSetting(String settingId, String value) {
            updatedSettings.add(settingId + "=" + value);
            if ("autocompact".equals(settingId)) {
                autoCompactionEnabled = "true".equals(value);
            }
            if ("transport".equals(settingId)) {
                transport = value;
            }
            if ("hide-thinking".equals(settingId)) {
                hideThinkingBlock = "true".equals(value);
            }
            if ("quiet-startup".equals(settingId)) {
                quietStartup = "true".equals(value);
            }
            if ("double-escape-action".equals(settingId)) {
                doubleEscapeAction = value;
            }
            if ("thinking".equals(settingId)) {
                state = state.withThinkingLevel("off".equals(value) ? null : ThinkingLevel.fromValue(value));
                emitState();
            }
        }

        private FakeSettingsSession withReasoningModel() {
            state = state.withModel(new Model(
                "reasoning-model",
                "Reasoning Model",
                "anthropic-messages",
                "anthropic",
                "https://example.com",
                true,
                List.of("text"),
                new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
                200_000,
                8_192,
                null,
                null
            )).withThinkingLevel(ThinkingLevel.MINIMAL);
            emitState();
            return this;
        }

        private void emitState() {
            for (var listener : stateListeners) {
                listener.accept(state);
            }
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
