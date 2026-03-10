package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoaderTest {
    @Test
    void rendersSpacerLineBeforeSpinnerText() {
        var terminal = new FakeTerminal();
        var tui = new Tui(terminal);
        var loader = new Loader(tui, LoaderTest::cyan, LoaderTest::dim, "Loading...", 80, false);

        var lines = loader.render(20);

        assertThat(lines.getFirst()).isEmpty();
        assertThat(stripAnsi(lines.get(1))).contains("⠋ Loading...");
    }

    @Test
    void advancingFrameUpdatesSpinnerAndRequestsRender() {
        var terminal = new FakeTerminal();
        var tui = new Tui(terminal);
        var loader = new Loader(tui, LoaderTest::cyan, LoaderTest::dim, "Working", 80, false);
        tui.addChild(loader);

        var before = stripAnsi(loader.render(20).get(1));
        var writesBefore = terminal.writes().size();

        loader.advanceFrame();

        var after = stripAnsi(loader.render(20).get(1));
        assertThat(before).contains("⠋ Working");
        assertThat(after).contains("⠙ Working");
        assertThat(terminal.writes().size()).isGreaterThan(writesBefore);
    }

    @Test
    void setMessageUpdatesRenderedText() {
        var terminal = new FakeTerminal();
        var tui = new Tui(terminal);
        var loader = new Loader(tui, LoaderTest::cyan, LoaderTest::dim, "Loading...", 80, false);

        loader.setMessage("Still loading");

        assertThat(stripAnsi(loader.render(24).get(1))).contains("Still loading");
    }

    private static String cyan(String text) {
        return "\u001b[36m" + text + "\u001b[0m";
    }

    private static String dim(String text) {
        return "\u001b[90m" + text + "\u001b[0m";
    }

    private static String stripAnsi(String text) {
        return text.replaceAll("\\u001B\\[[;\\d]*m", "");
    }

    private static final class FakeTerminal implements Terminal {
        private final List<String> writes = new ArrayList<>();

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
            return 40;
        }

        @Override
        public int rows() {
            return 10;
        }

        private List<String> writes() {
            return List.copyOf(writes);
        }
    }
}
