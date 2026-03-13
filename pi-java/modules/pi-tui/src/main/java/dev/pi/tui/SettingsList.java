package dev.pi.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;

public final class SettingsList implements Component, Focusable {
    private final List<SettingItem> items;
    private List<SettingItem> filteredItems;
    private final int maxVisible;
    private final SettingsListTheme theme;
    private final BiConsumer<String, String> onChange;
    private final Runnable onCancel;
    private final boolean searchEnabled;
    private final Input searchInput;

    private int selectedIndex;
    private boolean focused;
    private Component submenuComponent;
    private Integer submenuItemIndex;

    public SettingsList(
        List<SettingItem> items,
        int maxVisible,
        SettingsListTheme theme,
        BiConsumer<String, String> onChange,
        Runnable onCancel
    ) {
        this(items, maxVisible, theme, onChange, onCancel, SettingsListOptions.defaults());
    }

    public SettingsList(
        List<SettingItem> items,
        int maxVisible,
        SettingsListTheme theme,
        BiConsumer<String, String> onChange,
        Runnable onCancel,
        SettingsListOptions options
    ) {
        this.items = new ArrayList<>(Objects.requireNonNull(items, "items"));
        this.filteredItems = this.items;
        this.maxVisible = Math.max(1, maxVisible);
        this.theme = Objects.requireNonNull(theme, "theme");
        this.onChange = Objects.requireNonNull(onChange, "onChange");
        this.onCancel = Objects.requireNonNull(onCancel, "onCancel");
        this.searchEnabled = options != null && options.enableSearch();
        this.searchInput = searchEnabled ? new Input() : null;
        if (this.searchInput != null) {
            this.searchInput.setFocused(focused);
        }
    }

    public void updateValue(String id, String newValue) {
        for (var item : items) {
            if (item.id().equals(id)) {
                item.setCurrentValue(newValue);
                break;
            }
        }
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        if (searchInput != null) {
            searchInput.setFocused(focused);
        }
    }

    @Override
    public void invalidate() {
        if (submenuComponent != null) {
            submenuComponent.invalidate();
        }
    }

    @Override
    public List<String> render(int width) {
        if (submenuComponent != null) {
            return submenuComponent.render(width);
        }
        return renderMainList(width);
    }

    @Override
    public void handleInput(String data) {
        if (submenuComponent != null) {
            submenuComponent.handleInput(data);
            return;
        }

        var keybindings = EditorKeybindings.global();
        var displayItems = searchEnabled ? filteredItems : items;
        if (keybindings.matches(data, EditorAction.CURSOR_UP)) {
            if (displayItems.isEmpty()) {
                return;
            }
            selectedIndex = selectedIndex == 0 ? displayItems.size() - 1 : selectedIndex - 1;
            return;
        }
        if (keybindings.matches(data, EditorAction.CURSOR_DOWN)) {
            if (displayItems.isEmpty()) {
                return;
            }
            selectedIndex = selectedIndex == displayItems.size() - 1 ? 0 : selectedIndex + 1;
            return;
        }
        if (keybindings.matches(data, EditorAction.SUBMIT) || " ".equals(data)) {
            activateItem();
            return;
        }
        if (keybindings.matches(data, EditorAction.SELECT_CANCEL)) {
            onCancel.run();
            return;
        }
        if (searchEnabled && searchInput != null) {
            var sanitized = data.replace(" ", "");
            if (sanitized.isEmpty()) {
                return;
            }
            searchInput.handleInput(sanitized);
            applyFilter(searchInput.getValue());
        }
    }

