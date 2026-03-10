package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BasicComponentsTest {
    @Test
    void containerConcatenatesChildrenAndPropagatesInvalidate() {
        var childA = new FakeComponent(List.of("a"));
        var childB = new FakeComponent(List.of("b", "c"));
        var container = new Container();

        container.addChild(childA);
        container.addChild(childB);
        container.invalidate();

        assertThat(container.render(20)).containsExactly("a", "b", "c");
        assertThat(childA.invalidated()).isTrue();
        assertThat(childB.invalidated()).isTrue();
    }

    @Test
    void textWrapsContentWithPadding() {
        var text = new Text("Hello world", 1, 1, null);

        var lines = text.render(8);

        assertThat(lines).containsExactly(
            "        ",
            " Hello  ",
            " world  ",
            "        "
        );
    }

    @Test
    void truncatedTextUsesOnlyFirstLineAndAppliesEllipsis() {
        var text = new TruncatedText("Hello World\nignored");

        var lines = text.render(8);

        assertThat(lines).containsExactly("Hello...");
    }

    private static final class FakeComponent implements Component {
        private final List<String> lines;
        private boolean invalidated;

        private FakeComponent(List<String> lines) {
            this.lines = lines;
        }

        @Override
        public List<String> render(int width) {
            return lines;
        }

        @Override
        public void invalidate() {
            invalidated = true;
        }

        private boolean invalidated() {
            return invalidated;
        }
    }
}
