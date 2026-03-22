package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PiScopedModelsSelectorTest {
    @Test
    void togglesCurrentModelAndSavesSelection() {
        var updated = new CopyOnWriteArrayList<List<String>>();
        var saved = new CopyOnWriteArrayList<List<String>>();
        var cancelled = new AtomicBoolean(false);
        var selector = new PiScopedModelsSelector(
            new PiInteractiveSession.ScopedModelsSelection(models(), List.of(), false),
            ids -> updated.add(List.copyOf(ids)),
            ids -> saved.add(List.copyOf(ids)),
            () -> cancelled.set(true)
        );

        selector.handleInput("\r");
        selector.handleInput("\u0013");
        selector.handleInput("\u001b");

        assertThat(updated.getLast()).containsExactly("openai/gpt-5");
        assertThat(saved.getLast()).containsExactly("openai/gpt-5");
        assertThat(cancelled.get()).isTrue();
    }

    @Test
    void clearAndEnableAllUpdateFooterState() {
        var updated = new CopyOnWriteArrayList<List<String>>();
        var selector = new PiScopedModelsSelector(
            new PiInteractiveSession.ScopedModelsSelection(models(), List.of("openai/gpt-5"), true),
            ids -> updated.add(List.copyOf(ids)),
            ids -> {
            },
            () -> {
            }
        );

        selector.handleInput("\u0018");
        assertThat(String.join("\n", selector.render(90))).contains("0/3 enabled");

        selector.handleInput("\u0001");
        assertThat(String.join("\n", selector.render(90))).contains("all enabled");
        assertThat(updated).isNotEmpty();
    }

    private static List<PiInteractiveSession.SelectableModel> models() {
        return List.of(
            new PiInteractiveSession.SelectableModel(0, "openai", "gpt-5", "GPT-5", "off", true, true, 200_000),
            new PiInteractiveSession.SelectableModel(1, "anthropic", "claude-3-7-sonnet", "Claude 3.7 Sonnet", "off", false, true, 200_000),
            new PiInteractiveSession.SelectableModel(2, "google", "gemini-2.5-pro", "Gemini 2.5 Pro", "off", false, false, 1_000_000)
        );
    }
}
