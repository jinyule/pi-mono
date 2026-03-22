package dev.pi.cli;

import dev.pi.tui.Component;
import dev.pi.tui.EditorAction;
import dev.pi.tui.Focusable;
import dev.pi.tui.SelectItem;
import dev.pi.tui.SelectList;
import dev.pi.tui.SelectListTheme;
import dev.pi.tui.SettingItem;
import dev.pi.tui.SettingsList;
import dev.pi.tui.SettingsListOptions;
import dev.pi.tui.SettingsListTheme;
import dev.pi.tui.TerminalText;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PiSettingsSelector implements Component, Focusable {
    private final SettingsList settingsList;
    private boolean focused;

    public PiSettingsSelector(
        PiInteractiveSession.SettingsSelection settings,
        BiConsumer<String, String> onChange,
        Runnable onCancel
    ) {
        this(settings, onChange, onCancel, (settingId, value) -> {
        });
    }

    public PiSettingsSelector(
        PiInteractiveSession.SettingsSelection settings,
        BiConsumer<String, String> onChange,
        Runnable onCancel,
        BiConsumer<String, String> onPreview
    ) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(onChange, "onChange");
        Objects.requireNonNull(onCancel, "onCancel");
        Objects.requireNonNull(onPreview, "onPreview");

        var items = new ArrayList<SettingItem>();
        items.add(new SettingItem(
            "autocompact",
            "Auto-compact",
            "Automatically compact context when it gets too large",
            settings.autoCompact() ? "true" : "false",
            List.of("true", "false"),
            null
        ));
        items.add(new SettingItem(
            "steering-mode",
            "Steering mode",
            "Enter while streaming queues steering messages. 'one-at-a-time': deliver one, wait for response. 'all': deliver all at once.",
            settings.steeringMode(),
            List.of("one-at-a-time", "all"),
            null
        ));
        items.add(new SettingItem(
            "follow-up-mode",
            "Follow-up mode",
            "Alt+Enter queues follow-up messages until agent stops. 'one-at-a-time': deliver one, wait for response. 'all': deliver all at once.",
            settings.followUpMode(),
            List.of("one-at-a-time", "all"),
            null
        ));
        items.add(new SettingItem(
            "transport",
            "Transport",
            "Preferred transport for providers that support multiple transports",
            settings.transport(),
            List.of("sse", "websocket", "auto"),
            null
        ));
        if (settings.reasoningAvailable()) {
            items.add(new SettingItem(
                "thinking",
                "Thinking level",
                "Reasoning depth for the current model",
                settings.thinkingLevel(),
                List.of(),
                (currentValue, done) -> new SelectSubmenu(
                    "Thinking Level",
                    "Select reasoning depth for thinking-capable models",
                    settings.availableThinkingLevels().stream()
                        .map(level -> new SelectItem(level, level, thinkingDescription(level)))
                        .toList(),
                    currentValue,
                    done::accept,
                    () -> done.accept(null),
                    null
                )
            ));
        }
        items.add(new SettingItem(
            "hide-thinking",
            "Hide thinking",
            "Hide thinking blocks in assistant responses",
            settings.hideThinkingBlock() ? "true" : "false",
            List.of("true", "false"),
            null
        ));
        items.add(new SettingItem(
            "quiet-startup",
            "Quiet startup",
            "Disable verbose printing at startup",
            settings.quietStartup() ? "true" : "false",
            List.of("true", "false"),
            null
        ));
        items.add(new SettingItem(
            "double-escape-action",
            "Double-escape action",
            "Action when pressing Escape twice with empty editor",
            settings.doubleEscapeAction(),
            List.of("tree", "fork", "none"),
            null
        ));
        items.add(new SettingItem(
            "theme",
            "Theme",
            "Color theme for the interface",
            settings.theme(),
            List.of(),
            (currentValue, done) -> new SelectSubmenu(
                "Theme",
                "Select color theme",
                settings.availableThemes().stream()
                    .map(themeName -> new SelectItem(themeName, themeName, null))
                    .toList(),
                currentValue,
                done::accept,
                () -> {
                    onPreview.accept("theme", currentValue);
                    done.accept(null);
                },
                value -> onPreview.accept("theme", value)
            )
        ));
        items.add(new SettingItem(
            "show-hardware-cursor",
            "Show hardware cursor",
            "Show the terminal cursor while still positioning it for IME support",
            settings.showHardwareCursor() ? "true" : "false",
            List.of("true", "false"),
            null
        ));
        items.add(new SettingItem(
            "editor-padding",
            "Editor padding",
            "Horizontal padding for input editor (0-3)",
            Integer.toString(settings.editorPaddingX()),
            List.of("0", "1", "2", "3"),
            null
        ));
        items.add(new SettingItem(
            "clear-on-shrink",
            "Clear on shrink",
            "Clear empty rows when content shrinks (may cause flicker)",
            settings.clearOnShrink() ? "true" : "false",
            List.of("true", "false"),
            null
        ));

        this.settingsList = new SettingsList(
            items,
            Math.max(6, Math.min(10, Math.max(1, items.size()))),
            theme(),
            onChange,
            onCancel,
            new SettingsListOptions(true)
        );
        this.settingsList.setFocused(true);
    }

    @Override
    public List<String> render(int width) {
        var lines = new ArrayList<String>();
        lines.add(PiCliAnsi.bold("Settings"));
        lines.add(
            PiCliAnsi.muted("Type to search. ")
                + PiCliKeyHints.editorHint(EditorAction.SUBMIT, "changes")
                + PiCliAnsi.muted(" or ")
                + PiCliKeyHints.rawHint("space", "changes.")
                + PiCliAnsi.muted(" ")
                + PiCliKeyHints.editorHint(EditorAction.SELECT_CANCEL, "cancels.")
        );
        lines.add("");
        lines.addAll(settingsList.render(width));
        return List.copyOf(lines);
    }

    @Override
    public void handleInput(String data) {
        settingsList.handleInput(data);
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        settingsList.setFocused(focused);
    }

    @Override
    public void invalidate() {
        settingsList.invalidate();
    }

    private static SettingsListTheme theme() {
        return new SettingsListTheme() {
            @Override
            public String label(String text, boolean selected) {
                return selected ? PiCliAnsi.bold(text) : text;
            }

            @Override
            public String value(String text, boolean selected) {
                return selected ? PiCliAnsi.accent(text) : PiCliAnsi.muted(text);
            }

            @Override
            public String description(String text) {
                return PiCliAnsi.muted(text);
            }

            @Override
            public String cursor() {
                return PiCliAnsi.accent("→");
            }

            @Override
            public String hint(String text) {
                return PiCliAnsi.muted(text);
            }

            @Override
            public String hintKey(String text) {
                return PiCliAnsi.dim(text);
            }

            @Override
            public String hintDescription(String text) {
                return PiCliAnsi.muted(text);
            }
        };
    }

    private static SelectListTheme selectTheme() {
        return new SelectListTheme() {
            @Override
            public String selectedPrefix(String text) {
                return PiCliAnsi.accent(text);
            }

            @Override
            public String selectedText(String text) {
                return PiCliAnsi.accentBold(text);
            }

            @Override
            public String selectedDescription(String text) {
                return PiCliAnsi.muted(text);
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
    }

    private static String thinkingDescription(String level) {
        return switch (level) {
            case "off" -> "No reasoning";
            case "minimal" -> "Very brief reasoning (~1k tokens)";
            case "low" -> "Light reasoning (~2k tokens)";
            case "medium" -> "Moderate reasoning (~8k tokens)";
            case "high" -> "Deep reasoning (~16k tokens)";
            case "xhigh" -> "Maximum reasoning (~32k tokens)";
            default -> null;
        };
    }

    private static final class SelectSubmenu implements Component {
        private final String title;
        private final String description;
        private final SelectList selectList;

        private SelectSubmenu(
            String title,
            String description,
            List<SelectItem> options,
            String currentValue,
            Consumer<String> onSelect,
            Runnable onCancel,
            Consumer<String> onSelectionChange
        ) {
            this.title = title == null ? "" : title;
            this.description = description;
            this.selectList = new SelectList(
                options == null ? List.of() : options,
                Math.max(1, Math.min(10, options == null ? 0 : options.size())),
                selectTheme()
            );

            if (options != null) {
                for (var index = 0; index < options.size(); index += 1) {
                    if (Objects.equals(options.get(index).value(), currentValue)) {
                        selectList.setSelectedIndex(index);
                        break;
                    }
                }
            }

            selectList.setOnSelect(item -> onSelect.accept(item.value()));
            selectList.setOnCancel(onCancel);
            if (onSelectionChange != null) {
                selectList.setOnSelectionChange(item -> onSelectionChange.accept(item.value()));
            }
        }

        @Override
        public List<String> render(int width) {
            var lines = new ArrayList<String>();
            lines.add(PiCliAnsi.accentBold(title));
            if (description != null && !description.isBlank()) {
                lines.add("");
                for (var line : TerminalText.wrapText(description, Math.max(1, width))) {
                    lines.add(PiCliAnsi.muted(line));
                }
            }
            lines.add("");
            lines.addAll(selectList.render(width));
            lines.add("");
            lines.add(
                PiCliAnsi.muted("  ")
                    + PiCliKeyHints.editorHint(EditorAction.SUBMIT, "to select")
                    + PiCliAnsi.muted(" · ")
                    + PiCliKeyHints.editorHint(EditorAction.SELECT_CANCEL, "to go back")
            );
            return List.copyOf(lines);
        }

        @Override
        public void handleInput(String data) {
            selectList.handleInput(data);
        }
    }
}
