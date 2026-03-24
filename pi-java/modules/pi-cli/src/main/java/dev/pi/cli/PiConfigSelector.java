package dev.pi.cli;

import dev.pi.tui.Component;
import dev.pi.tui.EditorAction;
import dev.pi.tui.EditorKeybindings;
import dev.pi.tui.Focusable;
import dev.pi.tui.Input;
import dev.pi.tui.TerminalText;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;

final class PiConfigSelector implements Component, Focusable {
    private final Input search = new Input();
    private final List<FlatEntry> allEntries;
    private final BiConsumer<PiConfigResolver.ResourceItem, Boolean> onToggle;
    private final Runnable onClose;
    private final Runnable requestRender;

    private List<FlatEntry> filteredEntries;
    private int selectedIndex;
    private boolean focused;

    PiConfigSelector(
        List<PiConfigResolver.ResourceGroup> groups,
        BiConsumer<PiConfigResolver.ResourceItem, Boolean> onToggle,
        Runnable onClose,
        Runnable requestRender
    ) {
        this.onToggle = Objects.requireNonNull(onToggle, "onToggle");
        this.onClose = Objects.requireNonNull(onClose, "onClose");
        this.requestRender = Objects.requireNonNull(requestRender, "requestRender");
        this.allEntries = flatten(groups == null ? List.of() : groups);
        this.filteredEntries = new ArrayList<>(allEntries);
        this.selectedIndex = firstItemIndex(filteredEntries);
        this.search.setFocused(true);
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

    @Override
    public List<String> render(int width) {
        var safeWidth = Math.max(1, width);
        var lines = new ArrayList<String>();
        lines.add(headerLine(safeWidth));
        lines.add(PiCliAnsi.muted("Type to filter resources"));
        lines.add("");
        lines.addAll(search.render(safeWidth));
        lines.add("");

        if (filteredEntries.isEmpty()) {
            lines.add(PiCliAnsi.muted("  No resources found"));
            lines.add("");
            lines.add(hintLine(safeWidth));
            return List.copyOf(lines);
        }

        var visibleItems = 15;
        var startIndex = Math.max(0, Math.min(selectedIndex - Math.floorDiv(visibleItems, 2), filteredEntries.size() - visibleItems));
        var endIndex = Math.min(filteredEntries.size(), startIndex + visibleItems);
        for (var index = startIndex; index < endIndex; index += 1) {
            var entry = filteredEntries.get(index);
            lines.add(renderEntry(entry, index == selectedIndex, safeWidth));
        }

        if (startIndex > 0 || endIndex < filteredEntries.size()) {
            lines.add(PiCliAnsi.dim("  (" + (selectedIndex + 1) + "/" + filteredEntries.size() + ")"));
        }
        lines.add("");
        lines.add(hintLine(safeWidth));
        return List.copyOf(lines);
    }

    @Override
    public void handleInput(String data) {
        var keybindings = EditorKeybindings.global();
        if (keybindings.matches(data, EditorAction.CURSOR_UP)) {
            selectedIndex = moveSelection(-1);
            requestRender.run();
            return;
        }
        if (keybindings.matches(data, EditorAction.CURSOR_DOWN)) {
            selectedIndex = moveSelection(1);
            requestRender.run();
            return;
        }
        if (keybindings.matches(data, EditorAction.SELECT_CANCEL) || "\u0003".equals(data)) {
            onClose.run();
            return;
        }
        if (" ".equals(data) || keybindings.matches(data, EditorAction.SUBMIT)) {
            var entry = selectedEntry();
            if (entry != null && entry.item() != null) {
                var updatedItem = new PiConfigResolver.ResourceItem(
                    entry.item().path(),
                    !entry.item().enabled(),
                    entry.item().resourceType(),
                    entry.item().scope(),
                    entry.item().source(),
                    entry.item().packageRoot()
                );
                onToggle.accept(entry.item(), updatedItem.enabled());
                replaceEntry(new FlatEntry(entry.kind(), entry.groupLabel(), entry.sectionLabel(), updatedItem));
                requestRender.run();
            }
            return;
        }

        search.handleInput(data);
        applyFilter(search.getValue());
        requestRender.run();
    }

    private String headerLine(int width) {
        var title = PiCliAnsi.bold("Resource Configuration");
        var hint = PiCliKeyHints.rawHint("space", "toggle")
            + PiCliAnsi.muted(" · ")
            + PiCliKeyHints.editorHint(EditorAction.SELECT_CANCEL, "close");
        var spacing = Math.max(1, width - TerminalText.visibleWidth(title) - TerminalText.visibleWidth(hint));
        return TerminalText.truncateToWidth(title + " ".repeat(spacing) + hint, width, "");
    }

    private String hintLine(int width) {
        var hint = PiCliAnsi.muted("  ")
            + PiCliKeyHints.editorHint(EditorAction.CURSOR_UP, "move")
            + PiCliAnsi.muted(" · ")
            + PiCliKeyHints.rawHint("space", "toggle")
            + PiCliAnsi.muted(" · ")
            + PiCliKeyHints.editorHint(EditorAction.SELECT_CANCEL, "close");
        return TerminalText.truncateToWidth(hint, width, "");
    }

    private String renderEntry(FlatEntry entry, boolean selected, int width) {
        return switch (entry.kind()) {
            case GROUP -> TerminalText.truncateToWidth("  " + PiCliAnsi.accentBold(entry.groupLabel()), width, "");
            case SECTION -> TerminalText.truncateToWidth("    " + PiCliAnsi.muted(entry.sectionLabel()), width, "");
            case ITEM -> renderItem(entry.item(), selected, width);
        };
    }

    private String renderItem(PiConfigResolver.ResourceItem item, boolean selected, int width) {
        var cursor = selected ? PiCliAnsi.accent("\u2192 ") : "  ";
        var checkbox = item.enabled() ? PiCliAnsi.success("[x]") : PiCliAnsi.dim("[ ]");
        var label = selected ? PiCliAnsi.bold(item.displayName()) : item.displayName();
        return TerminalText.truncateToWidth("  " + cursor + checkbox + " " + label, width, "...");
    }

    private void applyFilter(String query) {
        var normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            filteredEntries = new ArrayList<>(allEntries);
            selectedIndex = firstItemIndex(filteredEntries);
            return;
        }

        var next = new ArrayList<FlatEntry>();
        FlatEntry groupEntry = null;
        FlatEntry sectionEntry = null;
        var groupAdded = false;
        var sectionAdded = false;

        for (var entry : allEntries) {
            switch (entry.kind()) {
                case GROUP -> {
                    groupEntry = entry;
                    sectionEntry = null;
                    groupAdded = false;
                    sectionAdded = false;
                }
                case SECTION -> {
                    sectionEntry = entry;
                    sectionAdded = false;
                }
                case ITEM -> {
                    var haystack = (
                        entry.item().displayName()
                            + " "
                            + entry.item().resourceType().label()
                            + " "
                            + entry.item().path()
                    ).toLowerCase(Locale.ROOT);
                    if (!haystack.contains(normalized)) {
                        continue;
                    }
                    if (!groupAdded && groupEntry != null) {
                        next.add(groupEntry);
                        groupAdded = true;
                    }
                    if (!sectionAdded && sectionEntry != null) {
                        next.add(sectionEntry);
                        sectionAdded = true;
                    }
                    next.add(entry);
                }
            }
        }

        filteredEntries = next;
        selectedIndex = firstItemIndex(filteredEntries);
    }

