package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PiModelSelectorTest {
    @Test
    void rendersCurrentModelFirstWithRicherMetadata() {
        var selector = new PiModelSelector(
            new PiInteractiveSession.ModelSelection(
                List.of(
                    new PiInteractiveSession.SelectableModel(1, "anthropic", "claude-3-7-sonnet", "Claude 3.7 Sonnet", "high", false, true, 200_000),
                    new PiInteractiveSession.SelectableModel(0, "openai", "gpt-5", "GPT-5", "minimal", true, true, 400_000)
                ),
                List.of()
            ),
            ignored -> {
            },
            () -> {
            },
            () -> {
            }
        );

        var lines = selector.render(100);

        assertThat(lines).anyMatch(line -> line.contains("Select model"));
        assertThat(lines).anyMatch(line -> line.contains("gpt-5 ✓"));
        assertThat(lines).anyMatch(line -> line.contains("[openai]"));
        assertThat(lines).anyMatch(line -> line.contains("GPT-5"));
        assertThat(lines).anyMatch(line -> line.contains("400k ctx"));
    }

    @Test
    void selectsUsingOriginalModelIndexAfterSorting() {
        var selectedIndex = new AtomicInteger(-1);
        var selector = new PiModelSelector(
            new PiInteractiveSession.ModelSelection(
                List.of(
                    new PiInteractiveSession.SelectableModel(1, "anthropic", "claude-3-7-sonnet", "Claude 3.7 Sonnet", "high", false, true, 200_000),
                    new PiInteractiveSession.SelectableModel(0, "openai", "gpt-5", "GPT-5", "minimal", true, true, 400_000)
                ),
                List.of()
            ),
            selectedIndex::set,
            () -> {
            },
            () -> {
            }
        );

        selector.handleInput("\r");

        assertThat(selectedIndex.get()).isZero();
    }

    @Test
    void togglesBetweenAllAndScopedScopes() {
        var selectedIndex = new AtomicInteger(-1);
        var selector = new PiModelSelector(
            new PiInteractiveSession.ModelSelection(
                List.of(
                    new PiInteractiveSession.SelectableModel(0, "openai", "gpt-5", "GPT-5", "minimal", true, true, 400_000),
                    new PiInteractiveSession.SelectableModel(1, "google", "gemini-2.5-pro", "Gemini 2.5 Pro", "high", false, true, 1_000_000)
                ),
                List.of(new PiInteractiveSession.SelectableModel(2, "anthropic", "claude-3-7-sonnet", "Claude 3.7 Sonnet", "high", false, true, 200_000))
            ),
            selectedIndex::set,
            () -> {
            },
            () -> {
            }
        );

        assertThat(selector.render(100)).anyMatch(line -> line.contains("Scoped"));
        assertThat(selector.render(100)).anyMatch(line -> line.contains("claude-3-7-sonnet"));

        selector.handleInput("\t");

        assertThat(selector.render(100)).anyMatch(line -> line.contains("gpt-5 ✓"));
        assertThat(selector.render(100)).anyMatch(line -> line.contains("gemini-2.5-pro"));

        selector.handleInput("\u001b[B");
        selector.handleInput("\r");

        assertThat(selectedIndex.get()).isEqualTo(1);
    }
}
