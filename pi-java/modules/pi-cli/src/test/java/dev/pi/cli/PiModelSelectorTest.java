package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.tui.EditorAction;
import dev.pi.tui.EditorKeybindings;
import dev.pi.tui.TerminalText;
import java.util.List;
import java.util.Map;
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

        assertThat(lines.getFirst()).contains("\u2500\u2500\u2500\u2500");
        assertThat(lines).noneMatch(line -> line.contains("Select model"));
        assertThat(lines).anyMatch(line -> line.contains("Only showing models with configured API keys"));
        assertThat(lines).anyMatch(line -> line.contains("gpt-5"));
        assertThat(lines).anyMatch(line -> line.contains("\u001b[1;36mgpt-5"));
        assertThat(lines).anyMatch(line -> line.contains("\u001b[90m") && line.contains("[openai]"));
        assertThat(lines).anyMatch(line -> line.contains("\u001b[1mSelected:"));
        assertThat(lines).anyMatch(line -> line.contains("\u001b[90mopenai/"));
        assertThat(lines).anyMatch(line -> line.contains("\u001b[1mgpt-5"));
        assertThat(lines).anyMatch(line -> line.contains("\u001b[1mModel Name:"));
        assertThat(lines).anyMatch(line -> line.contains("\u001b[90mGPT-5"));
        assertThat(lines).anyMatch(line -> line.contains("\u001b[1mThinking:"));
        assertThat(lines).anyMatch(line -> line.contains("\u001b[90mminimal"));
        assertThat(lines).anyMatch(line -> line.contains("\u001b[1mContext:"));
        assertThat(lines).anyMatch(line -> line.contains("\u001b[90m400k ctx"));
        assertThat(lines).anyMatch(line -> line.contains("\u001b[32m\u2713"));
        assertThat(lines).anyMatch(line -> line.contains("\u2500\u2500\u2500\u2500"));
        assertThat(lines.getLast()).contains("\u2500\u2500\u2500\u2500");
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

        assertThat(selector.render(100)).anyMatch(line -> line.contains("Scope:"));
        assertThat(selector.render(100)).anyMatch(line -> line.contains("tab") && line.contains("scope (all/scoped)"));
        assertThat(selector.render(100)).anyMatch(line -> line.contains("scoped"));
        assertThat(selector.render(100)).anyMatch(line -> line.contains("claude-3-7-sonnet"));

        selector.handleInput("\t");

        assertThat(selector.render(100)).anyMatch(line -> line.contains("gpt-5"));
        assertThat(selector.render(100)).anyMatch(line -> line.contains("gemini-2.5-pro"));

        selector.handleInput("\u001b[B");
        selector.handleInput("\r");

        assertThat(selectedIndex.get()).isEqualTo(1);
    }

    @Test
    void rendersConfiguredKeybindingHints() {
        var previous = EditorKeybindings.global();
        try {
            EditorKeybindings.setGlobal(new EditorKeybindings(Map.of(
                EditorAction.SESSION_SCOPE_TOGGLE, List.of("shift+tab"),
                EditorAction.SUBMIT, List.of("ctrl+j"),
                EditorAction.SELECT_CANCEL, List.of("ctrl+x")
            )));
            var selector = new PiModelSelector(
                new PiInteractiveSession.ModelSelection(
                    List.of(new PiInteractiveSession.SelectableModel(0, "openai", "gpt-5", "GPT-5", "minimal", true, true, 400_000)),
                    List.of(new PiInteractiveSession.SelectableModel(1, "anthropic", "claude-3-7-sonnet", "Claude 3.7 Sonnet", "high", false, true, 200_000))
                ),
                ignored -> {
                },
                () -> {
                },
                () -> {
                }
            );

            var lines = selector.render(100);

            assertThat(lines).anyMatch(line -> line.contains("shift+tab") && line.contains("scope (all/scoped)"));
            assertThat(lines).anyMatch(line -> line.contains("\u001b[2;37mshift+tab"));
            assertThat(lines).anyMatch(line -> line.contains("\u001b[90m scope (all/scoped)"));
            assertThat(lines).noneMatch(line -> line.contains("selects"));
            assertThat(lines).noneMatch(line -> line.contains("cancels"));
        } finally {
            EditorKeybindings.setGlobal(previous);
        }
    }

    @Test
    void wrapsWarningAndHintsAtNarrowWidths() {
        var selector = new PiModelSelector(
            new PiInteractiveSession.ModelSelection(
                List.of(new PiInteractiveSession.SelectableModel(0, "openai", "gpt-5", "GPT-5", "minimal", true, true, 400_000)),
                List.of()
            ),
            ignored -> {
            },
            () -> {
            },
            () -> {
            }
        );

        var lines = selector.render(28);

        assertThat(lines).anyMatch(line -> line.contains("configured"));
        assertThat(lines).anyMatch(line -> line.contains("README"));
        assertThat(lines).noneMatch(line -> line.contains("selects"));
        assertThat(lines.stream().mapToInt(TerminalText::visibleWidth)).allMatch(width -> width <= 28);
    }

    @Test
    void filterAndSortModelsMatchesProviderAndModelName() {
        var models = PiModelSelector.filterAndSortModels(
            List.of(
                new PiInteractiveSession.SelectableModel(0, "openai", "gpt-5", "GPT-5", "minimal", true, true, 400_000),
                new PiInteractiveSession.SelectableModel(1, "anthropic", "claude-3-7-sonnet", "Claude 3.7 Sonnet", "high", false, true, 200_000),
                new PiInteractiveSession.SelectableModel(2, "google", "gemini-2.5-pro", "Gemini 2.5 Pro", "high", false, true, 1_000_000)
            ),
            "claude"
        );

        assertThat(models)
            .extracting(PiInteractiveSession.SelectableModel::modelId)
            .containsExactly("claude-3-7-sonnet");

        var providerMatches = PiModelSelector.filterAndSortModels(
            List.of(
                new PiInteractiveSession.SelectableModel(0, "openai", "gpt-5", "GPT-5", "minimal", true, true, 400_000),
                new PiInteractiveSession.SelectableModel(1, "anthropic", "claude-3-7-sonnet", "Claude 3.7 Sonnet", "high", false, true, 200_000)
            ),
            "open"
        );

        assertThat(providerMatches)
            .extracting(PiInteractiveSession.SelectableModel::modelId)
            .containsExactly("gpt-5");
    }

    @Test
    void filterAndSortModelsPrefersExactAndPrefixMatches() {
        var matches = PiModelSelector.filterAndSortModels(
            List.of(
                new PiInteractiveSession.SelectableModel(0, "openai", "gpt-5", "GPT-5", "minimal", true, true, 400_000),
                new PiInteractiveSession.SelectableModel(1, "openai", "gpt-5-mini", "GPT-5 Mini", "minimal", false, true, 200_000),
                new PiInteractiveSession.SelectableModel(2, "google", "gemini-2.5-pro", "Gemini 2.5 Pro", "high", false, true, 1_000_000)
            ),
            "gpt-5"
        );

        assertThat(matches)
            .extracting(PiInteractiveSession.SelectableModel::modelId)
            .containsExactly("gpt-5", "gpt-5-mini");
    }

    @Test
    void rendersModelSpecificNoMatchCopy() {
        var selector = new PiModelSelector(
            new PiInteractiveSession.ModelSelection(
                List.of(new PiInteractiveSession.SelectableModel(0, "openai", "gpt-5", "GPT-5", "minimal", true, true, 400_000)),
                List.of()
            ),
            ignored -> {
            },
            () -> {
            },
            () -> {
            }
        );

        selector.handleInput("z");

        assertThat(selector.render(100)).anyMatch(line -> line.contains("No matching models"));
    }

    @Test
    void updatesSelectedDetailWhenSelectionChanges() {
        var selector = new PiModelSelector(
            new PiInteractiveSession.ModelSelection(
                List.of(
                    new PiInteractiveSession.SelectableModel(0, "openai", "gpt-5", "GPT-5", "minimal", true, true, 400_000),
                    new PiInteractiveSession.SelectableModel(1, "anthropic", "claude-3-7-sonnet", "Claude 3.7 Sonnet", "high", false, true, 200_000)
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

        selector.handleInput("\u001b[B");

        var lines = selector.render(100);
        assertThat(lines).anyMatch(line -> line.contains("\u001b[1mSelected:"));
        assertThat(lines).anyMatch(line -> line.contains("\u001b[90manthropic/"));
        assertThat(lines).anyMatch(line -> line.contains("\u001b[1mclaude-3-7-sonnet"));
        assertThat(lines).anyMatch(line -> line.contains("\u001b[1mModel Name:"));
        assertThat(lines).anyMatch(line -> line.contains("\u001b[90mClaude 3.7 Sonnet"));
    }
}