    private int moveSelection(int delta) {
        if (filteredEntries.isEmpty()) {
            return 0;
        }
        var next = selectedIndex;
        do {
            next = (next + delta + filteredEntries.size()) % filteredEntries.size();
        } while (filteredEntries.get(next).kind() != EntryKind.ITEM && next != selectedIndex);
        return next;
    }

    private FlatEntry selectedEntry() {
        if (filteredEntries.isEmpty() || selectedIndex < 0 || selectedIndex >= filteredEntries.size()) {
            return null;
        }
        return filteredEntries.get(selectedIndex);
    }

    private void replaceEntry(FlatEntry updated) {
        for (var index = 0; index < allEntries.size(); index += 1) {
            var existing = allEntries.get(index);
            if (existing.kind() == EntryKind.ITEM && existing.item().path().equals(updated.item().path())) {
                allEntries.set(index, updated);
            }
        }
        for (var index = 0; index < filteredEntries.size(); index += 1) {
            var existing = filteredEntries.get(index);
            if (existing.kind() == EntryKind.ITEM && existing.item().path().equals(updated.item().path())) {
                filteredEntries.set(index, updated);
            }
        }
    }

    private static int firstItemIndex(List<FlatEntry> entries) {
        for (var index = 0; index < entries.size(); index += 1) {
            if (entries.get(index).kind() == EntryKind.ITEM) {
                return index;
            }
        }
        return 0;
    }

    private static List<FlatEntry> flatten(List<PiConfigResolver.ResourceGroup> groups) {
        var entries = new ArrayList<FlatEntry>();
        for (var group : groups) {
            entries.add(new FlatEntry(EntryKind.GROUP, group.label(), null, null));
            for (var section : group.sections()) {
                entries.add(new FlatEntry(EntryKind.SECTION, group.label(), section.type().label(), null));
                for (var item : section.items()) {
                    entries.add(new FlatEntry(EntryKind.ITEM, group.label(), section.type().label(), item));
                }
            }
        }
        return entries;
    }

    private enum EntryKind {
        GROUP,
        SECTION,
        ITEM
    }

    private record FlatEntry(
        EntryKind kind,
        String groupLabel,
        String sectionLabel,
        PiConfigResolver.ResourceItem item
    ) {
    }
}
