package dev.pi.cli;

import dev.pi.session.SessionManager;
import dev.pi.session.SessionInfo;
import dev.pi.tui.Component;
import dev.pi.tui.EditorAction;
import dev.pi.tui.EditorKeybindings;
import dev.pi.tui.Focusable;
import dev.pi.tui.Input;
import dev.pi.tui.ProcessTerminal;
import dev.pi.tui.SelectItem;
import dev.pi.tui.SelectList;
import dev.pi.tui.SelectListTheme;
import dev.pi.tui.Terminal;
import dev.pi.tui.Tui;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

public final class PiSessionPicker implements PiCliSessionResolver.SessionPicker {
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

    private final Terminal terminal;

    public PiSessionPicker() {
        this(new ProcessTerminal());
    }

    public PiSessionPicker(Terminal terminal) {
        this.terminal = Objects.requireNonNull(terminal, "terminal");
    }

    static List<SessionInfo> filterAndSortSessions(List<SessionInfo> sessions, String filter, SortMode sortMode, NameFilter nameFilter) {
        var visibleSessions = new ArrayList<>(sessions.stream().filter(session -> nameFilter == NameFilter.ALL || hasName(session)).toList());
        var parsed = parseSearchQuery(filter);
        if (parsed.error() != null) {
            return List.of();
        }
        if (parsed.isEmpty()) {
            return List.copyOf(visibleSessions);
        }
        if (sortMode == SortMode.RECENT) {
            return visibleSessions.stream()
                .filter(session -> matchSession(session, parsed).matches())
                .toList();
        }
        var matches = new ArrayList<SessionMatch>();
        for (var session : visibleSessions) {
            var match = matchSession(session, parsed);
            if (!match.matches()) {
                continue;
            }
            matches.add(new SessionMatch(session, match.score()));
        }
        matches.sort(
            Comparator.comparingDouble(SessionMatch::score)
                .thenComparing(SessionMatch::session, Comparator.comparing(SessionInfo::modified).reversed())
        );
        return matches.stream().map(SessionMatch::session).toList();
    }

    @Override
    public Path pick(SessionLoader currentLoader, SessionLoader allLoader) {
        return pickInternal(currentLoader, allLoader, null, null);
    }

    public Path pick(List<SessionInfo> currentSessions, List<SessionInfo> allSessions) {
        Objects.requireNonNull(currentSessions, "currentSessions");
        Objects.requireNonNull(allSessions, "allSessions");
        return pickInternal(progress -> currentSessions, progress -> allSessions, currentSessions, allSessions);
    }

