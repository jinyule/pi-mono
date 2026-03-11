package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InputTest {
    @Test
    void typesTextAndSubmitsValue() {
        var input = new Input();
        var submitted = new StringBuilder();
        input.setOnSubmit(submitted::append);

        input.handleInput("h");
        input.handleInput("i");
        input.handleInput("\r");

        assertThat(input.getValue()).isEqualTo("hi");
        assertThat(submitted).hasToString("hi");
    }

    @Test
    void supportsCursorMovementAndDeletion() {
        var input = new Input();
        input.setValue("hello world");

        input.handleInput("\u0005");
        input.handleInput("\u001b[1;5D");
        input.handleInput("\u0017");

        assertThat(input.getValue()).isEqualTo("world");
    }

    @Test
    void buffersBracketedPasteUntilEndMarker() {
        var input = new Input();

        input.handleInput("\u001b[200~hello");
        input.handleInput(" world\u001b[201~");

        assertThat(input.getValue()).isEqualTo("hello world");
    }

    @Test
    void rendersCursorMarkerWhenFocused() {
        var input = new Input();
        input.setValue("abc");
        input.setFocused(true);

        var line = input.render(12).get(0);

        assertThat(line).contains(Tui.CURSOR_MARKER);
        assertThat(line).contains("\u001b[7m");
    }

    @Test
    void escapeTriggersCallback() {
        var input = new Input();
        var escapeCount = new AtomicInteger();
        input.setOnEscape(escapeCount::incrementAndGet);

        input.handleInput("\u001b");

        assertThat(escapeCount.get()).isEqualTo(1);
    }

    @Test
    void ctrlDOnEmptyInputTriggersExitCallback() {
        var input = new Input();
        var exitCount = new AtomicInteger();
        input.setOnExit(exitCount::incrementAndGet);

        input.handleInput("\u0004");

        assertThat(exitCount.get()).isEqualTo(1);
    }
}
