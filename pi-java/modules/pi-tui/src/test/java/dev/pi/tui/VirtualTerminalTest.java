package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VirtualTerminalTest {
    @Test
    void rendersViewportFromTuiOutput() {
        var terminal = new VirtualTerminal(20, 6);
        var tui = new Tui(terminal);
        tui.addChild(new Text("Hello", 0, 0, null));

        tui.requestRender();

        assertThat(terminal.getViewport().getFirst()).isEqualTo("Hello");
    }

    @Test
    void forwardsInputToFocusedComponent() {
        var terminal = new VirtualTerminal(20, 6);
        var tui = new Tui(terminal);
        var input = new Input();
        tui.addChild(input);
        tui.setFocus(input);
        tui.start();

        terminal.sendInput("a");
        tui.requestRender();

        assertThat(terminal.getViewport()).anyMatch(line -> line.contains("> a"));
    }

    @Test
    void resizeTriggersRerender() {
        var terminal = new VirtualTerminal(12, 6);
        var tui = new Tui(terminal);
        tui.addChild(new Text("Hello world", 0, 0, null));
        tui.start();

        assertThat(terminal.getViewport().getFirst()).isEqualTo("Hello world");

        terminal.resize(5, 6);

        assertThat(terminal.getViewport()).contains("Hello", "world");
    }
}
