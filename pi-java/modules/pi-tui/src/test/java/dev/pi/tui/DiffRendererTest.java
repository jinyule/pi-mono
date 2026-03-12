package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiffRendererTest {
    @Test
    void firstRenderWritesFullFrameInsideSynchronizedOutput() {
        var terminal = new FakeTerminal(80, 24);
        var renderer = new DiffRenderer(terminal);

        renderer.render(List.of("alpha", "beta"));

        assertThat(terminal.writes()).containsExactly(SynchronizedOutput.wrap("alpha\r\nbeta"));
        assertThat(renderer.fullRedrawCount()).isEqualTo(1);
        assertThat(renderer.previousLines()).containsExactly("alpha", "beta");
    }

    @Test
    void widthChangeTriggersClearingFullRedraw() {
        var terminal = new FakeTerminal(80, 24);
        var renderer = new DiffRenderer(terminal);

        renderer.render(List.of("alpha"));
        terminal.setColumns(100);
        renderer.render(List.of("alpha"));

        assertThat(terminal.writes().get(1)).isEqualTo(SynchronizedOutput.wrap("\u001b[3J\u001b[2J\u001b[Halpha"));
        assertThat(renderer.fullRedrawCount()).isEqualTo(2);
    }

    @Test
    void appendingLinesRendersOnlyTailRegion() {
        var terminal = new FakeTerminal(80, 24);
        var renderer = new DiffRenderer(terminal);

        renderer.render(List.of("alpha", "beta"));
        renderer.render(List.of("alpha", "beta", "gamma", "delta"));

        assertThat(terminal.writes().get(1)).isEqualTo(SynchronizedOutput.wrap("\r\n\u001b[2Kgamma\r\n\u001b[2Kdelta"));
    }

    @Test
    void shrinkTriggersClearingFullRedrawWhenEnabled() {
        var terminal = new FakeTerminal(80, 24);
        var renderer = new DiffRenderer(terminal);

        renderer.render(List.of("alpha", "beta", "gamma"));
        renderer.render(List.of("alpha"));

        assertThat(terminal.writes().get(1)).isEqualTo(SynchronizedOutput.wrap("\u001b[3J\u001b[2J\u001b[Halpha"));
        assertThat(renderer.fullRedrawCount()).isEqualTo(2);
    }

    @Test
    void shrinkDoesNotClearWhenDisabled() {
        var terminal = new FakeTerminal(80, 24);
        var renderer = new DiffRenderer(terminal);
        renderer.setClearOnShrink(false);

        renderer.render(List.of("alpha", "beta", "gamma"));
        renderer.render(List.of("alpha"));

        assertThat(terminal.writes()).hasSize(1);
        assertThat(renderer.fullRedrawCount()).isEqualTo(1);
    }

    @Test
    void changeAboveViewportFallsBackToFullRedraw() {
        var terminal = new FakeTerminal(80, 2);
        var renderer = new DiffRenderer(terminal);

        renderer.render(List.of("one", "two", "three"));
        renderer.render(List.of("ONE", "two", "three"));

        assertThat(terminal.writes().get(1)).isEqualTo(SynchronizedOutput.wrap("\u001b[3J\u001b[2J\u001b[HONE\r\ntwo\r\nthree"));
        assertThat(renderer.fullRedrawCount()).isEqualTo(2);
    }

    private static final class FakeTerminal implements Terminal {
        private final List<String> writes = new ArrayList<>();
        private int columns;
        private final int rows;

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

        private void setColumns(int columns) {
            this.columns = columns;
        }

        private List<String> writes() {
            return List.copyOf(writes);
        }
    }
}
