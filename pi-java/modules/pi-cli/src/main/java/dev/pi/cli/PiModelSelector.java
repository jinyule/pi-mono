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
    private static final int MAX_VISIBLE_MODELS = 10;
    private static final SelectListTheme THEME = new SelectListTheme() {
        @Override
        public String selectedPrefix(String text) {
            return PiCliAnsi.accent(text);
        }

        @Override
        public String selectedText(String text) {
            return PiCliAnsi.accent(text);
        }

        @Override
        public boolean rightAlignDescription() {
            return false;
        }

        @Override
        public String description(String text) {
            return PiCliAnsi.muted(text);
        }

        @Override
        public String compactDescription(String text, int width) {
            return truncateMetadata(text, width);
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
    private int selectedIndex;
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
            lines.add(truncateLine(scopeSummary(), width));
            lines.add(truncateLine(scopeHint(), width));
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
            var selectedDetailLine = selectedModel == null ? null : selectedDetailLine(selectedModel);
            if (selectedDetailLine != null) {
                lines.add("");
                lines.add(TerminalText.truncateToWidth(selectedDetailLine, width, "..."));
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
            selectedIndex = 0;
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
        models = new SelectList(items, Math.min(MAX_VISIBLE_MODELS, Math.max(1, items.size())), THEME);
        selectedIndex = Math.max(0, Math.min(selectedIndex, Math.max(0, items.size() - 1)));
        models.setOnSelectionChange(item -> {
            selectedIndex = Math.max(0, items.indexOf(item));
            requestRender.run();
        });
        models.setOnSelect(item -> onSelect.accept(decodeIndex(item.value())));
        models.setOnCancel(onCancel);
        models.setSelectedIndex(selectedIndex);
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
        return PiCliKeyHints.editorHint(EditorAction.SESSION_SCOPE_TOGGLE, "scope") + PiCliAnsi.muted(" (all/scoped)");
    }

    private static SelectItem toSelectItem(PiInteractiveSession.SelectableModel model) {
        return new SelectItem(encodeValue(model.modelId(), model.index()), model.modelId(), metadata(model));
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

        var tokens = searchTokens(query);
        if (tokens.isEmpty()) {
            return sortModels(models);
        }
        var matches = new ArrayList<ModelSearchMatch>();
        for (var model : models) {
            var score = searchScore(model, tokens);
            if (score != null) {
                matches.add(new ModelSearchMatch(model, score));
            }
        }
        matches.sort((left, right) -> Double.compare(left.score(), right.score()));
        return matches.stream().map(ModelSearchMatch::model).toList();
    }

    private static String encodeValue(String label, int index) {
        return label + VALUE_DELIMITER + index;
    }

    private static int decodeIndex(String value) {
        var delimiter = value.lastIndexOf(VALUE_DELIMITER);
        return delimiter >= 0 ? Integer.parseInt(value.substring(delimiter + VALUE_DELIMITER.length())) : Integer.parseInt(value);
    }

    private static String metadata(PiInteractiveSession.SelectableModel model) {
        return "[" + model.provider() + "]" + (model.current() ? " " + PiCliAnsi.success("\u2713") : "");
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

    private static String separatorLine(int width) {
        return PiCliAnsi.borderMuted("\u2500".repeat(Math.max(1, width)));
    }

    private static String selectedDetailLine(PiInteractiveSession.SelectableModel model) {
        if (model.modelName() == null || model.modelName().isBlank()) {
            return null;
        }
        return PiCliAnsi.muted("  Model Name: " + model.modelName());
    }

    private static String truncateMetadata(String text, int width) {
        if (text == null || text.isBlank() || width <= 0) {
            return "";
        }
        if (TerminalText.visibleWidth(text) <= width) {
            return text;
        }
        var splitIndex = text.lastIndexOf(' ');
        if (splitIndex < 0) {
            return TerminalText.truncateToWidth(text, width, "");
        }
        var head = text.substring(0, splitIndex).stripTrailing();
        var tail = text.substring(splitIndex + 1).trim();
        if (tail.isEmpty()) {
            return TerminalText.truncateToWidth(text, width, "");
        }
        var tailWidth = TerminalText.visibleWidth(tail);
        if (tailWidth >= width) {
            return TerminalText.truncateToWidth(tail, width, "");
        }
        var headWidth = width - tailWidth - 1;
        if (headWidth < 3) {
            return tail;
        }
        var truncatedHead = TerminalText.truncateToWidth(head, headWidth, "...");
        if (truncatedHead.isBlank()) {
            return tail;
        }
        return truncatedHead + " " + tail;
    }

    private static String truncateLine(String text, int width) {
        return TerminalText.truncateToWidth(text, Math.max(1, width), "...");
    }

    private static Double searchScore(PiInteractiveSession.SelectableModel model, List<String> tokens) {
        var totalScore = 0.0;
        var text = searchText(model);
        for (var token : tokens) {
            var match = fuzzyMatch(token, text);
            if (!match.matches()) {
                return null;
            }
            totalScore += match.score();
        }
        return totalScore;
    }

    private static List<String> searchTokens(String query) {
        var tokens = new ArrayList<String>();
        for (var token : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String searchText(PiInteractiveSession.SelectableModel model) {
        return model.modelId() + " " + model.provider();
    }

    private static FuzzySearchMatch fuzzyMatch(String query, String text) {
        var normalizedQuery = query.toLowerCase(Locale.ROOT);
        var normalizedText = text.toLowerCase(Locale.ROOT);
        var primaryMatch = fuzzyMatchNormalized(normalizedQuery, normalizedText);
        if (primaryMatch.matches()) {
            return primaryMatch;
        }
        var swappedQuery = swapAlphaNumericQuery(normalizedQuery);
        if (swappedQuery == null) {
            return primaryMatch;
        }
        var swappedMatch = fuzzyMatchNormalized(swappedQuery, normalizedText);
        if (!swappedMatch.matches()) {
            return primaryMatch;
        }
        return new FuzzySearchMatch(true, swappedMatch.score() + 5.0);
    }

    private static FuzzySearchMatch fuzzyMatchNormalized(String query, String text) {
        if (query.isEmpty()) {
            return new FuzzySearchMatch(true, 0.0);
        }
        if (query.length() > text.length()) {
            return new FuzzySearchMatch(false, 0.0);
        }

        var queryIndex = 0;
        var score = 0.0;
        var lastMatchIndex = -1;
        var consecutiveMatches = 0;

        for (var textIndex = 0; textIndex < text.length() && queryIndex < query.length(); textIndex += 1) {
            if (text.charAt(textIndex) != query.charAt(queryIndex)) {
                continue;
            }
            var isWordBoundary = textIndex == 0 || isWordBoundary(text.charAt(textIndex - 1));
            if (lastMatchIndex == textIndex - 1) {
                consecutiveMatches += 1;
                score -= consecutiveMatches * 5.0;
            } else {
                consecutiveMatches = 0;
                if (lastMatchIndex >= 0) {
                    score += (textIndex - lastMatchIndex - 1) * 2.0;
                }
            }
            if (isWordBoundary) {
                score -= 10.0;
            }
            score += textIndex * 0.1;
            lastMatchIndex = textIndex;
            queryIndex += 1;
        }

        if (queryIndex < query.length()) {
            return new FuzzySearchMatch(false, 0.0);
        }
        return new FuzzySearchMatch(true, score);
    }

    private static boolean isWordBoundary(char character) {
        return Character.isWhitespace(character)
            || character == '-'
            || character == '_'
            || character == '.'
            || character == '/'
            || character == ':';
    }

    private static String swapAlphaNumericQuery(String query) {
        var splitIndex = leadingRunLength(query);
        if (splitIndex <= 0 || splitIndex >= query.length()) {
            return null;
        }
        var left = query.substring(0, splitIndex);
        var right = query.substring(splitIndex);
        if (isLowercaseLetters(left) && isDigits(right)) {
            return right + left;
        }
        if (isDigits(left) && isLowercaseLetters(right)) {
            return right + left;
        }
        return null;
    }

    private static int leadingRunLength(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        var firstIsDigit = Character.isDigit(text.charAt(0));
        var firstIsLetter = isLowercaseLetter(text.charAt(0));
        if (!firstIsDigit && !firstIsLetter) {
            return 0;
        }
        var index = 1;
        while (index < text.length()) {
            var character = text.charAt(index);
            if (firstIsDigit && Character.isDigit(character)) {
                index += 1;
                continue;
            }
            if (firstIsLetter && isLowercaseLetter(character)) {
                index += 1;
                continue;
            }
            break;
        }
        return index;
    }

    private static boolean isDigits(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (var index = 0; index < text.length(); index += 1) {
            if (!Character.isDigit(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLowercaseLetters(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (var index = 0; index < text.length(); index += 1) {
            if (!isLowercaseLetter(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLowercaseLetter(char character) {
        return character >= 'a' && character <= 'z';
    }
    private static List<String> styleWrappedLines(String text, int width, UnaryOperator<String> style) {
        return TerminalText.wrapText(text, Math.max(1, width)).stream()
            .map(style)
            .toList();
    }

    private record ModelSearchMatch(
        PiInteractiveSession.SelectableModel model,
        double score
    ) {
    }

    private record FuzzySearchMatch(
        boolean matches,
        double score
    ) {
    }
}
