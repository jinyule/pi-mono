package dev.pi.cli;

import dev.pi.tui.KeyMatcher;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class PiAppKeybindings {
    private static final Map<PiAppAction, List<String>> DEFAULTS = Map.of(
        PiAppAction.INTERRUPT,
        List.of("escape"),
        PiAppAction.RESUME,
        List.of(),
        PiAppAction.CYCLE_MODEL_FORWARD,
        List.of("ctrl+p"),
        PiAppAction.CYCLE_THINKING_LEVEL,
        List.of("shift+tab"),
        PiAppAction.TREE,
        List.of(),
        PiAppAction.FORK,
        List.of(),
        PiAppAction.TOGGLE_SESSION_NAMED_FILTER,
        List.of("ctrl+n")
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
}
