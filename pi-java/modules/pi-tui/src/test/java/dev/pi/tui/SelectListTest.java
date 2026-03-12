package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SelectListTest {
    private static final SelectListTheme THEME = new SelectListTheme() {
        @Override
        public String selectedPrefix(String text) {
            return text;
        }

        @Override
        public String selectedText(String text) {
            return text;
        }

        @Override
        public String description(String text) {
            return text;
        }

        @Override
        public String scrollInfo(String text) {
            return text;
        }

        @Override
        public String noMatch(String text) {
            return text;
        }
    };

    @Test
    void normalizesMultilineDescriptionsToSingleLine() {
        var list = new SelectList(
            List.of(new SelectItem("test", "test", "Line one\nLine two\nLine three")),
            5,
            THEME
        );

        var lines = list.render(100);

        assertThat(lines).isNotEmpty();
        assertThat(lines.getFirst()).doesNotContain("\n");
        assertThat(lines.getFirst()).contains("Line one Line two Line three");
    }

    @Test
    void wrapsSelectionAndInvokesCallbacks() {
        var list = new SelectList(
            List.of(
                new SelectItem("one", "One", null),
                new SelectItem("two", "Two", null)
            ),
            5,
            THEME
        );
        var selected = new AtomicReference<SelectItem>();
        var changed = new AtomicReference<SelectItem>();
        var cancelled = new AtomicReference<Boolean>(false);
        list.setOnSelect(selected::set);
        list.setOnSelectionChange(changed::set);
        list.setOnCancel(() -> cancelled.set(true));

        list.handleInput("\u001b[A");
        list.handleInput("\r");
        list.handleInput("\u001b");

        assertThat(changed.get()).isEqualTo(new SelectItem("two", "Two", null));
        assertThat(selected.get()).isEqualTo(new SelectItem("two", "Two", null));
        assertThat(cancelled.get()).isTrue();
    }

    @Test
    void rendersNoMatchAndScrollInfo() {
        var list = new SelectList(
            List.of(
                new SelectItem("alpha", "Alpha", null),
                new SelectItem("beta", "Beta", null),
                new SelectItem("gamma", "Gamma", null)
            ),
            1,
            THEME
        );

        list.setFilter("zzz");
        assertThat(list.render(40)).containsExactly("  No matching commands");

        list.setFilter("");
        list.setSelectedIndex(1);

        var lines = list.render(40);

        assertThat(lines).anyMatch(line -> line.contains("(2/3)"));
    }

    @Test
    void filtersByTokenContainsInsteadOfPrefixOnly() {
        var list = new SelectList(
            List.of(
                new SelectItem("alpha workspace first", "Alpha", null),
                new SelectItem("beta workspace second", "Beta", null)
            ),
            5,
            THEME
        );

        list.setFilter("workspace second");

        assertThat(list.getSelectedItem()).isEqualTo(new SelectItem("beta workspace second", "Beta", null));
        assertThat(list.render(40)).anyMatch(line -> line.contains("Beta"));
        assertThat(list.render(40)).noneMatch(line -> line.contains("Alpha"));
    }

    @Test
    void keepsDescriptionsVisibleAtMediumWidths() {
        var list = new SelectList(
            List.of(new SelectItem("alpha", "Alpha task", "2 msg · 1m")),
            5,
            THEME
        );

        var line = list.render(36).getFirst();

        assertThat(TerminalText.visibleWidth(line)).isLessThanOrEqualTo(36);
        assertThat(line)
            .contains("Alpha task")
            .contains("2 msg");
    }

    @Test
    void rightAlignsDescriptionsAtWideWidths() {
        var list = new SelectList(
            List.of(new SelectItem("alpha", "Alpha task", "2 msg · 1m")),
            5,
            THEME
        );

        var line = list.render(48).getFirst();

        assertThat(TerminalText.visibleWidth(line)).isLessThanOrEqualTo(48);
        assertThat(line).endsWith("2 msg · 1m");
        assertThat(line).containsPattern("Alpha task\\s{2,}2 msg · 1m$");
    }

    @Test
    void expandsDescriptionWhenLabelIsShort() {
        var list = new SelectList(
            List.of(new SelectItem("alpha", "Alpha", "/workspace/project/path · 2 msg · 1m")),
            5,
            THEME
        );

        var line = list.render(48).getFirst();

        assertThat(TerminalText.visibleWidth(line)).isLessThanOrEqualTo(48);
        assertThat(line).contains("/workspace/project/path · 2 msg · 1m");
    }

    @Test
    void keepsRightSideMetadataVisibleWhenLabelTruncates() {
        var list = new SelectList(
            List.of(new SelectItem("alpha", "A very long session label that should truncate", "2 msg · 1m")),
            5,
            THEME
        );

        var line = list.render(32).getFirst();

        assertThat(TerminalText.visibleWidth(line)).isLessThanOrEqualTo(32);
        assertThat(line).endsWith("2 msg · 1m");
        assertThat(line).doesNotContain("A very long session label that should truncate");
    }

    @Test
    void stylesSelectedDescriptionIndependently() {
        var theme = new SelectListTheme() {
            @Override
            public String selectedPrefix(String text) {
                return text;
            }

            @Override
            public String selectedText(String text) {
                return "<sel>" + text + "</sel>";
            }

            @Override
            public String selectedDescription(String text) {
                return "<desc>" + text + "</desc>";
            }

            @Override
            public String description(String text) {
                return text;
            }

            @Override
            public String scrollInfo(String text) {
                return text;
            }

            @Override
            public String noMatch(String text) {
                return text;
            }
        };
        var list = new SelectList(
            List.of(new SelectItem("alpha", "Alpha task", "[openai]")),
            5,
            theme
        );

        var line = list.render(48).getFirst();

        assertThat(line).contains("<sel>Alpha task</sel>");
        assertThat(line).contains("<desc>");
        assertThat(line).endsWith("</desc>");
    }
}