    private Path pickInternal(
        SessionLoader currentLoader,
        SessionLoader allLoader,
        List<SessionInfo> initialCurrentSessions,
        List<SessionInfo> initialAllSessions
    ) {
        Objects.requireNonNull(currentLoader, "currentLoader");
        Objects.requireNonNull(allLoader, "allLoader");
        var selection = new CompletableFuture<Path>();
        var tui = new Tui(terminal, true);
        var component = new PickerComponent(
            currentLoader,
            allLoader,
            path -> {
                if (selection.complete(path)) {
                    tui.stop();
                }
            },
            () -> {
                if (selection.complete(null)) {
                    tui.stop();
                }
            },
            () -> {
                if (selection.completeExceptionally(new IllegalStateException("No sessions available to resume"))) {
                    tui.stop();
                }
            },
            tui::requestRender
        );
        if (initialCurrentSessions != null || initialAllSessions != null) {
            component.primeSessions(initialCurrentSessions, initialAllSessions);
        }
        tui.addChild(component);
        tui.setFocus(component);
        tui.start();
        component.start();
        try {
            return selection.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    public Path pick(List<SessionInfo> sessions) {
        return pick(sessions, sessions);
    }

    static String displayLabel(SessionInfo session) {
        return session.name() == null || session.name().isBlank() ? session.firstMessage() : session.name();
    }

    static boolean hasName(SessionInfo session) {
        return session.name() != null && !session.name().isBlank();
    }

    private static ParsedSearchQuery parseSearchQuery(String query) {
        var trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return new ParsedSearchQuery(SearchMode.TOKENS, List.of(), null, null);
        }
        if (trimmed.startsWith("re:")) {
            var pattern = trimmed.substring(3).trim();
            if (pattern.isEmpty()) {
                return new ParsedSearchQuery(SearchMode.REGEX, List.of(), null, "Empty regex");
            }
            try {
                return new ParsedSearchQuery(SearchMode.REGEX, List.of(), Pattern.compile(pattern, Pattern.CASE_INSENSITIVE), null);
            } catch (java.util.regex.PatternSyntaxException exception) {
                return new ParsedSearchQuery(SearchMode.REGEX, List.of(), null, exception.getMessage());
            }
        }

        var tokens = new ArrayList<SearchToken>();
        var buffer = new StringBuilder();
        var inQuote = false;
        var hadUnclosedQuote = false;
        for (var index = 0; index < trimmed.length(); index += 1) {
            var ch = trimmed.charAt(index);
            if (ch == '"') {
                if (inQuote) {
                    flushToken(tokens, buffer, SearchTokenKind.PHRASE);
                    inQuote = false;
                } else {
                    flushToken(tokens, buffer, SearchTokenKind.FUZZY);
                    inQuote = true;
                }
                continue;
            }
            if (!inQuote && Character.isWhitespace(ch)) {
                flushToken(tokens, buffer, SearchTokenKind.FUZZY);
                continue;
            }
            buffer.append(ch);
        }
        if (inQuote) {
            hadUnclosedQuote = true;
        }
        if (hadUnclosedQuote) {
            return new ParsedSearchQuery(
                SearchMode.TOKENS,
                List.of(trimmed.split("\\s+")).stream()
                    .map(String::trim)
                    .filter(token -> !token.isEmpty())
                    .map(token -> new SearchToken(SearchTokenKind.FUZZY, token))
                    .toList(),
                null,
                null
            );
        }
        flushToken(tokens, buffer, SearchTokenKind.FUZZY);
        return new ParsedSearchQuery(SearchMode.TOKENS, List.copyOf(tokens), null, null);
    }

    private static void flushToken(List<SearchToken> tokens, StringBuilder buffer, SearchTokenKind defaultKind) {
        var value = buffer.toString().trim();
        buffer.setLength(0);
        if (!value.isEmpty()) {
            tokens.add(new SearchToken(defaultKind, value));
        }
    }

    private static MatchResult matchSession(SessionInfo session, ParsedSearchQuery parsed) {
        var text = getSearchText(session);
        if (parsed.mode() == SearchMode.REGEX) {
            if (parsed.regex() == null) {
                return new MatchResult(false, 0);
            }
            var matcher = parsed.regex().matcher(text);
            if (!matcher.find()) {
                return new MatchResult(false, 0);
            }
            return new MatchResult(true, matcher.start() * 0.1);
        }
        if (parsed.tokens().isEmpty()) {
            return new MatchResult(true, 0);
        }

        var score = 0.0;
        String normalizedText = null;
        for (var token : parsed.tokens()) {
            if (token.kind() == SearchTokenKind.PHRASE) {
                if (normalizedText == null) {
                    normalizedText = normalizeWhitespaceLower(text);
                }
                var phrase = normalizeWhitespaceLower(token.value());
                if (phrase.isEmpty()) {
                    continue;
                }
                var index = normalizedText.indexOf(phrase);
                if (index < 0) {
                    return new MatchResult(false, 0);
                }
                score += index * 0.1;
                continue;
            }

            var fuzzy = fuzzyMatch(token.value(), text);
            if (!fuzzy.matches()) {
                return new MatchResult(false, 0);
            }
            score += fuzzy.score();
        }
        return new MatchResult(true, score);
    }

    private static FuzzyScore fuzzyMatch(String query, String text) {
        var normalizedQuery = query.toLowerCase(Locale.ROOT);
        var normalizedText = text.toLowerCase(Locale.ROOT);
        var primaryMatch = fuzzyMatchNormalized(normalizedQuery, normalizedText);
        if (primaryMatch.matches()) {
            return primaryMatch;
        }

        var swappedQuery = swapAlphaNumericQuery(normalizedQuery);
        if (swappedQuery.isEmpty()) {
            return primaryMatch;
        }

        var swappedMatch = fuzzyMatchNormalized(swappedQuery, normalizedText);
        if (!swappedMatch.matches()) {
            return primaryMatch;
        }

        return new FuzzyScore(true, swappedMatch.score() + 5);
    }

    private static FuzzyScore fuzzyMatchNormalized(String normalizedQuery, String normalizedText) {
        if (normalizedQuery.isEmpty()) {
            return new FuzzyScore(true, 0);
        }
        if (normalizedQuery.length() > normalizedText.length()) {
            return new FuzzyScore(false, 0);
        }

        var queryIndex = 0;
        var score = 0.0;
        var lastMatchIndex = -1;
        var consecutiveMatches = 0;

        for (var index = 0; index < normalizedText.length() && queryIndex < normalizedQuery.length(); index += 1) {
            if (normalizedText.charAt(index) != normalizedQuery.charAt(queryIndex)) {
                continue;
            }

            var isWordBoundary = index == 0 || isWordBoundary(normalizedText.charAt(index - 1));
            if (lastMatchIndex == index - 1) {
                consecutiveMatches += 1;
                score -= consecutiveMatches * 5.0;
            } else {
                consecutiveMatches = 0;
                if (lastMatchIndex >= 0) {
                    score += (index - lastMatchIndex - 1) * 2.0;
                }
            }

            if (isWordBoundary) {
                score -= 10.0;
            }

            score += index * 0.1;
            lastMatchIndex = index;
            queryIndex += 1;
        }

        if (queryIndex < normalizedQuery.length()) {
            return new FuzzyScore(false, 0);
        }
        return new FuzzyScore(true, score);
    }

    private static boolean isWordBoundary(char ch) {
        return Character.isWhitespace(ch) || ch == '-' || ch == '_' || ch == '.' || ch == '/' || ch == ':';
    }

    private static String swapAlphaNumericQuery(String normalizedQuery) {
        var splitIndex = splitAlphaNumericBoundary(normalizedQuery, true);
        if (splitIndex >= 0) {
            return normalizedQuery.substring(splitIndex) + normalizedQuery.substring(0, splitIndex);
        }
        splitIndex = splitAlphaNumericBoundary(normalizedQuery, false);
        if (splitIndex >= 0) {
            return normalizedQuery.substring(splitIndex) + normalizedQuery.substring(0, splitIndex);
        }
        return "";
    }

    private static int splitAlphaNumericBoundary(String value, boolean lettersFirst) {
        if (value.isEmpty()) {
            return -1;
        }
        var index = 0;
        while (index < value.length() && matchesAlphaNumericSegment(value.charAt(index), lettersFirst)) {
            index += 1;
        }
        if (index == 0 || index == value.length()) {
            return -1;
        }
        while (index < value.length() && matchesAlphaNumericSegment(value.charAt(index), !lettersFirst)) {
            index += 1;
        }
        return index == value.length() ? findTransitionIndex(value, lettersFirst) : -1;
    }

    private static int findTransitionIndex(String value, boolean lettersFirst) {
        var index = 0;
        while (index < value.length() && matchesAlphaNumericSegment(value.charAt(index), lettersFirst)) {
            index += 1;
        }
        return index;
    }

    private static boolean matchesAlphaNumericSegment(char ch, boolean letters) {
        return letters ? ch >= 'a' && ch <= 'z' : ch >= '0' && ch <= '9';
    }

    private static String normalizeWhitespaceLower(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String getSearchText(SessionInfo session) {
        return "%s %s %s %s".formatted(
            session.id(),
            session.name() == null ? "" : session.name(),
            session.allMessagesText(),
            session.cwd()
        );
    }

    enum SortMode {
        RECENT("Recent"),
        RELEVANCE("Fuzzy"),
        THREADED("Threaded");

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }

    enum NameFilter {
        ALL("All"),
        NAMED("Named");

        private final String label;

        NameFilter(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }

    @FunctionalInterface
    public interface SessionLoader {
        List<SessionInfo> load(SessionLoadProgress progress) throws Exception;
    }

    @FunctionalInterface
    public interface SessionLoadProgress {
        void onProgress(int loaded, int total);
    }

    private enum SearchMode {
        TOKENS,
        REGEX
    }

    private enum SearchTokenKind {
        FUZZY,
        PHRASE
    }

    private record SearchToken(
        SearchTokenKind kind,
        String value
    ) {
    }

    private record ParsedSearchQuery(
        SearchMode mode,
        List<SearchToken> tokens,
        Pattern regex,
        String error
    ) {
        private boolean isEmpty() {
            return tokens.isEmpty() && regex == null;
        }
    }

    private record MatchResult(
        boolean matches,
        double score
    ) {
    }

    private record FuzzyScore(
        boolean matches,
        double score
    ) {
    }

    private record SessionMatch(
        SessionInfo session,
        double score
    ) {
    }

    private static final class PickerComponent implements Component, Focusable {
        private final Input search = new Input();
        private final Input rename = new Input();
        private final SessionLoader currentLoader;
        private final SessionLoader allLoader;
        private final java.util.function.Consumer<Path> onSelect;
        private final Runnable onCancel;
        private final Runnable onNoSessions;
        private final Runnable requestRender;
        private volatile List<SessionInfo> currentSessions;
        private volatile List<SessionInfo> allSessions;
        private SelectList sessions;
        private boolean focused;
        private String pendingDeletePath;
        private String renamingPath;
        private String renamingCurrentName;
        private Scope scope = Scope.CURRENT;
        private SortMode sortMode = SortMode.THREADED;
        private NameFilter nameFilter = NameFilter.ALL;
        private boolean showPath;
        private volatile boolean currentLoading;
        private volatile boolean allLoading;
        private volatile ProgressSnapshot currentProgress;
        private volatile ProgressSnapshot allProgress;
        private volatile String loadingError;

        private PickerComponent(
            SessionLoader currentLoader,
            SessionLoader allLoader,
            java.util.function.Consumer<Path> onSelect,
            Runnable onCancel,
            Runnable onNoSessions,
            Runnable requestRender
        ) {
            this.currentLoader = currentLoader;
            this.allLoader = allLoader;
            this.onSelect = onSelect;
            this.onCancel = onCancel;
            this.onNoSessions = onNoSessions;
            this.requestRender = requestRender;
            this.search.setFocused(true);
            rebuildSessionList();
        }

        @Override
        public List<String> render(int width) {
            if (renamingPath != null) {
                var lines = new ArrayList<String>();
                lines.add("Rename session");
                lines.add(renamingCurrentName == null || renamingCurrentName.isBlank()
                    ? "Enter new name. Enter saves. Esc cancels."
                    : "Current name: " + renamingCurrentName);
                lines.add("");
                lines.addAll(rename.render(width));
                lines.add("");
                lines.add("Enter saves. Esc cancels.");
                return List.copyOf(lines);
            }
            var lines = new java.util.ArrayList<String>();
            lines.add(scope == Scope.CURRENT ? "Resume session (Current folder)" : "Resume session (All)");
            lines.add(scopeSummary());
            if (pendingDeletePath != null) {
                lines.add("Delete session? [Enter] confirm · [Esc] cancel");
                lines.add("");
            } else {
                lines.add(
                    "%s sort(%s) · %s named(%s) · %s path(%s)"
                        .formatted(
                            keyHint(EditorAction.SESSION_SORT_TOGGLE),
                            sortMode.label(),
                            appKeyHint(PiAppAction.TOGGLE_SESSION_NAMED_FILTER),
                            nameFilter.label(),
                            keyHint(EditorAction.SESSION_PATH_TOGGLE),
                            showPath ? "on" : "off"
                        )
                );
                lines.add(
                    loadingError != null
                        ? "Failed to load sessions: " + loadingError
                        : "%s scope · re:<pattern> regex · \"phrase\" exact · %s delete · %s rename"
                            .formatted(
                                keyHint(EditorAction.SESSION_SCOPE_TOGGLE),
                                keyHint(EditorAction.SESSION_DELETE),
                                keyHint(EditorAction.SESSION_RENAME)
                            )
                );
            }
            lines.add("");
            lines.addAll(search.render(width));
            lines.add("");
            lines.addAll(sessions.render(width));
            return List.copyOf(lines);
        }

        @Override
        public void handleInput(String data) {
            var keybindings = EditorKeybindings.global();
            var appKeybindings = PiAppKeybindings.global();
            if (renamingPath != null) {
                if (keybindings.matches(data, EditorAction.SUBMIT)) {
                    renameSession();
                    return;
                }
                if (keybindings.matches(data, EditorAction.SELECT_CANCEL)) {
                    renamingPath = null;
                    renamingCurrentName = null;
                    rename.setValue("");
                    search.setFocused(true);
                    rename.setFocused(false);
                    requestRender.run();
                    return;
                }
                rename.handleInput(data);
                requestRender.run();
                return;
            }
            if (pendingDeletePath != null) {
                if (keybindings.matches(data, EditorAction.SUBMIT)) {
                    deletePendingSession();
                    return;
                }
                if (keybindings.matches(data, EditorAction.SELECT_CANCEL)) {
                    pendingDeletePath = null;
                    requestRender.run();
                }
                return;
            }
            if (keybindings.matches(data, EditorAction.SESSION_SCOPE_TOGGLE)) {
                toggleScope();
                return;
            }
            if (keybindings.matches(data, EditorAction.SESSION_SORT_TOGGLE)) {
                toggleSort();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.TOGGLE_SESSION_NAMED_FILTER)) {
                toggleNameFilter();
                return;
            }
            if (keybindings.matches(data, EditorAction.SESSION_PATH_TOGGLE)) {
                togglePath();
                return;
            }
            if (
                keybindings.matches(data, EditorAction.CURSOR_UP) ||
                keybindings.matches(data, EditorAction.CURSOR_DOWN) ||
                keybindings.matches(data, EditorAction.SUBMIT) ||
                keybindings.matches(data, EditorAction.SELECT_CANCEL)
            ) {
                sessions.handleInput(data);
                return;
            }
            if (keybindings.matches(data, EditorAction.SESSION_DELETE)) {
                var selected = sessions.getSelectedItem();
                if (selected != null) {
                    pendingDeletePath = decodePath(selected.value());
                    requestRender.run();
                }
                return;
            }
            if (keybindings.matches(data, EditorAction.SESSION_RENAME)) {
                var selected = sessions.getSelectedItem();
                if (selected != null) {
                    startRename(decodePath(selected.value()));
                }
                return;
            }

            search.handleInput(data);
            rebuildSessionList();
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
            rename.setFocused(focused && renamingPath != null);
        }

        private void start() {
            if (currentSessions != null) {
                if ((currentSessions.isEmpty()) && (allSessions == null || allSessions.isEmpty())) {
                    onNoSessions.run();
                    return;
                }
                rebuildSessionList();
                requestRender.run();
                return;
            }
            loadCurrentSessions();
        }

        private void primeSessions(List<SessionInfo> initialCurrentSessions, List<SessionInfo> initialAllSessions) {
            currentSessions = initialCurrentSessions == null ? null : new ArrayList<>(initialCurrentSessions);
            allSessions = initialAllSessions == null ? null : new ArrayList<>(initialAllSessions);
            currentLoading = false;
            allLoading = false;
            currentProgress = null;
            allProgress = null;
            loadingError = null;
            rebuildSessionList();
        }

        private void rebuildSessionList() {
            this.sessions = new SelectList(sessionItems(), 10, THEME);
            this.sessions.setOnSelectionChange(ignored -> requestRender.run());
            this.sessions.setOnSelect(item -> onSelect.accept(Path.of(decodePath(item.value()))));
            this.sessions.setOnCancel(onCancel);
            this.sessions.setFilter("");
        }

        private void deletePendingSession() {
            var targetPath = Path.of(pendingDeletePath);
            pendingDeletePath = null;
            try {
                Files.deleteIfExists(targetPath);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Failed to delete session: " + targetPath.getFileName(), exception);
            }

            if (currentSessions != null) {
                currentSessions = new ArrayList<>(currentSessions);
                currentSessions.removeIf(session -> session.path().toAbsolutePath().normalize().equals(targetPath.toAbsolutePath().normalize()));
            }
            if (allSessions != null) {
                allSessions = new ArrayList<>(allSessions);
                allSessions.removeIf(session -> session.path().toAbsolutePath().normalize().equals(targetPath.toAbsolutePath().normalize()));
            }
            if ((currentSessions == null || currentSessions.isEmpty()) && (allSessions == null || allSessions.isEmpty())) {
                onCancel.run();
                return;
            }
            rebuildSessionList();
            requestRender.run();
        }

        private void startRename(String targetPath) {
            renamingPath = targetPath;
            renamingCurrentName = currentNameFor(Path.of(targetPath));
            rename.setValue("");
            search.setFocused(false);
            rename.setFocused(true);
            requestRender.run();
        }

        private void renameSession() {
            var targetPath = Path.of(renamingPath);
            var nextName = rename.getValue() == null ? "" : rename.getValue().trim();
            if (nextName.isEmpty()) {
                return;
            }
            try {
                var manager = SessionManager.open(targetPath);
                manager.appendSessionInfo(nextName);
                if (currentSessions != null) {
                    refreshSessionInfo(currentSessions, targetPath);
                }
                if (allSessions != null) {
                    refreshSessionInfo(allSessions, targetPath);
                }
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Failed to rename session: " + targetPath.getFileName(), exception);
            }
            renamingPath = null;
            renamingCurrentName = null;
            rename.setValue("");
            rename.setFocused(false);
            search.setFocused(true);
            rebuildSessionList();
            requestRender.run();
        }

        private void refreshSessionInfo(List<SessionInfo> sessionInfos, Path targetPath) throws java.io.IOException {
            var refreshed = SessionManager.list(targetPath.getParent()).stream()
                .filter(session -> session.path().equals(targetPath.toAbsolutePath().normalize()))
                .findFirst()
                .orElse(null);
            if (refreshed == null) {
                return;
            }
            for (var index = 0; index < sessionInfos.size(); index += 1) {
                if (sessionInfos.get(index).path().equals(refreshed.path())) {
                    sessionInfos.set(index, refreshed);
                    return;
                }
            }
        }

        private String currentNameFor(Path targetPath) {
            var normalized = targetPath.toAbsolutePath().normalize();
            var loadedAllSessions = allSessions == null ? List.<SessionInfo>of() : allSessions;
            for (var sessionInfo : loadedAllSessions) {
                if (sessionInfo.path().equals(normalized)) {
                    return sessionInfo.name() == null ? "" : sessionInfo.name();
                }
            }
            return "";
        }

        private void toggleScope() {
            if (sameScopeData()) {
                return;
            }
            scope = scope == Scope.CURRENT ? Scope.ALL : Scope.CURRENT;
            loadingError = null;
            if (scope == Scope.ALL && allSessions == null && !allLoading) {
                loadAllSessions();
            }
            rebuildSessionList();
            requestRender.run();
        }

        private void toggleSort() {
            sortMode = switch (sortMode) {
                case THREADED -> SortMode.RECENT;
                case RECENT -> SortMode.RELEVANCE;
                case RELEVANCE -> SortMode.THREADED;
            };
            rebuildSessionList();
            requestRender.run();
        }

        private void toggleNameFilter() {
            nameFilter = nameFilter == NameFilter.ALL ? NameFilter.NAMED : NameFilter.ALL;
            rebuildSessionList();
            requestRender.run();
        }

        private void togglePath() {
            showPath = !showPath;
            rebuildSessionList();
            requestRender.run();
        }

        private List<SessionInfo> activeSessions() {
            var sessions = scope == Scope.ALL ? allSessions : currentSessions;
            return sessions == null ? List.of() : sessions;
        }

        private List<SessionInfo> sortedSessions() {
            var effectiveSortMode = sortMode == SortMode.THREADED && !search.getValue().isBlank()
                ? SortMode.RELEVANCE
                : sortMode;
            return filterAndSortSessions(activeSessions(), search.getValue(), effectiveSortMode, nameFilter);
        }

        private List<SelectItem> sessionItems() {
            if (sortMode == SortMode.THREADED && search.getValue().isBlank()) {
                return flattenThreadedSessions(activeSessions(), nameFilter).stream()
                    .map(session -> toSelectItem(session.session(), showCwd(), showPath, session.prefix()))
                    .toList();
            }
            return sortedSessions().stream()
                .map(session -> toSelectItem(session, showCwd(), showPath, ""))
                .toList();
        }

        private boolean showCwd() {
            return scope == Scope.ALL || sameScopeData();
        }

        private boolean sameScopeData() {
            if (currentSessions == null || allSessions == null) {
                return false;
            }
            return currentSessions.size() == allSessions.size() && currentSessions.containsAll(allSessions) && allSessions.containsAll(currentSessions);
        }

        private void loadCurrentSessions() {
            if (currentLoading || currentSessions != null) {
                return;
            }
            currentLoading = true;
            currentProgress = null;
            loadingError = null;
            requestRender.run();
            Thread.ofVirtual().start(() -> runLoader(Scope.CURRENT, currentLoader));
        }

        private void loadAllSessions() {
            if (allLoading || allSessions != null) {
                return;
            }
            allLoading = true;
            allProgress = null;
            loadingError = null;
            requestRender.run();
            Thread.ofVirtual().start(() -> runLoader(Scope.ALL, allLoader));
        }

        private void runLoader(Scope targetScope, SessionLoader loader) {
            try {
                var loadedSessions = loader.load((loaded, total) -> {
                    if (targetScope == Scope.CURRENT) {
                        currentProgress = new ProgressSnapshot(loaded, total);
                    } else {
                        allProgress = new ProgressSnapshot(loaded, total);
                    }
                    requestRender.run();
                });
                if (targetScope == Scope.CURRENT) {
                    currentSessions = new ArrayList<>(loadedSessions);
                    currentLoading = false;
                    currentProgress = new ProgressSnapshot(1, 1);
                    if (currentSessions.isEmpty() && allSessions == null && !allLoading) {
                        loadAllSessions();
                    }
                } else {
                    allSessions = new ArrayList<>(loadedSessions);
                    allLoading = false;
                }
                if (targetScope == Scope.ALL && (loadedSessions == null || loadedSessions.isEmpty()) && (currentSessions == null || currentSessions.isEmpty())) {
                    onNoSessions.run();
                    return;
                }
                rebuildSessionList();
                requestRender.run();
            } catch (Exception exception) {
                if (targetScope == Scope.CURRENT) {
                    currentLoading = false;
                } else {
                    allLoading = false;
                }
                loadingError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                rebuildSessionList();
                requestRender.run();
            }
        }

        private String scopeSummary() {
            if (scope == Scope.CURRENT) {
                if (currentLoading) {
                    return "Loading current " + progressText(currentProgress);
                }
                return "◉ Current Folder | ○ All";
            }
            if (allLoading) {
                return "○ Current Folder | Loading " + progressText(allProgress);
            }
            return "○ Current Folder | ◉ All";
        }

        private static String progressText(ProgressSnapshot progress) {
            if (progress == null || progress.total() <= 0) {
                return "...";
            }
            return progress.loaded() + "/" + progress.total();
        }

        private static List<ThreadedSession> flattenThreadedSessions(List<SessionInfo> sessions, NameFilter nameFilter) {
            var visibleSessions = sessions.stream()
                .filter(session -> nameFilter == NameFilter.ALL || hasName(session))
                .toList();
            var nodesByPath = new java.util.LinkedHashMap<Path, ThreadedNode>();
            for (var session : visibleSessions) {
                nodesByPath.put(session.path(), new ThreadedNode(session));
            }

            var roots = new ArrayList<ThreadedNode>();
            for (var node : nodesByPath.values()) {
                var parentPath = node.session().parentSessionPath();
                if (parentPath != null && !parentPath.isBlank()) {
                    var parent = nodesByPath.get(Path.of(parentPath).toAbsolutePath().normalize());
                    if (parent != null) {
                        parent.children().add(node);
                        continue;
                    }
                }
                roots.add(node);
            }

            var byModified = Comparator.comparing((ThreadedNode node) -> node.session().modified()).reversed();
            sortThreadedNodes(roots, byModified);

            var flattened = new ArrayList<ThreadedSession>();
            for (var index = 0; index < roots.size(); index += 1) {
                flattenThreadedNode(roots.get(index), List.of(), index == roots.size() - 1, flattened);
            }
            return List.copyOf(flattened);
        }

        private static void sortThreadedNodes(List<ThreadedNode> nodes, Comparator<ThreadedNode> comparator) {
            nodes.sort(comparator);
            for (var node : nodes) {
                sortThreadedNodes(node.children(), comparator);
            }
        }

        private static void flattenThreadedNode(
            ThreadedNode node,
            List<Boolean> ancestry,
            boolean isLast,
            List<ThreadedSession> flattened
        ) {
            flattened.add(new ThreadedSession(node.session(), treePrefix(ancestry, isLast)));
            var nextAncestry = new ArrayList<Boolean>(ancestry);
            nextAncestry.add(isLast);
            for (var index = 0; index < node.children().size(); index += 1) {
                flattenThreadedNode(node.children().get(index), nextAncestry, index == node.children().size() - 1, flattened);
            }
        }

        private static String treePrefix(List<Boolean> ancestry, boolean isLast) {
            if (ancestry.isEmpty()) {
                return "";
            }
            var prefix = new StringBuilder();
            for (var branchIsLast : ancestry) {
                prefix.append(branchIsLast ? "   " : "│  ");
            }
            prefix.append(isLast ? "└─ " : "├─ ");
            return prefix.toString();
        }

        private static SelectItem toSelectItem(SessionInfo session, boolean showCwd, boolean showPath, String prefix) {
            var label = prefix + PiSessionPicker.displayLabel(session);
            var metadata = new ArrayList<String>();
            if (showPath) {
                metadata.add(shortenPath(session.path().toString()));
            }
            if (showCwd && session.cwd() != null && !session.cwd().isBlank()) {
                metadata.add(shortenPath(session.cwd()));
            }
            metadata.add("%d msg".formatted(session.messageCount()));
            metadata.add(formatAge(session.modified()));
            var description = String.join(" · ", metadata);
            return new SelectItem(encodeValue(session, label), label, description);
        }

        private static String keyHint(EditorAction action) {
            var keys = EditorKeybindings.global().getKeys(action);
            return keys.isEmpty() ? action.name() : keys.getFirst();
        }

        private static String appKeyHint(PiAppAction action) {
            var keys = PiAppKeybindings.global().getKeys(action);
            return keys.isEmpty() ? action.name() : keys.getFirst();
        }

        private static String formatAge(Instant modified) {
            var elapsed = Duration.between(modified, Instant.now()).abs();
            if (elapsed.compareTo(Duration.ofMinutes(1)) < 0) {
                return "now";
            }
            if (elapsed.compareTo(Duration.ofHours(1)) < 0) {
                return elapsed.toMinutes() + "m";
            }
            if (elapsed.compareTo(Duration.ofDays(1)) < 0) {
                return elapsed.toHours() + "h";
            }
            return elapsed.toDays() + "d";
        }

        private static String shortenPath(String path) {
            if (path == null || path.isBlank()) {
                return path;
            }
            var home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize().toString();
            return path.startsWith(home) ? "~" + path.substring(home.length()) : path;
        }

        private static String encodeValue(SessionInfo session, String label) {
            var path = session.path();
            var fileName = path.getFileName() == null ? path.toString() : path.getFileName().toString();
            var cwd = session.cwd() == null ? "" : session.cwd();
            return (label + " " + fileName + " " + cwd + " " + path + VALUE_DELIMITER + path).trim();
        }

        private static String decodePath(String value) {
            var delimiter = value.lastIndexOf(VALUE_DELIMITER);
            return delimiter >= 0 ? value.substring(delimiter + VALUE_DELIMITER.length()) : value;
        }

        private enum Scope {
            CURRENT,
            ALL
        }

        private record ThreadedNode(
            SessionInfo session,
            List<ThreadedNode> children
        ) {
            private ThreadedNode(SessionInfo session) {
                this(session, new ArrayList<>());
            }
        }

        private record ThreadedSession(
            SessionInfo session,
            String prefix
        ) {
        }

        private record ProgressSnapshot(
            int loaded,
            int total
        ) {
        }
    }
}
