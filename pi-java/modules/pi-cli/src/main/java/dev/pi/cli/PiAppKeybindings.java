package dev.pi.cli;

import dev.pi.tui.KeyMatcher;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class PiAppKeybindings {
    private static final List<String> DEFAULT_PASTE_IMAGE_KEYS = isWindows()
        ? List.of("alt+v")
        : List.of("ctrl+v");
    private static final Map<PiAppAction, List<String>> DEFAULTS = Map.ofEntries(
        Map.entry(PiAppAction.INTERRUPT, List.of("escape")),
        Map.entry(PiAppAction.CLEAR, List.of("ctrl+c")),
        Map.entry(PiAppAction.EXIT, List.of("ctrl+d")),
        Map.entry(PiAppAction.RESUME, List.of()),
        Map.entry(PiAppAction.CYCLE_MODEL_FORWARD, List.of("ctrl+p")),
        Map.entry(PiAppAction.CYCLE_MODEL_BACKWARD, List.of("shift+ctrl+p")),
        Map.entry(PiAppAction.CYCLE_THINKING_LEVEL, List.of("shift+tab")),
        Map.entry(PiAppAction.SELECT_MODEL, List.of("ctrl+l")),
        Map.entry(PiAppAction.EXPAND_TOOLS, List.of("ctrl+o")),
        Map.entry(PiAppAction.TOGGLE_THINKING, List.of("ctrl+t")),
        Map.entry(PiAppAction.FOLLOW_UP, List.of("alt+enter")),
        Map.entry(PiAppAction.DEQUEUE, List.of("alt+up")),
        Map.entry(PiAppAction.PASTE_IMAGE, DEFAULT_PASTE_IMAGE_KEYS),
        Map.entry(PiAppAction.NEW_SESSION, List.of()),
        Map.entry(PiAppAction.TREE, List.of()),
        Map.entry(PiAppAction.FORK, List.of()),
        Map.entry(PiAppAction.TOGGLE_SESSION_NAMED_FILTER, List.of("ctrl+n"))
    );

    private static PiAppKeybindings global = new PiAppKeybindings();

    private final Map<PiAppAction, List<String>> bindings;

    PiAppKeybindings() {
        this(Map.of());
    }

    PiAppKeybindings(Map<PiAppAction, List<String>> overrides) {
        var nextBindings = new EnumMap<PiAppAction, List<String>>(PiAppAction.class);
        for (var entry : DEFAULTS.entrySet()) {
            nextBindings.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        for (var entry : Objects.requireNonNull(overrides, "overrides").entrySet()) {
            nextBindings.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.bindings = Map.copyOf(nextBindings);
    }

    boolean matches(String data, PiAppAction action) {
        for (var key : getKeys(action)) {
            if (KeyMatcher.matches(data, key)) {
                return true;
            }
        }
        return false;
    }

    List<String> getKeys(PiAppAction action) {
        return bindings.getOrDefault(action, List.of());
    }

    static PiAppKeybindings global() {
        return global;
    }

    static void setGlobal(PiAppKeybindings keybindings) {
        global = keybindings == null ? new PiAppKeybindings() : keybindings;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("win");
    }
}
