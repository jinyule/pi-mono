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
import java.time.Duration;
import java.time.Instant;
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
        private final SelectList sessions;
        private final java.util.function.Consumer<Path> onSelect;
        private final Runnable onCancel;
        private final Runnable requestRender;
        private boolean focused;

        private PickerComponent(
            List<SessionInfo> sessionInfos,
            java.util.function.Consumer<Path> onSelect,
            Runnable onCancel,
            Runnable requestRender
        ) {
            this.sessions = new SelectList(sessionInfos.stream().map(PickerComponent::toSelectItem).toList(), 10, THEME);
            this.onSelect = onSelect;
            this.onCancel = onCancel;
            this.requestRender = requestRender;
            this.search.setFocused(true);
            this.sessions.setOnSelectionChange(ignored -> requestRender.run());
            this.sessions.setOnSelect(item -> onSelect.accept(Path.of(decodePath(item.value()))));
            this.sessions.setOnCancel(onCancel);
        }

        @Override
        public List<String> render(int width) {
            var lines = new java.util.ArrayList<String>();
            lines.add("Resume session");
            lines.add("Type to filter. Enter selects. Esc cancels.");
            lines.add("");
            lines.addAll(search.render(width));
            lines.add("");
            lines.addAll(sessions.render(width));
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
                sessions.handleInput(data);
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

        private static SelectItem toSelectItem(SessionInfo session) {
            var label = session.name() == null || session.name().isBlank() ? session.firstMessage() : session.name();
            var description = "%s · %d msg · %s%s".formatted(
                session.path().getFileName(),
                session.messageCount(),
                formatAge(session.modified()),
                session.cwd() == null || session.cwd().isBlank() ? "" : " · " + session.cwd()
            );
            return new SelectItem(encodeValue(label, session.path()), label, description);
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

        private static String encodeValue(String label, Path path) {
            return label + " " + path + VALUE_DELIMITER + path;
        }

        private static String decodePath(String value) {
            var delimiter = value.lastIndexOf(VALUE_DELIMITER);
            return delimiter >= 0 ? value.substring(delimiter + VALUE_DELIMITER.length()) : value;
        }
    }
}
