package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TuiTest {
    @Test
    void cursorMarkerPositionsHardwareCursorForFocusedComponent() {
        var terminal = new FakeTerminal(20, 6);
        var tui = new Tui(terminal, true);
        var input = new FakeInput("> " + Tui.CURSOR_MARKER + "x");

        tui.addChild(input);
        tui.setFocus(input);
        tui.requestRender();

        assertThat(terminal.writes().get(0)).contains("> x");
        assertThat(terminal.writes().get(1)).isEqualTo("\u001b[3G");
        assertThat(terminal.showCursorCalls()).isEqualTo(1);
        assertThat(input.isFocused()).isTrue();
    }

    @Test
    void showOverlayFocusesTopmostOverlayAndSupportsHideLifecycle() {
        var terminal = new FakeTerminal(20, 6);
        var tui = new Tui(terminal);
        var base = new FakeInput("base");
        var overlay = new FakeInput("overlay");

        tui.addChild(base);
        tui.setFocus(base);

        var handle = tui.showOverlay(overlay, new OverlayOptions(7, null, null, OverlayAnchor.CENTER, 0, 0, null, null, OverlayMargin.uniform(0)));

        assertThat(tui.hasOverlay()).isTrue();
        assertThat(base.isFocused()).isFalse();
        assertThat(overlay.isFocused()).isTrue();
        assertThat(terminal.writes().get(0)).contains("overlay");

        handle.setHidden(true);
        assertThat(handle.isHidden()).isTrue();
        assertThat(tui.hasOverlay()).isFalse();
        assertThat(base.isFocused()).isTrue();

        handle.setHidden(false);
        assertThat(handle.isHidden()).isFalse();
        assertThat(tui.hasOverlay()).isTrue();
        assertThat(overlay.isFocused()).isTrue();

        handle.hide();
        assertThat(tui.hasOverlay()).isFalse();
        assertThat(base.isFocused()).isTrue();
    }

    private static final class FakeInput implements Component, Focusable {
        private final String line;
        private boolean focused;

        private FakeInput(String line) {
            this.line = line;
        }

        @Override
        public List<String> render(int width) {
            return List.of(line);
        }

        @Override
        public boolean isFocused() {
            return focused;
        }

        @Override
        public void setFocused(boolean focused) {
            this.focused = focused;
        }
    }

    private static final class FakeTerminal implements Terminal {
        private final List<String> writes = new ArrayList<>();
        private final int columns;
        private final int rows;
        private int showCursorCalls;

        private FakeTerminal(int columns, int rows) {
            this.columns = columns;
            this.rows = rows;
        }

        @Override
        public void start(InputHandler onInput, Runnable onResize) {
        }

        @Override
        public void stop() {
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

        @Override
        public void showCursor() {
            showCursorCalls += 1;
        }

        List<String> writes() {
            return List.copyOf(writes);
        }

        int showCursorCalls() {
            return showCursorCalls;
        }
    }
}
