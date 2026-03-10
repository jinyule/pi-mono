package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TuiGoldenTest {
    private static final SelectListTheme SELECT_THEME = new SelectListTheme() {
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
    void rendersStableViewportForStaticContent() {
        var terminal = new VirtualTerminal(16, 6);
        var tui = new Tui(terminal);
        tui.addChild(new Text("Hello", 0, 0, null));

        tui.requestRender();

        assertThat(terminal.getViewport()).containsExactly(
            "Hello",
            "",
            "",
            "",
            "",
            ""
        );
    }

    @Test
    void updatesViewportAfterSelectionKeyInput() {
        var terminal = new VirtualTerminal(16, 6);
        var tui = new Tui(terminal);
        var list = new SelectList(
            List.of(
                new SelectItem("one", "One", null),
                new SelectItem("two", "Two", null)
            ),
            5,
            SELECT_THEME
        );
        tui.addChild(list);
        tui.setFocus(list);
        tui.start();

        assertThat(terminal.getViewport()).contains("→ One", "  Two");

        terminal.sendInput("\u001b[B");
        tui.requestRender();

        assertThat(terminal.getViewport()).contains("  One", "→ Two");
    }
}
