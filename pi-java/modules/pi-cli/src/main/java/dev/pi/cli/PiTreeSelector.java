package dev.pi.cli;

import dev.pi.session.SessionEntry;
import dev.pi.session.SessionTreeNode;
import dev.pi.tui.Component;
import dev.pi.tui.EditorAction;
import dev.pi.tui.EditorKeybindings;
import dev.pi.tui.Focusable;
import dev.pi.tui.Input;
import dev.pi.tui.SelectItem;
import dev.pi.tui.SelectList;
import dev.pi.tui.SelectListTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class PiTreeSelector implements Component, Focusable {
    private static final String VALUE_DELIMITER = "\u0000";
    private static final SelectListTheme THEME = new SelectListTheme() {
        @Override
        public String selectedPrefix(String text) {
            return text;
        }

        @Override
        public String selectedText(String text) {
            return text;
        }

        @Override
        public String description(String text) {
            return text;
        }

        @Override
        public String scrollInfo(String text) {
            return text;
        }

        @Override
        public String noMatch(String text) {
            return text;
        }
    };

    private final Input search = new Input();
    private final SelectList entries;
    private final Runnable requestRender;
    private boolean focused;

    public PiTreeSelector(
        List<SessionTreeNode> tree,
        String currentLeafId,
        Consumer<String> onSelect,
        Runnable onCancel,
        Runnable requestRender
    ) {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(onSelect, "onSelect");
        Objects.requireNonNull(onCancel, "onCancel");
        this.requestRender = Objects.requireNonNull(requestRender, "requestRender");

        var flattened = flatten(tree, currentLeafId);
        this.entries = new SelectList(
            flattened.stream().map(FlatNode::item).toList(),
            Math.max(6, Math.min(12, Math.max(1, flattened.size()))),
            THEME
        );
        this.search.setFocused(true);
        this.entries.setOnSelectionChange(ignored -> requestRender.run());
        this.entries.setOnSelect(item -> onSelect.accept(decodeId(item.value())));
        this.entries.setOnCancel(onCancel);
        this.entries.setSelectedIndex(selectedIndex(flattened));
    }

    @Override
    public List<String> render(int width) {
        var lines = new ArrayList<String>();
        lines.add("Navigate session tree");
        lines.add("Type to filter. Enter selects. Esc cancels.");
        lines.add("");
        lines.addAll(search.render(width));
        lines.add("");
        lines.addAll(entries.render(width));
        return List.copyOf(lines);
    }

    @Override
    public void handleInput(String data) {
        var keybindings = EditorKeybindings.global();
        if (
            keybindings.matches(data, EditorAction.CURSOR_UP) ||
            keybindings.matches(data, EditorAction.CURSOR_DOWN) ||
            keybindings.matches(data, EditorAction.SUBMIT) ||
            keybindings.matches(data, EditorAction.SELECT_CANCEL)
        ) {
            entries.handleInput(data);
            return;
        }

        search.handleInput(data);
        entries.setFilter(search.getValue());
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

    private static List<FlatNode> flatten(List<SessionTreeNode> roots, String currentLeafId) {
        var flattened = new ArrayList<FlatNode>();
        for (var index = 0; index < roots.size(); index += 1) {
            flattenNode(roots.get(index), List.of(), index == roots.size() - 1, currentLeafId, flattened);
        }
        return List.copyOf(flattened);
    }

    private static void flattenNode(
        SessionTreeNode node,
        List<Boolean> ancestry,
        boolean isLast,
        String currentLeafId,
        List<FlatNode> flattened
    ) {
        var path = new ArrayList<Boolean>(ancestry);
        path.add(isLast);

        if (isVisible(node.entry())) {
            flattened.add(new FlatNode(
                node.entry().id(),
                Objects.equals(currentLeafId, node.entry().id()),
                toSelectItem(node, path, Objects.equals(currentLeafId, node.entry().id()))
            ));
        }

        for (var index = 0; index < node.children().size(); index += 1) {
            flattenNode(node.children().get(index), path, index == node.children().size() - 1, currentLeafId, flattened);
        }
    }

    private static SelectItem toSelectItem(SessionTreeNode node, List<Boolean> path, boolean active) {
        var searchableText = searchableText(node);
        var label = treePrefix(path) + searchableText + (active ? " ← active" : "");
        return new SelectItem(encodeValue(searchableText, node.entry().id()), label, null);
    }

    private static boolean isVisible(SessionEntry entry) {
        return switch (entry) {
            case SessionEntry.MessageEntry ignored -> true;
            case SessionEntry.CompactionEntry ignored -> true;
            case SessionEntry.BranchSummaryEntry ignored -> true;
            case SessionEntry.CustomMessageEntry ignored -> true;
            default -> false;
        };
    }

    private static String searchableText(SessionTreeNode node) {
        var labelPrefix = node.label() == null || node.label().isBlank() ? "" : "[" + node.label().trim() + "] ";
        return labelPrefix + switch (node.entry()) {
            case SessionEntry.MessageEntry messageEntry -> renderMessageEntry(messageEntry);
            case SessionEntry.CompactionEntry compactionEntry -> "[compaction] " + summarize(compactionEntry.summary());
            case SessionEntry.BranchSummaryEntry branchSummaryEntry -> "[branch] " + summarize(branchSummaryEntry.summary());
            case SessionEntry.CustomMessageEntry customMessageEntry ->
                "[" + customMessageEntry.customType() + "] " + summarize(extractContentText(customMessageEntry.content()));
            default -> node.entry().getClass().getSimpleName();
        };
    }

    private static String renderMessageEntry(SessionEntry.MessageEntry entry) {
        var message = entry.message();
        var role = message.path("role").asText("message");
        return switch (role) {
            case "user" -> "user: " + summarize(extractContentText(message.path("content")));
            case "assistant" -> "assistant: " + summarize(extractContentText(message.path("content")));
            case "toolResult" -> "tool: " + summarize(extractContentText(message.path("content")));
            default -> role + ": " + summarize(extractContentText(message.path("content")));
        };
    }

    private static String extractContentText(com.fasterxml.jackson.databind.JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText("");
        }
        if (!content.isArray()) {
            return "";
        }

        var parts = new ArrayList<String>();
        for (var item : content) {
            var type = item.path("type").asText("");
            switch (type) {
                case "text" -> appendIfPresent(parts, item.path("text").asText(""));
                case "thinking" -> appendIfPresent(parts, "Thinking: " + item.path("thinking").asText(""));
                case "toolCall" -> appendIfPresent(
                    parts,
                    "Tool call %s(%s)".formatted(item.path("name").asText("tool"), item.path("arguments").asText(""))
                );
                default -> {
                }
            }
        }
        return String.join(" ", parts);
    }

    private static void appendIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }

    private static String summarize(String value) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }
        return value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static String treePrefix(List<Boolean> path) {
        if (path.isEmpty()) {
            return "";
        }
        var prefix = new StringBuilder();
        for (var index = 0; index < path.size() - 1; index += 1) {
            prefix.append(path.get(index) ? "   " : "│  ");
        }
        prefix.append(path.getLast() ? "└─ " : "├─ ");
        return prefix.toString();
    }

    private static int selectedIndex(List<FlatNode> flattened) {
        for (var index = 0; index < flattened.size(); index += 1) {
            if (flattened.get(index).active()) {
                return index;
            }
        }
        return 0;
    }

    private static String encodeValue(String searchableText, String entryId) {
        return searchableText + VALUE_DELIMITER + entryId;
    }

    private static String decodeId(String value) {
        var delimiter = value.lastIndexOf(VALUE_DELIMITER);
        return delimiter >= 0 ? value.substring(delimiter + VALUE_DELIMITER.length()) : value;
    }

    private record FlatNode(
        String entryId,
        boolean active,
        SelectItem item
    ) {
    }
}
