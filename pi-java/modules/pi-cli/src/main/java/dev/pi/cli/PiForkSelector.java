package dev.pi.cli;

import dev.pi.tui.Component;
import dev.pi.tui.EditorAction;
import dev.pi.tui.EditorKeybindings;
import dev.pi.tui.Focusable;
import dev.pi.tui.Input;
import dev.pi.tui.SelectItem;
import dev.pi.tui.SelectList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class PiForkSelector implements Component, Focusable {
    private static final String VALUE_DELIMITER = "\u0000";

    private final Input search = new Input();
    private final SelectList messages;
    private final Runnable requestRender;
    private boolean focused;

    public PiForkSelector(
        List<PiInteractiveSession.ForkMessage> messages,
        Consumer<String> onSelect,
        Runnable onCancel,
        Runnable requestRender
    ) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(onSelect, "onSelect");
        Objects.requireNonNull(onCancel, "onCancel");
        this.requestRender = Objects.requireNonNull(requestRender, "requestRender");

        var items = messages.stream()
            .map(PiForkSelector::toSelectItem)
            .toList();
        this.messages = new SelectList(items, Math.max(6, Math.min(12, Math.max(1, items.size()))), PiSessionPicker.sessionTheme());
        this.search.setFocused(true);
        this.messages.setOnSelectionChange(ignored -> requestRender.run());
        this.messages.setOnSelect(item -> onSelect.accept(decodeId(item.value())));
        this.messages.setOnCancel(onCancel);
        if (!items.isEmpty()) {
            this.messages.setSelectedIndex(items.size() - 1);
        }
    }

    @Override
    public List<String> render(int width) {
        var lines = new ArrayList<String>();
        lines.add(PiCliAnsi.bold("Fork from previous message"));
        lines.add(
            PiCliAnsi.muted("Type to filter. ")
                + PiCliKeyHints.editorHint(EditorAction.SUBMIT, "forks.")
                + PiCliAnsi.muted(" ")
                + PiCliKeyHints.editorHint(EditorAction.SELECT_CANCEL, "cancels.")
        );
        lines.add("");
        lines.addAll(search.render(width));
        lines.add("");
        lines.addAll(messages.render(width));
        return List.copyOf(lines);
    }

    @Override
    public void handleInput(String data) {
        var keybindings = EditorKeybindings.global();
        if (
            keybindings.matches(data, EditorAction.CURSOR_UP) ||
            keybindings.matches(data, EditorAction.CURSOR_DOWN) ||
            keybindings.matches(data, EditorAction.SUBMIT) ||
            keybindings.matches(data, EditorAction.SELECT_CANCEL)
        ) {
            messages.handleInput(data);
            return;
        }

        search.handleInput(data);
        messages.setFilter(search.getValue());
        requestRender.run();
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        search.setFocused(focused);
    }

    private static SelectItem toSelectItem(PiInteractiveSession.ForkMessage message) {
        var text = normalize(message.text());
        return new SelectItem(encodeValue(text, message.entryId()), text, null);
    }

    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "(empty)";
        }
        return text.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static String encodeValue(String text, String entryId) {
        return text + VALUE_DELIMITER + entryId;
    }

    private static String decodeId(String value) {
        var delimiter = value.lastIndexOf(VALUE_DELIMITER);
        return delimiter >= 0 ? value.substring(delimiter + VALUE_DELIMITER.length()) : value;
    }
}
