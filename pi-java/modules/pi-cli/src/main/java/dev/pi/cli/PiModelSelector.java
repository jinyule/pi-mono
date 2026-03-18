package dev.pi.cli;

import dev.pi.tui.Component;
import dev.pi.tui.EditorAction;
import dev.pi.tui.EditorKeybindings;
import dev.pi.tui.Focusable;
import dev.pi.tui.Input;
import dev.pi.tui.SelectItem;
import dev.pi.tui.SelectList;
import dev.pi.tui.SelectListTheme;
import dev.pi.tui.TerminalText;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;

public final class PiModelSelector implements Component, Focusable {
    private static final String VALUE_DELIMITER = "\u0000";
    private static final SelectListTheme THEME = new SelectListTheme() {
        @Override
        public String selectedPrefix(String text) {
            return PiCliAnsi.accent(text);
        }

        @Override
        public String selectedText(String text) {
            return PiCliAnsi.accentBold(text);
        }

        @Override
        public String description(String text) {
            return PiCliAnsi.muted(text);
        }

        @Override
        public String scrollInfo(String text) {
            return PiCliAnsi.muted(text);
        }

        @Override
        public String noMatch(String text) {
            return PiCliAnsi.muted(text);
        }
    };

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
    private List<PiInteractiveSession.SelectableModel> visibleModels = List.of();
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
        lines.add(separatorLine(width));
        lines.add("");
        if (!scopedModels.isEmpty()) {
            lines.add(scopeSummary());
            lines.add(scopeHint());
        } else {
            lines.addAll(styleWrappedLines(
                "Only showing models with configured API keys (see README for details)",
                width,
                PiCliAnsi::warning
            ));
        }
        lines.add("");
        lines.addAll(search.render(width));
        lines.add("");
        if (visibleModels.isEmpty()) {
            lines.add(PiCliAnsi.muted("  No matching models"));
        } else {
            lines.addAll(models.render(width));
            var selectedModel = selectedModel();
            if (selectedModel != null) {
                lines.add("");
                lines.addAll(renderSelectedDetailPanel(selectedModel, width));
            }
        }
        lines.add("");
        lines.add(separatorLine(width));
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
        rebuildList();
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
        visibleModels = activeModels();
        var items = visibleModels.stream().map(PiModelSelector::toSelectItem).toList();
        models = new SelectList(items, Math.max(6, Math.min(12, Math.max(1, items.size()))), THEME);
        models.setOnSelectionChange(ignored -> requestRender.run());
        models.setOnSelect(item -> onSelect.accept(decodeIndex(item.value())));
        models.setOnCancel(onCancel);
        models.setSelectedIndex(selectedIndex(visibleModels));
    }

    private List<PiInteractiveSession.SelectableModel> activeModels() {
        return filterAndSortModels(scope == Scope.SCOPED ? scopedModels : allModels, search.getValue());
    }

    private String scopeSummary() {
        var all = scope == Scope.ALL ? PiCliAnsi.accent("all") : PiCliAnsi.muted("all");
        var scoped = scope == Scope.SCOPED ? PiCliAnsi.accent("scoped") : PiCliAnsi.muted("scoped");
        return PiCliAnsi.muted("Scope: ") + all + PiCliAnsi.muted(" | ") + scoped;
    }

    private String scopeHint() {
        return PiCliAnsi.dim(keyLabel(EditorAction.SESSION_SCOPE_TOGGLE)) + PiCliAnsi.muted(" scope (all/scoped)");
    }

    private static SelectItem toSelectItem(PiInteractiveSession.SelectableModel model) {
        var label = model.modelId() + (model.current() ? " " + PiCliAnsi.success("\u2713") : "");
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
            return 0;
        });
        return List.copyOf(sorted);
    }

    static List<PiInteractiveSession.SelectableModel> filterAndSortModels(
        List<PiInteractiveSession.SelectableModel> models,
        String query
    ) {
        if (query == null || query.isBlank()) {
            return sortModels(models);
        }

        var normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        var matches = new ArrayList<ModelSearchMatch>();
        for (var model : models) {
            var score = searchScore(model, normalizedQuery);
            if (score != null) {
                matches.add(new ModelSearchMatch(model, score));
            }
        }
        matches.sort((left, right) -> {
            var scoreCompare = Integer.compare(left.score(), right.score());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            if (left.model().current() && !right.model().current()) {
                return -1;
            }
            if (!left.model().current() && right.model().current()) {
                return 1;
            }
            var providerCompare = left.model().provider().compareToIgnoreCase(right.model().provider());
            if (providerCompare != 0) {
                return providerCompare;
            }
            return 0;
        });
        return matches.stream().map(ModelSearchMatch::model).toList();
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
        return "[" + model.provider() + "]";
    }

    private PiInteractiveSession.SelectableModel selectedModel() {
        var item = models.getSelectedItem();
        if (item == null) {
            return null;
        }
        var index = decodeIndex(item.value());
        return visibleModels.stream()
            .filter(model -> model.index() == index)
            .findFirst()
            .orElse(null);
    }

    private static List<String> renderSelectedDetailPanel(PiInteractiveSession.SelectableModel model, int width) {
        var lines = new ArrayList<String>();
        lines.add(separatorLine(width));
        lines.add(TerminalText.truncateToWidth(renderSelectedDetailHeader(model), width, "..."));
        for (var detailLine : selectedDetailLines(model)) {
            lines.add(TerminalText.truncateToWidth(detailLine, width, "..."));
        }
        lines.add(separatorLine(width));
        return List.copyOf(lines);
    }

    private static String renderSelectedDetailHeader(PiInteractiveSession.SelectableModel model) {
        var line = "  "
            + PiCliAnsi.bold("Selected:")
            + " "
            + PiCliAnsi.muted(model.provider() + "/")
            + PiCliAnsi.bold(model.modelId())
            + (model.current() ? " " + PiCliAnsi.success("\u2713") : "");
        return line;
    }

    private static String separatorLine(int width) {
        return PiCliAnsi.borderMuted("\u2500".repeat(Math.max(1, width)));
    }

    private static List<String> selectedDetailLines(PiInteractiveSession.SelectableModel model) {
        var lines = new ArrayList<String>();
        if (model.modelName() != null && !model.modelName().isBlank() && !model.modelName().equals(model.modelId())) {
            lines.add(detailField("Model Name", model.modelName()));
        }
        if (model.reasoning()) {
            lines.add(detailField("Thinking", model.thinkingLevel()));
        }
        if (model.contextWindow() > 0) {
            lines.add(detailField("Context", formatContextWindow(model.contextWindow())));
        }
        return List.copyOf(lines);
    }

    private static String detailField(String label, String value) {
        return "  " + PiCliAnsi.bold(label + ":") + " " + PiCliAnsi.muted(value);
    }

    private static Integer searchScore(PiInteractiveSession.SelectableModel model, String query) {
        Integer bestScore = null;
        for (var text : searchTexts(model)) {
            var score = searchTextScore(text, query);
            if (score != null && (bestScore == null || score < bestScore)) {
                bestScore = score;
            }
        }
        return bestScore;
    }

    private static List<String> searchTexts(PiInteractiveSession.SelectableModel model) {
        var texts = new ArrayList<String>();
        texts.add(model.provider() + "/" + model.modelId());
        texts.add(model.modelId());
        texts.add(model.provider());
        return texts;
    }

    private static Integer searchTextScore(String text, String query) {
        var normalizedText = text.toLowerCase(Locale.ROOT);
        if (normalizedText.equals(query)) {
            return 0;
        }
        if (normalizedText.startsWith(query)) {
            return 10;
        }
        var boundaryIndex = wordBoundaryIndex(normalizedText, query);
        if (boundaryIndex >= 0) {
            return 20 + boundaryIndex;
        }
        var containsIndex = normalizedText.indexOf(query);
        if (containsIndex >= 0) {
            return 40 + containsIndex;
        }
        var fuzzyScore = fuzzyTokenScore(query, normalizedText);
        if (fuzzyScore >= 0) {
            return 200 + fuzzyScore;
        }
        return null;
    }

    private static int wordBoundaryIndex(String text, String query) {
        var index = text.indexOf(query);
        while (index >= 0) {
            if (index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1))) {
                return index;
            }
            index = text.indexOf(query, index + 1);
        }
        return -1;
    }

    private static int fuzzySubsequenceScore(String query, String text) {
        var score = 0;
        var previousIndex = -1;
        for (var queryIndex = 0; queryIndex < query.length(); queryIndex += 1) {
            var match = text.indexOf(query.charAt(queryIndex), previousIndex + 1);
            if (match < 0) {
                return -1;
            }
            score += previousIndex < 0 ? match : match - previousIndex - 1;
            previousIndex = match;
        }
        return score;
    }

    private static int fuzzyTokenScore(String query, String text) {
        var bestScore = Integer.MAX_VALUE;
        for (var token : text.split("[^a-z0-9]+")) {
            if (token.isBlank()) {
                continue;
            }
            var score = fuzzySubsequenceScore(query, token);
            if (score >= 0 && score < bestScore) {
                bestScore = score;
            }
        }
        return bestScore == Integer.MAX_VALUE ? -1 : bestScore;
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

    private static String keyLabel(EditorAction action) {
        var keys = EditorKeybindings.global().getKeys(action);
        return keys.isEmpty() ? action.name() : keys.getFirst();
    }

    private static List<String> styleWrappedLines(String text, int width, UnaryOperator<String> style) {
        return TerminalText.wrapText(text, Math.max(1, width)).stream()
            .map(style)
            .toList();
    }

    private record ModelSearchMatch(
        PiInteractiveSession.SelectableModel model,
        int score
    ) {
    }
}
