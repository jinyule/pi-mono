package dev.pi.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public final class SelectList implements Component {
    private static final String SELECTED_PREFIX = "\u2192 ";

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
        var selectedPrefix = theme.selectedPrefix(SELECTED_PREFIX);
        var plainPrefix = "  ";
        var prefix = selected ? selectedPrefix : plainPrefix;
        var availableWidth = Math.max(1, width - TerminalText.visibleWidth(prefix) - 2);
        var itemLayout = layoutItem(display, description, availableWidth);

        if (selected) {
            if (itemLayout != null) {
                return selectedPrefix
                    + theme.selectedText(itemLayout.display())
                    + theme.selectedDescription(itemLayout.spacing() + itemLayout.description());
            }
            return selectedPrefix + theme.selectedText(TerminalText.truncateToWidth(display, availableWidth, ""));
        }

        if (itemLayout != null) {
            return plainPrefix + itemLayout.display() + theme.description(itemLayout.spacing() + itemLayout.description());
        }

        return plainPrefix + TerminalText.truncateToWidth(display, availableWidth, "");
    }

    private static ItemLayout layoutItem(String display, String description, int availableWidth) {
        if (description == null || availableWidth <= 0) {
            return null;
        }
        var gapWidth = 2;
        var minDisplayWidth = 8;
        var minDescriptionWidth = 8;
        if (availableWidth < minDisplayWidth + gapWidth + minDescriptionWidth) {
            return null;
        }

        var descriptionVisibleWidth = TerminalText.visibleWidth(description);
        var preferredDescriptionWidth = Math.min(descriptionVisibleWidth, availableWidth - gapWidth - minDisplayWidth);
        if (preferredDescriptionWidth < minDescriptionWidth) {
            return null;
        }

        var maxDisplayWidth = Math.min(32, availableWidth - gapWidth - preferredDescriptionWidth);
        if (maxDisplayWidth < minDisplayWidth) {
            maxDisplayWidth = minDisplayWidth;
        }

        var truncatedDisplay = TerminalText.truncateToWidth(display, maxDisplayWidth, "");
        var actualDisplayWidth = TerminalText.visibleWidth(truncatedDisplay);
        var descriptionWidth = Math.min(descriptionVisibleWidth, availableWidth - actualDisplayWidth - gapWidth);
        if (descriptionWidth < minDescriptionWidth) {
            return null;
        }

        var truncatedDescription = TerminalText.truncateToWidth(description, descriptionWidth, "");
        var actualDescriptionWidth = TerminalText.visibleWidth(truncatedDescription);
        var spacing = " ".repeat(Math.max(gapWidth, availableWidth - actualDisplayWidth - actualDescriptionWidth));
        return new ItemLayout(truncatedDisplay, spacing, truncatedDescription);
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

    private record ItemLayout(
        String display,
        String spacing,
        String description
    ) {
    }
}
