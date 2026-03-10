package dev.pi.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public final class SelectList implements Component {
    private final List<SelectItem> items;
    private final SelectListTheme theme;
    private final int maxVisible;

    private List<SelectItem> filteredItems;
    private int selectedIndex;

    private Consumer<SelectItem> onSelect;
    private Runnable onCancel;
    private Consumer<SelectItem> onSelectionChange;

    public SelectList(List<SelectItem> items, int maxVisible, SelectListTheme theme) {
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.filteredItems = this.items;
        this.maxVisible = Math.max(1, maxVisible);
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    public void setOnSelect(Consumer<SelectItem> onSelect) {
        this.onSelect = onSelect;
    }

    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    public void setOnSelectionChange(Consumer<SelectItem> onSelectionChange) {
        this.onSelectionChange = onSelectionChange;
    }

    public void setFilter(String filter) {
        var normalized = filter == null ? "" : filter.toLowerCase(Locale.ROOT).trim();
        var tokens = normalized.isEmpty() ? List.<String>of() : List.of(normalized.split("\\s+"));
        filteredItems = items.stream()
            .filter(item -> matchesFilter(item.value(), tokens))
            .toList();
        selectedIndex = 0;
    }

    public void setSelectedIndex(int index) {
        if (filteredItems.isEmpty()) {
            selectedIndex = 0;
            return;
        }
        selectedIndex = Math.max(0, Math.min(index, filteredItems.size() - 1));
    }

    public SelectItem getSelectedItem() {
        if (filteredItems.isEmpty()) {
            return null;
        }
        return filteredItems.get(selectedIndex);
    }

    @Override
    public List<String> render(int width) {
        if (filteredItems.isEmpty()) {
            return List.of(theme.noMatch("  No matching commands"));
        }

        var lines = new ArrayList<String>();
        var startIndex = Math.max(0, Math.min(selectedIndex - Math.floorDiv(maxVisible, 2), filteredItems.size() - maxVisible));
        var endIndex = Math.min(startIndex + maxVisible, filteredItems.size());

        for (var index = startIndex; index < endIndex; index += 1) {
            var item = filteredItems.get(index);
            lines.add(renderItem(item, index == selectedIndex, width));
        }

        if (startIndex > 0 || endIndex < filteredItems.size()) {
            var scrollText = "  (" + (selectedIndex + 1) + "/" + filteredItems.size() + ")";
            lines.add(theme.scrollInfo(TerminalText.truncateToWidth(scrollText, Math.max(1, width - 2), "")));
        }
        return List.copyOf(lines);
    }

    @Override
    public void handleInput(String data) {
        var keybindings = EditorKeybindings.global();
        if (filteredItems.isEmpty()) {
            if (keybindings.matches(data, EditorAction.SELECT_CANCEL) && onCancel != null) {
                onCancel.run();
            }
            return;
        }

        if (keybindings.matches(data, EditorAction.CURSOR_UP)) {
            selectedIndex = selectedIndex == 0 ? filteredItems.size() - 1 : selectedIndex - 1;
            notifySelectionChange();
            return;
        }
        if (keybindings.matches(data, EditorAction.CURSOR_DOWN)) {
            selectedIndex = selectedIndex == filteredItems.size() - 1 ? 0 : selectedIndex + 1;
            notifySelectionChange();
            return;
        }
        if (keybindings.matches(data, EditorAction.SUBMIT)) {
            if (onSelect != null) {
                onSelect.accept(filteredItems.get(selectedIndex));
            }
            return;
        }
        if (keybindings.matches(data, EditorAction.SELECT_CANCEL) && onCancel != null) {
            onCancel.run();
        }
    }

    private String renderItem(SelectItem item, boolean selected, int width) {
        var description = normalizeSingleLine(item.description());
        var display = item.displayValue();
        var selectedPrefix = theme.selectedPrefix("→ ");
        var plainPrefix = "  ";

        if (selected) {
            var prefixWidth = TerminalText.visibleWidth(selectedPrefix);
            if (description != null && width > 40) {
                var maxValueWidth = Math.min(30, Math.max(1, width - prefixWidth - 4));
                var truncatedValue = TerminalText.truncateToWidth(display, maxValueWidth, "");
                var spacing = " ".repeat(Math.max(1, 32 - TerminalText.visibleWidth(truncatedValue)));
                var descriptionStart = prefixWidth + TerminalText.visibleWidth(truncatedValue) + spacing.length();
                var remainingWidth = width - descriptionStart - 2;
                if (remainingWidth > 10) {
                    var truncatedDescription = TerminalText.truncateToWidth(description, remainingWidth, "");
                    return selectedPrefix + theme.selectedText(truncatedValue + spacing + truncatedDescription);
                }
            }
            var maxWidth = Math.max(1, width - prefixWidth - 2);
            return selectedPrefix + theme.selectedText(TerminalText.truncateToWidth(display, maxWidth, ""));
        }

        if (description != null && width > 40) {
            var maxValueWidth = Math.min(30, Math.max(1, width - plainPrefix.length() - 4));
            var truncatedValue = TerminalText.truncateToWidth(display, maxValueWidth, "");
            var spacing = " ".repeat(Math.max(1, 32 - TerminalText.visibleWidth(truncatedValue)));
            var descriptionStart = plainPrefix.length() + TerminalText.visibleWidth(truncatedValue) + spacing.length();
            var remainingWidth = width - descriptionStart - 2;
            if (remainingWidth > 10) {
                var truncatedDescription = TerminalText.truncateToWidth(description, remainingWidth, "");
                return plainPrefix + truncatedValue + theme.description(spacing + truncatedDescription);
            }
        }

        var maxWidth = Math.max(1, width - plainPrefix.length() - 2);
        return plainPrefix + TerminalText.truncateToWidth(display, maxWidth, "");
    }

    private void notifySelectionChange() {
        if (onSelectionChange != null) {
            onSelectionChange.accept(filteredItems.get(selectedIndex));
        }
    }

    private static String normalizeSingleLine(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static boolean matchesFilter(String value, List<String> tokens) {
        if (tokens.isEmpty()) {
            return true;
        }
        var normalizedValue = value.toLowerCase(Locale.ROOT);
        for (var token : tokens) {
            if (!normalizedValue.contains(token)) {
                return false;
            }
        }
        return true;
    }
}
