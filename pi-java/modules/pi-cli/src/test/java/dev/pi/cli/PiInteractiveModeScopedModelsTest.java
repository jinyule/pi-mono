package dev.pi.cli;

import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentState;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.Subscription;
import dev.pi.tui.VirtualTerminal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class PiInteractiveModeScopedModelsTest {
    @Test
    void scopedModelsSlashCommandShowsNoModelsStatusWhenEmpty() {
        var session = new FakeScopedModelsSession();
        session.selection = new PiInteractiveSession.ScopedModelsSelection(List.of(), List.of(), false);
        var terminal = new VirtualTerminal(90, 18);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("/scoped-models");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("No models available")));

        mode.stop();
    }

    private static void waitFor(CheckedCondition condition) {
        var deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.check()) {
                return;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting", exception);
            }
        }
        throw new AssertionError("Condition was not met in time");
    }

    @FunctionalInterface
    private interface CheckedCondition {
        boolean check();
    }

    private static final class FakeScopedModelsSession implements PiInteractiveSession {
        private final Model model = new Model(
            "gpt-5",
            "GPT-5",
            "openai-responses",
            "openai",
            "https://example.com",
            true,
            List.of("text"),
            new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
            200_000,
            8_192,
            null,
            null
        );
        private final AgentState state = new AgentState("", model, null, List.of(), List.of(), false, null, Set.of(), null);
        private final List<SelectableModel> allModels = List.of(
            new SelectableModel(0, "openai", "gpt-5", "GPT-5", "off", true, true, 200_000),
            new SelectableModel(1, "anthropic", "claude-3-7-sonnet", "Claude 3.7 Sonnet", "off", false, true, 200_000),
            new SelectableModel(2, "google", "gemini-2.5-pro", "Gemini 2.5 Pro", "off", false, false, 1_000_000)
        );
        private volatile ScopedModelsSelection selection = new ScopedModelsSelection(allModels, List.of(), false);

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
            listener.accept(state);
            return () -> {
            };
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
        public ScopedModelsSelection scopedModelsSelection() {
            return selection;
        }

        @Override
        public void updateScopedModels(List<String> enabledModelIds) {
            selection = new ScopedModelsSelection(allModels, List.copyOf(enabledModelIds), !enabledModelIds.isEmpty());
        }

        @Override
        public void saveScopedModels(List<String> enabledModelIds) {
            selection = new ScopedModelsSelection(allModels, List.copyOf(enabledModelIds), !enabledModelIds.isEmpty());
        }

        @Override
        public String cwd() {
            return "/workspace";
        }
    }
}
