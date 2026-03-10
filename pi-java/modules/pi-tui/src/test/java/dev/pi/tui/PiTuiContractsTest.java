package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PiTuiContractsTest {
    @Test
    void componentAndFocusableContractsCanBeImplementedTogether() {
        var component = new FakeInput();

        component.setFocused(true);

        assertThat(component.isFocused()).isTrue();
        assertThat(component.render(40)).containsExactly("input");
    }

    @Test
    void terminalContractExposesDimensionsAndOutputHooks() {
        var terminal = new FakeTerminal();
        terminal.start(data -> {
        }, () -> {
        });
        terminal.write("hello");
        terminal.setTitle("Pi");
        terminal.hideCursor();
        terminal.showCursor();
        terminal.stop();

        assertThat(terminal.columns()).isEqualTo(80);
        assertThat(terminal.rows()).isEqualTo(24);
        assertThat(terminal.written()).containsExactly("hello");
        assertThat(terminal.started()).isTrue();
        assertThat(terminal.stopped()).isTrue();
    }

    @Test
    void overlayOptionsDefaultToCenteredZeroMargin() {
        var options = OverlayOptions.defaults();
        var overlay = new FakeOverlay(new FakeInput(), options);

        overlay.setHidden(true);

        assertThat(options.anchor()).isEqualTo(OverlayAnchor.CENTER);
        assertThat(options.margin()).isEqualTo(OverlayMargin.uniform(0));
        assertThat(overlay.isHidden()).isTrue();
    }

    private static final class FakeInput implements Component, Focusable {
        private boolean focused;

        @Override
        public List<String> render(int width) {
            return List.of("input");
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
        private boolean started;
        private boolean stopped;
        private final java.util.ArrayList<String> written = new java.util.ArrayList<>();

        @Override
        public void start(InputHandler onInput, Runnable onResize) {
            this.started = true;
        }

        @Override
        public void stop() {
            this.stopped = true;
        }

        @Override
        public void write(String data) {
            written.add(data);
        }

        @Override
        public int columns() {
            return 80;
        }

        @Override
        public int rows() {
            return 24;
        }

        boolean started() {
            return started;
        }

        boolean stopped() {
            return stopped;
        }

        List<String> written() {
            return List.copyOf(written);
        }
    }

    private static final class FakeOverlay implements Overlay {
        private final Component component;
        private final OverlayOptions options;
        private boolean hidden;

        private FakeOverlay(Component component, OverlayOptions options) {
            this.component = component;
            this.options = options;
        }

        @Override
        public Component component() {
            return component;
        }

        @Override
        public OverlayOptions options() {
            return options;
        }

        @Override
        public boolean isHidden() {
            return hidden;
        }

        @Override
        public void setHidden(boolean hidden) {
            this.hidden = hidden;
        }
    }
}
