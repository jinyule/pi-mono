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
    private static final Map<String, EditorAction> ACTION_ALIASES = Map.ofEntries(
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
        Map.entry("toggleSessionNamedFilter", EditorAction.SESSION_NAMED_FILTER_TOGGLE),
        Map.entry("toggleSessionPath", EditorAction.SESSION_PATH_TOGGLE),
        Map.entry("deleteSession", EditorAction.SESSION_DELETE),
        Map.entry("renameSession", EditorAction.SESSION_RENAME)
    );

    private final Path agentDir;

    PiCliKeybindingsLoader(Path agentDir) {
        this.agentDir = Objects.requireNonNull(agentDir, "agentDir").toAbsolutePath().normalize();
    }

    static PiCliKeybindingsLoader createDefault() {
        return new PiCliKeybindingsLoader(Path.of(System.getProperty("user.home"), ".pi", "agent"));
    }

    EditorKeybindings load() {
        return new EditorKeybindings(loadOverrides());
    }

    private Map<EditorAction, List<String>> loadOverrides() {
        var keybindingsPath = agentDir.resolve("keybindings.json");
        if (!Files.exists(keybindingsPath)) {
            return Map.of();
        }
        try {
            var root = OBJECT_MAPPER.readTree(keybindingsPath.toFile());
            if (root == null || !root.isObject()) {
                return Map.of();
            }
            var overrides = new EnumMap<EditorAction, List<String>>(EditorAction.class);
            var fields = root.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                var action = resolveAction(field.getKey());
                var keys = parseKeys(field.getValue());
                if (action == null || keys.isEmpty()) {
                    continue;
                }
                overrides.put(action, List.copyOf(keys));
            }
            return Map.copyOf(overrides);
        } catch (IOException ignored) {
            return Map.of();
        }
    }

    private static EditorAction resolveAction(String name) {
        var alias = ACTION_ALIASES.get(name);
        if (alias != null) {
            return alias;
        }
        try {
            return EditorAction.valueOf(name);
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
}
