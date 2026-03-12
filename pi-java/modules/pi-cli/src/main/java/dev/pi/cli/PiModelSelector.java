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
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntConsumer;

public final class PiModelSelector implements Component, Focusable {
    private static final String VALUE_DELIMITER = "\u0000";

    private enum Scope {
        ALL,
        SCOPED
    }

    private final Input search = new Input();
    private final Runnable requestRender;
    private final IntConsumer onSelect;
    private final Runnable onCancel;
    private final List<PiInteractiveSession.SelectableModel> allModels;
    private final List<PiInteractiveSession.SelectableModel> scopedModels;
    private Scope scope;
    private SelectList models;
    private boolean focused;

    public PiModelSelector(
        PiInteractiveSession.ModelSelection selection,
        IntConsumer onSelect,
        Runnable onCancel,
        Runnable requestRender
    ) {
        Objects.requireNonNull(selection, "selection");
        this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
        this.onCancel = Objects.requireNonNull(onCancel, "onCancel");
        this.requestRender = Objects.requireNonNull(requestRender, "requestRender");
        this.allModels = sortModels(selection.allModels());
        this.scopedModels = sortModels(selection.scopedModels());
        this.scope = this.scopedModels.isEmpty() ? Scope.ALL : Scope.SCOPED;
        this.search.setFocused(true);
        rebuildList();
    }

    @Override
    public List<String> render(int width) {
        var lines = new ArrayList<String>();
        lines.add(PiCliAnsi.bold("Select model"));
        if (!scopedModels.isEmpty()) {
            lines.add(scopeSummary());
            lines.add(PiCliAnsi.muted("tab scope · type to filter · Enter selects · Esc cancels"));
        } else {
            lines.add(PiCliAnsi.muted("Type to filter. Enter selects. Esc cancels."));
        }
        lines.add("");
        lines.addAll(search.render(width));
        lines.add("");
        lines.addAll(models.render(width));
        return List.copyOf(lines);
    }

    @Override
    public void handleInput(String data) {
        var keybindings = EditorKeybindings.global();
        if (keybindings.matches(data, EditorAction.SESSION_SCOPE_TOGGLE) && !scopedModels.isEmpty()) {
            scope = scope == Scope.ALL ? Scope.SCOPED : Scope.ALL;
            rebuildList();
            requestRender.run();
            return;
        }
        if (
            keybindings.matches(data, EditorAction.CURSOR_UP) ||
            keybindings.matches(data, EditorAction.CURSOR_DOWN) ||
            keybindings.matches(data, EditorAction.SUBMIT) ||
            keybindings.matches(data, EditorAction.SELECT_CANCEL)
        ) {
            models.handleInput(data);
            return;
        }

        search.handleInput(data);
        models.setFilter(search.getValue());
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

    private void rebuildList() {
        var activeModels = activeModels();
        var items = activeModels.stream().map(PiModelSelector::toSelectItem).toList();
        models = new SelectList(items, Math.max(6, Math.min(12, Math.max(1, items.size()))), PiSessionPicker.sessionTheme());
        models.setOnSelectionChange(ignored -> requestRender.run());
        models.setOnSelect(item -> onSelect.accept(decodeIndex(item.value())));
        models.setOnCancel(onCancel);
        models.setSelectedIndex(selectedIndex(activeModels));
        if (search.getValue() != null && !search.getValue().isBlank()) {
            models.setFilter(search.getValue());
        }
    }

    private List<PiInteractiveSession.SelectableModel> activeModels() {
        return scope == Scope.SCOPED ? scopedModels : allModels;
    }

    private String scopeSummary() {
        var all = scope == Scope.ALL ? PiCliAnsi.accent("◉ All") : PiCliAnsi.muted("○ All");
        var scoped = scope == Scope.SCOPED ? PiCliAnsi.accent("◉ Scoped") : PiCliAnsi.muted("○ Scoped");
        return all + PiCliAnsi.muted(" | ") + scoped;
    }

    private static SelectItem toSelectItem(PiInteractiveSession.SelectableModel model) {
        var label = model.modelId() + (model.current() ? " ✓" : "");
        return new SelectItem(encodeValue(label, model.index()), label, metadata(model));
    }

    private static List<PiInteractiveSession.SelectableModel> sortModels(List<PiInteractiveSession.SelectableModel> models) {
        var sorted = new ArrayList<>(models);
        sorted.sort((left, right) -> {
            if (left.current() && !right.current()) {
                return -1;
            }
            if (!left.current() && right.current()) {
                return 1;
            }
            var providerCompare = left.provider().compareToIgnoreCase(right.provider());
            if (providerCompare != 0) {
                return providerCompare;
            }
            return left.modelId().compareToIgnoreCase(right.modelId());
        });
        return List.copyOf(sorted);
    }

    private static int selectedIndex(List<PiInteractiveSession.SelectableModel> models) {
        for (var index = 0; index < models.size(); index += 1) {
            if (models.get(index).current()) {
                return index;
            }
        }
        return 0;
    }

    private static String encodeValue(String label, int index) {
        return label + VALUE_DELIMITER + index;
    }

    private static int decodeIndex(String value) {
        var delimiter = value.lastIndexOf(VALUE_DELIMITER);
        return delimiter >= 0 ? Integer.parseInt(value.substring(delimiter + VALUE_DELIMITER.length())) : Integer.parseInt(value);
    }

    private static String metadata(PiInteractiveSession.SelectableModel model) {
        var parts = new ArrayList<String>();
        parts.add("[" + model.provider() + "]");
        if (model.modelName() != null && !model.modelName().isBlank() && !model.modelName().equals(model.modelId())) {
            parts.add(model.modelName());
        }
        if (model.reasoning()) {
            parts.add("thinking: " + model.thinkingLevel());
        }
        if (model.contextWindow() > 0) {
            parts.add(formatContextWindow(model.contextWindow()));
        }
        return String.join(" · ", parts);
    }

    private static String formatContextWindow(int contextWindow) {
        if (contextWindow < 1_000) {
            return contextWindow + " ctx";
        }
        if (contextWindow < 1_000_000) {
            var value = contextWindow / 1_000.0;
            var formatted = value >= 100 ? Integer.toString((int) Math.round(value)) : String.format(Locale.ROOT, "%.0f", value);
            return formatted + "k ctx";
        }
        return String.format(Locale.ROOT, "%.1fM ctx", contextWindow / 1_000_000.0);
    }
}
