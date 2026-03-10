package dev.pi.cli;

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
import java.util.List;
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

    @Override
    public Path pick(List<SessionInfo> sessions) {
        Objects.requireNonNull(sessions, "sessions");
        if (sessions.isEmpty()) {
            return null;
        }

        var selection = new CompletableFuture<Path>();
        var tui = new Tui(terminal, true);
        var component = new PickerComponent(
            sessions,
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

    private static final class PickerComponent implements Component, Focusable {
        private final Input search = new Input();
        private final List<SessionInfo> sessionInfos;
        private final java.util.function.Consumer<Path> onSelect;
        private final Runnable onCancel;
        private final Runnable requestRender;
        private SelectList sessions;
        private boolean focused;
        private String pendingDeletePath;

        private PickerComponent(
            List<SessionInfo> sessionInfos,
            java.util.function.Consumer<Path> onSelect,
            Runnable onCancel,
            Runnable requestRender
        ) {
            this.sessionInfos = new ArrayList<>(sessionInfos);
            this.onSelect = onSelect;
            this.onCancel = onCancel;
            this.requestRender = requestRender;
            this.search.setFocused(true);
            rebuildSessionList();
        }

        @Override
        public List<String> render(int width) {
            var lines = new java.util.ArrayList<String>();
            lines.add("Resume session");
            lines.add(pendingDeletePath == null
                ? "Type to filter. Enter selects. Ctrl+D deletes. Esc cancels."
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
        }

        private void rebuildSessionList() {
            this.sessions = new SelectList(sessionInfos.stream().map(PickerComponent::toSelectItem).toList(), 10, THEME);
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

            sessionInfos.removeIf(session -> session.path().toAbsolutePath().normalize().equals(targetPath.toAbsolutePath().normalize()));
            if (sessionInfos.isEmpty()) {
                onCancel.run();
                return;
            }
            rebuildSessionList();
            requestRender.run();
        }

        private static SelectItem toSelectItem(SessionInfo session) {
            var label = session.name() == null || session.name().isBlank() ? session.firstMessage() : session.name();
            var description = "%s · %d msg · %s%s".formatted(
                session.path().getFileName(),
                session.messageCount(),
                formatAge(session.modified()),
                session.cwd() == null || session.cwd().isBlank() ? "" : " · " + session.cwd()
            );
            return new SelectItem(encodeValue(session, label), label, description);
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
    }
}
