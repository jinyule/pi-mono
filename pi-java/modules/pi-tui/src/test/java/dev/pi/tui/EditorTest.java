package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EditorTest {
    @Test
    void supportsMultilineEditingAndSubmit() {
        var editor = new Editor();
        var submitted = new StringBuilder();
        editor.setOnSubmit(submitted::append);

        editor.handleInput("h");
        editor.handleInput("i");
        editor.handleInput("\u001b\r");
        editor.handleInput("x");
        editor.handleInput("\r");

        assertThat(editor.getText()).isEqualTo("hi\nx");
        assertThat(submitted).hasToString("hi\nx");
    }

    @Test
    void backspaceAtLineStartMergesWithPreviousLine() {
        var editor = new Editor();
        editor.setText("hello\nworld");
        editor.handleInput("\u001b[A");
        editor.handleInput("\u0005");
        editor.handleInput("\u001b[B");
        editor.handleInput("\u0001");
        editor.handleInput("\u007f");

        assertThat(editor.getText()).isEqualTo("helloworld");
    }

    @Test
    void rendersBordersAndCursorMarkerWhenFocused() {
        var editor = new Editor();
        editor.setText("abc");
        editor.setFocused(true);

        var lines = editor.render(10);

        assertThat(lines.get(0)).contains("─");
        assertThat(lines.get(1)).contains(Tui.CURSOR_MARKER);
        assertThat(lines.getLast()).contains("─");
    }
}
