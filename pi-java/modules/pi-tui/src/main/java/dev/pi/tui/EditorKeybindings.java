package dev.pi.tui;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EditorKeybindings {
    public static final Map<EditorAction, List<String>> DEFAULT_EDITOR_KEYBINDINGS = Map.ofEntries(
        Map.entry(EditorAction.CURSOR_UP, List.of("up")),
        Map.entry(EditorAction.CURSOR_DOWN, List.of("down")),
        Map.entry(EditorAction.CURSOR_LEFT, List.of("left", "ctrl+b")),
        Map.entry(EditorAction.CURSOR_RIGHT, List.of("right", "ctrl+f")),
        Map.entry(EditorAction.CURSOR_WORD_LEFT, List.of("alt+left", "ctrl+left", "alt+b")),
        Map.entry(EditorAction.CURSOR_WORD_RIGHT, List.of("alt+right", "ctrl+right", "alt+f")),
        Map.entry(EditorAction.CURSOR_LINE_START, List.of("home", "ctrl+a")),
        Map.entry(EditorAction.CURSOR_LINE_END, List.of("end", "ctrl+e")),
        Map.entry(EditorAction.DELETE_CHAR_BACKWARD, List.of("backspace")),
        Map.entry(EditorAction.DELETE_CHAR_FORWARD, List.of("delete", "ctrl+d")),
        Map.entry(EditorAction.DELETE_WORD_BACKWARD, List.of("ctrl+w", "alt+backspace")),
        Map.entry(EditorAction.DELETE_WORD_FORWARD, List.of("alt+d", "alt+delete")),
        Map.entry(EditorAction.DELETE_TO_LINE_START, List.of("ctrl+u")),
        Map.entry(EditorAction.DELETE_TO_LINE_END, List.of("ctrl+k")),
        Map.entry(EditorAction.NEW_LINE, List.of("alt+enter")),
        Map.entry(EditorAction.SUBMIT, List.of("enter")),
        Map.entry(EditorAction.EXIT, List.of("ctrl+d")),
        Map.entry(EditorAction.SELECT_CANCEL, List.of("escape", "ctrl+c")),
        Map.entry(EditorAction.SESSION_SCOPE_TOGGLE, List.of("tab")),
        Map.entry(EditorAction.SESSION_SORT_TOGGLE, List.of("ctrl+s")),
        Map.entry(EditorAction.SESSION_NAMED_FILTER_TOGGLE, List.of("ctrl+n")),
        Map.entry(EditorAction.SESSION_PATH_TOGGLE, List.of("ctrl+p")),
        Map.entry(EditorAction.SESSION_DELETE, List.of("ctrl+d")),
        Map.entry(EditorAction.SESSION_RENAME, List.of("ctrl+r")),
        Map.entry(EditorAction.YANK, List.of("ctrl+y")),
        Map.entry(EditorAction.YANK_POP, List.of("alt+y")),
        Map.entry(EditorAction.UNDO, List.of("ctrl+-"))
    );

    private static EditorKeybindings global = new EditorKeybindings();

    private final EnumMap<EditorAction, List<String>> actionToKeys = new EnumMap<>(EditorAction.class);

    public EditorKeybindings() {
        this(Map.of());
    }

    public EditorKeybindings(Map<EditorAction, List<String>> overrides) {
        actionToKeys.putAll(DEFAULT_EDITOR_KEYBINDINGS);
        for (var entry : overrides.entrySet()) {
            actionToKeys.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
    }

    public boolean matches(String data, EditorAction action) {
        Objects.requireNonNull(data, "data");
        var keys = actionToKeys.get(action);
        if (keys == null) {
            return false;
        }
        for (var key : keys) {
            if (KeyMatcher.matches(data, key)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getKeys(EditorAction action) {
        return actionToKeys.getOrDefault(action, List.of());
    }

    public static EditorKeybindings global() {
        return global;
    }

    public static void setGlobal(EditorKeybindings keybindings) {
        global = Objects.requireNonNull(keybindings, "keybindings");
    }
}
