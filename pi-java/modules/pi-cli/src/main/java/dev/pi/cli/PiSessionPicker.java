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
        var filterTokens = filterTokens(filter);
        if (sortMode == SortMode.RELEVANCE && !filterTokens.isEmpty()) {
            visibleSessions.sort(
                Comparator.comparingInt((SessionInfo session) -> relevanceScore(session, filterTokens))
                    .thenComparing(SessionInfo::modified, Comparator.reverseOrder())
            );
        }
        return List.copyOf(visibleSessions);
    }

    @Override
    public Path pick(List<SessionInfo> currentSessions, List<SessionInfo> allSessions) {
        Objects.requireNonNull(currentSessions, "currentSessions");
        Objects.requireNonNull(allSessions, "allSessions");
        if (currentSessions.isEmpty() && allSessions.isEmpty()) {
            return null;
        }

        var selection = new CompletableFuture<Path>();
        var tui = new Tui(terminal, true);
        var component = new PickerComponent(
            currentSessions,
            allSessions,
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
            tui::requestRender
        );
        tui.addChild(component);
        tui.setFocus(component);
        tui.start();
        return selection.join();
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

    private static List<String> filterTokens(String filter) {
        var normalized = filter == null ? "" : filter.toLowerCase(Locale.ROOT).trim();
        return normalized.isEmpty() ? List.of() : List.of(normalized.split("\\s+"));
    }

    private static int relevanceScore(SessionInfo session, List<String> tokens) {
        var label = displayLabel(session).toLowerCase(Locale.ROOT);
        var fileName = session.path().getFileName() == null
            ? session.path().toString().toLowerCase(Locale.ROOT)
            : session.path().getFileName().toString().toLowerCase(Locale.ROOT);
        var cwd = session.cwd().toLowerCase(Locale.ROOT);
        var path = session.path().toString().toLowerCase(Locale.ROOT);
        var score = 0;
        for (var token : tokens) {
            var tokenScore = fieldScore(label, token, 0);
            tokenScore = Math.min(tokenScore, fieldScore(fileName, token, 100));
            tokenScore = Math.min(tokenScore, fieldScore(cwd, token, 200));
            tokenScore = Math.min(tokenScore, fieldScore(path, token, 300));
            if (tokenScore == Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            score = score > Integer.MAX_VALUE - tokenScore ? Integer.MAX_VALUE : score + tokenScore;
        }
        return score;
    }

    private static int fieldScore(String value, String token, int base) {
        var index = value.indexOf(token);
        return index < 0 ? Integer.MAX_VALUE : base + index;
    }

    enum SortMode {
        RECENT("Recent"),
        RELEVANCE("Relevance"),
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

    private static final class PickerComponent implements Component, Focusable {
        private final Input search = new Input();
        private final Input rename = new Input();
        private final List<SessionInfo> currentSessions;
        private final List<SessionInfo> allSessions;
        private final java.util.function.Consumer<Path> onSelect;
        private final Runnable onCancel;
        private final Runnable requestRender;
        private SelectList sessions;
        private boolean focused;
        private String pendingDeletePath;
        private String renamingPath;
        private String renamingCurrentName;
        private Scope scope = Scope.CURRENT;
        private SortMode sortMode = SortMode.RECENT;
        private NameFilter nameFilter = NameFilter.ALL;
        private boolean showPath;

        private PickerComponent(
            List<SessionInfo> currentSessions,
            List<SessionInfo> allSessions,
            java.util.function.Consumer<Path> onSelect,
            Runnable onCancel,
            Runnable requestRender
        ) {
            this.currentSessions = new ArrayList<>(currentSessions);
            this.allSessions = new ArrayList<>(allSessions);
            this.onSelect = onSelect;
            this.onCancel = onCancel;
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
            lines.add(pendingDeletePath == null
                ? "%s scope · %s sort(%s) · %s named(%s) · %s path(%s)"
                    .formatted(
                        keyHint(EditorAction.SESSION_SCOPE_TOGGLE),
                        keyHint(EditorAction.SESSION_SORT_TOGGLE),
                        sortMode.label(),
                        keyHint(EditorAction.SESSION_NAMED_FILTER_TOGGLE),
                        nameFilter.label(),
                        keyHint(EditorAction.SESSION_PATH_TOGGLE),
                        showPath ? "on" : "off"
                    )
                : "Delete selected session? Enter confirms. Esc cancels.");
            lines.add("");
            lines.addAll(search.render(width));
            lines.add("");
            lines.addAll(sessions.render(width));
            return List.copyOf(lines);
        }

        @Override
        public void handleInput(String data) {
            var keybindings = EditorKeybindings.global();
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
            if (keybindings.matches(data, EditorAction.SESSION_NAMED_FILTER_TOGGLE)) {
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
            sessions.setFilter(search.getValue());
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

        private void rebuildSessionList() {
            this.sessions = new SelectList(sessionItems(), 10, THEME);
            this.sessions.setOnSelectionChange(ignored -> requestRender.run());
            this.sessions.setOnSelect(item -> onSelect.accept(Path.of(decodePath(item.value()))));
            this.sessions.setOnCancel(onCancel);
            this.sessions.setFilter(search.getValue());
        }

        private void deletePendingSession() {
            var targetPath = Path.of(pendingDeletePath);
            pendingDeletePath = null;
            try {
                Files.deleteIfExists(targetPath);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Failed to delete session: " + targetPath.getFileName(), exception);
            }

            currentSessions.removeIf(session -> session.path().toAbsolutePath().normalize().equals(targetPath.toAbsolutePath().normalize()));
            allSessions.removeIf(session -> session.path().toAbsolutePath().normalize().equals(targetPath.toAbsolutePath().normalize()));
            if (currentSessions.isEmpty() && allSessions.isEmpty()) {
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
                refreshSessionInfo(currentSessions, targetPath);
                refreshSessionInfo(allSessions, targetPath);
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
            for (var sessionInfo : allSessions) {
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
            rebuildSessionList();
            requestRender.run();
        }

        private void toggleSort() {
            sortMode = switch (sortMode) {
                case RECENT -> SortMode.RELEVANCE;
                case RELEVANCE -> SortMode.THREADED;
                case THREADED -> SortMode.RECENT;
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
            return scope == Scope.ALL ? allSessions : currentSessions;
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
            return currentSessions.size() == allSessions.size() && currentSessions.containsAll(allSessions) && allSessions.containsAll(currentSessions);
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
            var description = "%s · %d msg · %s%s%s".formatted(
                session.path().getFileName(),
                session.messageCount(),
                formatAge(session.modified()),
                showCwd && session.cwd() != null && !session.cwd().isBlank() ? " · " + session.cwd() : "",
                showPath ? " · " + session.path() : ""
            );
            return new SelectItem(encodeValue(session, label), label, description);
        }

        private static String keyHint(EditorAction action) {
            var keys = EditorKeybindings.global().getKeys(action);
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
    }
}
