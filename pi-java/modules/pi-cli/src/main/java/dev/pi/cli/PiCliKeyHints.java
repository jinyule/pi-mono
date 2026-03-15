package dev.pi.cli;

import dev.pi.tui.EditorAction;
import dev.pi.tui.EditorKeybindings;

final class PiCliKeyHints {
    private PiCliKeyHints() {
    }

    static String editorKey(EditorAction action) {
        var keys = EditorKeybindings.global().getKeys(action);
        return keys.isEmpty() ? action.name() : keys.getFirst();
    }

    static String editorHint(EditorAction action, String description) {
        return PiCliAnsi.dim(editorKey(action)) + PiCliAnsi.muted(" " + description);
    }

    static String appKey(PiAppAction action) {
        var keys = PiAppKeybindings.global().getKeys(action);
        return keys.isEmpty() ? action.name() : keys.getFirst();
    }

    static String appHint(PiAppAction action, String description) {
        return PiCliAnsi.dim(appKey(action)) + PiCliAnsi.muted(" " + description);
    }

    static String rawHint(String key, String description) {
        return PiCliAnsi.dim(key) + PiCliAnsi.muted(" " + description);
    }
}
