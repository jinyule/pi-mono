package dev.pi.cli;

import dev.pi.tui.Component;
import dev.pi.tui.Focusable;
import dev.pi.tui.Input;
import dev.pi.tui.TerminalText;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class PiSecretPrompt implements Component, Focusable {
    private final String title;
    private final String description;
    private final Input input = new Input();
    private boolean focused;

    public PiSecretPrompt(
        String title,
        String description,
        Consumer<String> onSubmit,
        Runnable onCancel
    ) {
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
        Objects.requireNonNull(onSubmit, "onSubmit");
        Objects.requireNonNull(onCancel, "onCancel");
        input.setMasked(true);
        input.setOnSubmit(onSubmit::accept);
        input.setOnEscape(onCancel);
        input.setFocused(true);
    }

    @Override
    public List<String> render(int width) {
        var lines = new ArrayList<String>();
        var safeWidth = Math.max(1, width);
        lines.add(PiCliAnsi.borderMuted("\u2500".repeat(safeWidth)));
        lines.add(PiCliAnsi.accentBold(title));
        lines.add("");
        for (var line : TerminalText.wrapText(description, safeWidth)) {
            lines.add(PiCliAnsi.muted(line));
        }
        lines.add("");
        lines.addAll(input.render(safeWidth));
        lines.add("");
        lines.add(
            PiCliAnsi.muted("  ")
                + PiCliKeyHints.editorHint(dev.pi.tui.EditorAction.SUBMIT, "to save")
                + PiCliAnsi.muted(" \u00b7 ")
                + PiCliKeyHints.editorHint(dev.pi.tui.EditorAction.SELECT_CANCEL, "to cancel")
        );
        lines.add(PiCliAnsi.borderMuted("\u2500".repeat(safeWidth)));
        return List.copyOf(lines);
    }

    @Override
    public void handleInput(String data) {
        input.handleInput(data);
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        input.setFocused(focused);
    }
}
