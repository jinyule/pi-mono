package dev.pi.cli;

import dev.pi.tui.Component;
import dev.pi.tui.EditorAction;
import dev.pi.tui.EditorKeybindings;
import dev.pi.tui.Focusable;
import dev.pi.tui.Input;
import dev.pi.tui.TerminalText;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public final class PiScopedModelsSelector implements Component, Focusable {
    private final List<PiInteractiveSession.SelectableModel> allModels;
    private final LinkedHashSet<String> enabledIds = new LinkedHashSet<>();
    private final Input search = new Input();
    private final Consumer<List<String>> onChange;
    private final Consumer<List<String>> onSave;
    private final Runnable onCancel;

    private List<PiInteractiveSession.SelectableModel> filteredModels;
    private int selectedIndex;
    private boolean hasFilter;
    private boolean dirty;
    private boolean focused;

    public PiScopedModelsSelector(
        PiInteractiveSession.ScopedModelsSelection selection,
        Consumer<List<String>> onChange,
        Consumer<List<String>> onSave,
        Runnable onCancel
    ) {
        Objects.requireNonNull(selection, "selection");
        this.allModels = List.copyOf(selection.allModels());
        this.hasFilter = selection.hasFilter();
        this.enabledIds.addAll(selection.enabledModelIds());
        this.onChange = Objects.requireNonNull(onChange, "onChange");
        this.onSave = Objects.requireNonNull(onSave, "onSave");
        this.onCancel = Objects.requireNonNull(onCancel, "onCancel");
        this.search.setFocused(true);
        this.filteredModels = orderedModels();
        this.selectedIndex = Math.max(0, initialSelectionIndex());
    }

    @Override
    public List<String> render(int width) {
        var safeWidth = Math.max(1, width);
        var lines = new ArrayList<String>();
        lines.add(PiCliAnsi.borderMuted("\u2500".repeat(safeWidth)));
        lines.add(PiCliAnsi.accentBold("Scoped Models"));
        lines.add(PiCliAnsi.muted("Session-only until saved"));
        lines.add("");
        lines.addAll(search.render(safeWidth));
        lines.add("");

        if (filteredModels.isEmpty()) {
            lines.add(PiCliAnsi.muted("  No matching models"));
        } else {
            var startIndex = Math.max(0, Math.min(selectedIndex - 5, Math.max(0, filteredModels.size() - 10)));
            var endIndex = Math.min(startIndex + 10, filteredModels.size());
            for (var index = startIndex; index < endIndex; index += 1) {
                var model = filteredModels.get(index);
                var selected = index == selectedIndex;
                lines.add(renderModelLine(model, selected, safeWidth));
            }
            if (startIndex > 0 || endIndex < filteredModels.size()) {
                lines.add(PiCliAnsi.muted("  (" + (selectedIndex + 1) + "/" + filteredModels.size() + ")"));
            }
            var selectedModel = filteredModels.get(selectedIndex);
            lines.add("");
            lines.add(PiCliAnsi.muted("  Model Name: " + selectedModel.modelName()));
        }

        lines.add("");
        lines.add(renderHintLine());
        lines.add(PiCliAnsi.borderMuted("\u2500".repeat(safeWidth)));
        return List.copyOf(lines);
    }

    @Override
    public void handleInput(String data) {
        var keybindings = EditorKeybindings.global();
        if (keybindings.matches(data, EditorAction.CURSOR_UP)) {
            if (!filteredModels.isEmpty()) {
                selectedIndex = selectedIndex == 0 ? filteredModels.size() - 1 : selectedIndex - 1;
            }
            return;
        }
        if (keybindings.matches(data, EditorAction.CURSOR_DOWN)) {
            if (!filteredModels.isEmpty()) {
                selectedIndex = selectedIndex == filteredModels.size() - 1 ? 0 : selectedIndex + 1;
            }
            return;
        }
        if (keybindings.matches(data, EditorAction.SUBMIT)) {
            toggleSelected();
            return;
        }
        if (keybindings.matches(data, EditorAction.MODEL_SCOPE_ENABLE_ALL)) {
            enableVisibleModels();
            dirty = true;
            onChange.accept(currentSavedIds());
            refresh();
            return;
        }
        if (keybindings.matches(data, EditorAction.MODEL_SCOPE_CLEAR_ALL)) {
            clearVisibleModels();
            dirty = true;
            onChange.accept(currentSavedIds());
            refresh();
            return;
        }
        if (keybindings.matches(data, EditorAction.MODEL_SCOPE_SAVE)) {
            onSave.accept(currentSavedIds());
            dirty = false;
            return;
        }
        if (keybindings.matches(data, EditorAction.SELECT_CANCEL)) {
            onCancel.run();
            return;
        }
        search.handleInput(data);
        refresh();
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

    private void refresh() {
        var query = search.getValue();
        if (query == null || query.isBlank()) {
            filteredModels = orderedModels();
        } else {
            var normalized = query.toLowerCase(Locale.ROOT);
            filteredModels = orderedModels().stream()
                .filter(model -> searchText(model).contains(normalized))
                .toList();
        }
        selectedIndex = Math.min(selectedIndex, Math.max(0, filteredModels.size() - 1));
    }

    private void toggleSelected() {
        if (filteredModels.isEmpty()) {
            return;
        }
        var selected = filteredModels.get(selectedIndex);
        var modelId = fullId(selected);
        if (!hasFilter) {
            enabledIds.clear();
            enabledIds.add(modelId);
            hasFilter = true;
        } else if (enabledIds.contains(modelId)) {
            enabledIds.remove(modelId);
        } else {
            enabledIds.add(modelId);
        }
        if (enabledIds.isEmpty() || enabledIds.size() >= allModels.size()) {
            hasFilter = false;
            enabledIds.clear();
        }
        dirty = true;
        onChange.accept(currentSavedIds());
        refresh();
    }

    private List<String> currentSavedIds() {
        if (!hasFilter) {
            return List.of();
        }
        var ordered = new ArrayList<String>();
        for (var model : allModels) {
            var fullId = fullId(model);
            if (enabledIds.contains(fullId)) {
                ordered.add(fullId);
            }
        }
        return List.copyOf(ordered);
    }

    private boolean isEnabled(PiInteractiveSession.SelectableModel model) {
        return !hasFilter || enabledIds.contains(fullId(model));
    }

    private List<PiInteractiveSession.SelectableModel> orderedModels() {
        if (!hasFilter || enabledIds.isEmpty()) {
            return allModels;
        }
        var ordered = new ArrayList<PiInteractiveSession.SelectableModel>(allModels.size());
        for (var id : enabledIds) {
            for (var model : allModels) {
                if (fullId(model).equals(id) && !ordered.contains(model)) {
                    ordered.add(model);
                }
            }
        }
        for (var model : allModels) {
            if (!ordered.contains(model)) {
                ordered.add(model);
            }
        }
        return List.copyOf(ordered);
    }

    private int initialSelectionIndex() {
        for (var index = 0; index < filteredModels.size(); index += 1) {
            if (filteredModels.get(index).current()) {
                return index;
            }
        }
        return 0;
    }

    private void enableVisibleModels() {
        if (search.getValue() == null || search.getValue().isBlank()) {
            hasFilter = false;
            enabledIds.clear();
            return;
        }
        for (var model : filteredModels) {
            enabledIds.add(fullId(model));
        }
        if (enabledIds.size() >= allModels.size()) {
            hasFilter = false;
            enabledIds.clear();
            return;
        }
        hasFilter = true;
    }

    private void clearVisibleModels() {
        if (!hasFilter) {
            enabledIds.clear();
            for (var model : allModels) {
                enabledIds.add(fullId(model));
            }
        }
        if (search.getValue() == null || search.getValue().isBlank()) {
            enabledIds.clear();
            hasFilter = true;
            return;
        }
        for (var model : filteredModels) {
            enabledIds.remove(fullId(model));
        }
        hasFilter = true;
    }

    private String renderModelLine(PiInteractiveSession.SelectableModel model, boolean selected, int width) {
        var prefix = selected ? PiCliAnsi.accent("\u2192 ") : "  ";
        var modelText = selected ? PiCliAnsi.accent(model.modelId()) : model.modelId();
        var provider = PiCliAnsi.muted(" [" + model.provider() + "]");
        var status = !hasFilter
            ? ""
            : isEnabled(model) ? PiCliAnsi.success(" \u2713") : PiCliAnsi.dim(" \u00b7");
        var line = prefix + modelText + provider + status;
        return TerminalText.truncateToWidth(line, Math.max(1, width), "");
    }

    private String renderHintLine() {
        var status = hasFilter
            ? enabledIds.size() + "/" + allModels.size() + " enabled"
            : "all enabled";
        var dirtySuffix = dirty ? PiCliAnsi.warning(" (unsaved)") : "";
        return PiCliAnsi.muted("  ")
            + PiCliKeyHints.editorHint(EditorAction.SUBMIT, "to toggle")
            + PiCliAnsi.muted(" \u00b7 ")
            + PiCliKeyHints.editorHint(EditorAction.MODEL_SCOPE_ENABLE_ALL, "all")
            + PiCliAnsi.muted(" \u00b7 ")
            + PiCliKeyHints.editorHint(EditorAction.MODEL_SCOPE_CLEAR_ALL, "clear")
            + PiCliAnsi.muted(" \u00b7 ")
            + PiCliKeyHints.editorHint(EditorAction.MODEL_SCOPE_SAVE, "save")
            + PiCliAnsi.muted(" \u00b7 ")
            + PiCliKeyHints.editorHint(EditorAction.SELECT_CANCEL, "close")
            + PiCliAnsi.muted(" \u00b7 " + status)
            + dirtySuffix;
    }

    private static String searchText(PiInteractiveSession.SelectableModel model) {
        return (model.provider() + " " + model.modelId() + " " + model.modelName()).toLowerCase(Locale.ROOT);
    }

    private static String fullId(PiInteractiveSession.SelectableModel model) {
        return model.provider() + "/" + model.modelId();
    }
}
