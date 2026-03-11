package dev.pi.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pi.tui.EditorAction;
import dev.pi.tui.EditorKeybindings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class PiCliKeybindingsLoader {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, EditorAction> EDITOR_ACTION_ALIASES = Map.ofEntries(
        Map.entry("cursorUp", EditorAction.CURSOR_UP),
        Map.entry("cursorDown", EditorAction.CURSOR_DOWN),
        Map.entry("cursorLeft", EditorAction.CURSOR_LEFT),
        Map.entry("cursorRight", EditorAction.CURSOR_RIGHT),
        Map.entry("cursorWordLeft", EditorAction.CURSOR_WORD_LEFT),
        Map.entry("cursorWordRight", EditorAction.CURSOR_WORD_RIGHT),
        Map.entry("cursorLineStart", EditorAction.CURSOR_LINE_START),
        Map.entry("cursorLineEnd", EditorAction.CURSOR_LINE_END),
        Map.entry("deleteCharBackward", EditorAction.DELETE_CHAR_BACKWARD),
        Map.entry("deleteCharForward", EditorAction.DELETE_CHAR_FORWARD),
        Map.entry("deleteWordBackward", EditorAction.DELETE_WORD_BACKWARD),
        Map.entry("deleteWordForward", EditorAction.DELETE_WORD_FORWARD),
        Map.entry("deleteToLineStart", EditorAction.DELETE_TO_LINE_START),
        Map.entry("deleteToLineEnd", EditorAction.DELETE_TO_LINE_END),
        Map.entry("newLine", EditorAction.NEW_LINE),
        Map.entry("submit", EditorAction.SUBMIT),
        Map.entry("selectCancel", EditorAction.SELECT_CANCEL),
        Map.entry("yank", EditorAction.YANK),
        Map.entry("yankPop", EditorAction.YANK_POP),
        Map.entry("undo", EditorAction.UNDO),
        Map.entry("tab", EditorAction.SESSION_SCOPE_TOGGLE),
        Map.entry("toggleSessionSort", EditorAction.SESSION_SORT_TOGGLE),
        Map.entry("toggleSessionPath", EditorAction.SESSION_PATH_TOGGLE),
        Map.entry("deleteSession", EditorAction.SESSION_DELETE),
        Map.entry("renameSession", EditorAction.SESSION_RENAME)
    );
    private static final Map<String, PiAppAction> APP_ACTION_ALIASES = Map.of(
        "interrupt",
        PiAppAction.INTERRUPT,
        "resume",
        PiAppAction.RESUME,
        "tree",
        PiAppAction.TREE,
        "fork",
        PiAppAction.FORK,
        "toggleSessionNamedFilter",
        PiAppAction.TOGGLE_SESSION_NAMED_FILTER
    );

    private final Path agentDir;

    PiCliKeybindingsLoader(Path agentDir) {
        this.agentDir = Objects.requireNonNull(agentDir, "agentDir").toAbsolutePath().normalize();
    }

    static PiCliKeybindingsLoader createDefault() {
        return new PiCliKeybindingsLoader(Path.of(System.getProperty("user.home"), ".pi", "agent"));
    }

    LoadedKeybindings load() {
        return loadOverrides();
    }

    private LoadedKeybindings loadOverrides() {
        var keybindingsPath = agentDir.resolve("keybindings.json");
        if (!Files.exists(keybindingsPath)) {
            return new LoadedKeybindings(new EditorKeybindings(), new PiAppKeybindings());
        }
        try {
            var root = OBJECT_MAPPER.readTree(keybindingsPath.toFile());
            if (root == null || !root.isObject()) {
                return new LoadedKeybindings(new EditorKeybindings(), new PiAppKeybindings());
            }
            var editorOverrides = new EnumMap<EditorAction, List<String>>(EditorAction.class);
            var appOverrides = new EnumMap<PiAppAction, List<String>>(PiAppAction.class);
            var fields = root.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                var keys = parseKeys(field.getValue());
                if (keys.isEmpty()) {
                    continue;
                }
                var editorAction = resolveEditorAction(field.getKey());
                if (editorAction != null) {
                    editorOverrides.put(editorAction, List.copyOf(keys));
                }
                var appAction = resolveAppAction(field.getKey());
                if (appAction != null) {
                    appOverrides.put(appAction, List.copyOf(keys));
                }
            }
            return new LoadedKeybindings(
                new EditorKeybindings(Map.copyOf(editorOverrides)),
                new PiAppKeybindings(Map.copyOf(appOverrides))
            );
        } catch (IOException ignored) {
            return new LoadedKeybindings(new EditorKeybindings(), new PiAppKeybindings());
        }
    }

    private static EditorAction resolveEditorAction(String name) {
        var alias = EDITOR_ACTION_ALIASES.get(name);
        if (alias != null) {
            return alias;
        }
        try {
            return EditorAction.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static PiAppAction resolveAppAction(String name) {
        var alias = APP_ACTION_ALIASES.get(name);
        if (alias != null) {
            return alias;
        }
        try {
            return PiAppAction.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static List<String> parseKeys(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isTextual()) {
            var value = node.asText().trim();
            return value.isEmpty() ? List.of() : List.of(value);
        }
        if (!node.isArray()) {
            return List.of();
        }
        var values = new ArrayList<String>();
        for (var item : node) {
            if (!item.isTextual()) {
                continue;
            }
            var value = item.asText().trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    record LoadedKeybindings(
        EditorKeybindings editorKeybindings,
        PiAppKeybindings appKeybindings
    ) {
    }
}