    private List<String> renderMainList(int width) {
        var lines = new ArrayList<String>();
        if (searchEnabled && searchInput != null) {
            lines.addAll(searchInput.render(width));
            lines.add("");
        }

        if (items.isEmpty()) {
            lines.add(theme.hint("  No settings available"));
            if (searchEnabled) {
                addHintLine(lines, width);
            }
            return List.copyOf(lines);
        }

        var displayItems = searchEnabled ? filteredItems : items;
        if (displayItems.isEmpty()) {
            lines.add(TerminalText.truncateToWidth(theme.hint("  No matching settings"), width));
            addHintLine(lines, width);
            return List.copyOf(lines);
        }

        var startIndex = Math.max(0, Math.min(selectedIndex - Math.floorDiv(maxVisible, 2), displayItems.size() - maxVisible));
        var endIndex = Math.min(startIndex + maxVisible, displayItems.size());
        var maxLabelWidth = Math.min(30, displayItems.stream()
            .mapToInt(item -> TerminalText.visibleWidth(item.label()))
            .max()
            .orElse(0));

        for (var index = startIndex; index < endIndex; index += 1) {
            var item = displayItems.get(index);
            var selected = index == selectedIndex;
            var prefix = selected ? theme.cursor() : "  ";
            var prefixWidth = TerminalText.visibleWidth(prefix);
            var labelPadded = item.label() + " ".repeat(Math.max(0, maxLabelWidth - TerminalText.visibleWidth(item.label())));
            var label = theme.label(labelPadded, selected);
            var separator = "  ";
            var usedWidth = prefixWidth + maxLabelWidth + separator.length();
            var valueMaxWidth = Math.max(1, width - usedWidth - 2);
            var value = theme.value(TerminalText.truncateToWidth(item.currentValue(), valueMaxWidth, ""), selected);
            lines.add(TerminalText.truncateToWidth(prefix + label + separator + value, width));
        }

        if (startIndex > 0 || endIndex < displayItems.size()) {
            var scrollText = "  (" + (selectedIndex + 1) + "/" + displayItems.size() + ")";
            lines.add(theme.hint(TerminalText.truncateToWidth(scrollText, Math.max(1, width - 2), "")));
        }

        var selectedItem = displayItems.get(selectedIndex);
        if (selectedItem.description() != null && !selectedItem.description().isBlank()) {
            lines.add("");
            for (var line : TerminalText.wrapText(selectedItem.description(), Math.max(1, width - 4))) {
                lines.add(theme.description("  " + line));
            }
        }

        addHintLine(lines, width);
        return List.copyOf(lines);
    }

    private void activateItem() {
        var item = searchEnabled ? getFilteredSelection() : getBaseSelection();
        if (item == null) {
            return;
        }

        if (item.submenu() != null) {
            submenuItemIndex = selectedIndex;
            submenuComponent = item.submenu().create(item.currentValue(), selectedValue -> {
                if (selectedValue != null) {
                    item.setCurrentValue(selectedValue);
                    onChange.accept(item.id(), selectedValue);
                }
                closeSubmenu();
            });
            return;
        }

        if (!item.values().isEmpty()) {
            var currentIndex = item.values().indexOf(item.currentValue());
            var nextIndex = (currentIndex + 1) % item.values().size();
            var newValue = item.values().get(nextIndex);
            item.setCurrentValue(newValue);
            onChange.accept(item.id(), newValue);
        }
    }

    private SettingItem getFilteredSelection() {
        if (filteredItems.isEmpty()) {
            return null;
        }
        return filteredItems.get(selectedIndex);
    }

    private SettingItem getBaseSelection() {
        if (items.isEmpty()) {
            return null;
        }
        return items.get(selectedIndex);
    }

    private void closeSubmenu() {
        submenuComponent = null;
        if (submenuItemIndex != null) {
            selectedIndex = submenuItemIndex;
            submenuItemIndex = null;
        }
    }

    private void applyFilter(String query) {
        if (query == null || query.isBlank()) {
            filteredItems = items;
            selectedIndex = 0;
            return;
        }
        var normalizedQuery = query.toLowerCase(Locale.ROOT);
        filteredItems = items.stream()
            .filter(item -> fuzzyMatch(item.label().toLowerCase(Locale.ROOT), normalizedQuery))
            .toList();
        selectedIndex = 0;
    }

    private void addHintLine(List<String> lines, int width) {
        lines.add("");
        var submitKey = keyHint(EditorAction.SUBMIT);
        var cancelKey = keyHint(EditorAction.SELECT_CANCEL);
        var hint = searchEnabled
            ? theme.hintDescription("  Type to search \u00b7 ")
                + theme.hintKey(submitKey + "/space")
                + theme.hintDescription(" to change \u00b7 ")
                + theme.hintKey(cancelKey)
                + theme.hintDescription(" to cancel")
            : theme.hintDescription("  ")
                + theme.hintKey(submitKey + "/space")
                + theme.hintDescription(" to change \u00b7 ")
                + theme.hintKey(cancelKey)
                + theme.hintDescription(" to cancel");
        lines.add(TerminalText.truncateToWidth(hint, width));
    }

    private static boolean fuzzyMatch(String text, String query) {
        var queryIndex = 0;
        for (var index = 0; index < text.length() && queryIndex < query.length(); index += 1) {
            if (text.charAt(index) == query.charAt(queryIndex)) {
                queryIndex += 1;
            }
        }
        return queryIndex == query.length();
    }

    private static String keyHint(EditorAction action) {
        var keys = EditorKeybindings.global().getKeys(action);
        return keys.isEmpty() ? action.name().toLowerCase(Locale.ROOT) : keys.getFirst();
    }
}


