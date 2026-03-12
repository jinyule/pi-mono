package dev.pi.cli;

import dev.pi.tui.Component;
import dev.pi.tui.EditorAction;
import dev.pi.tui.EditorKeybindings;
import dev.pi.tui.Focusable;
import dev.pi.tui.SettingItem;
import dev.pi.tui.SettingsList;
import dev.pi.tui.SettingsListOptions;
import dev.pi.tui.SettingsListTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;

public final class PiSettingsSelector implements Component, Focusable {
    private final SettingsList settingsList;
    private boolean focused;

    public PiSettingsSelector(
        PiInteractiveSession.SettingsSelection settings,
        BiConsumer<String, String> onChange,
        Runnable onCancel
    ) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(onChange, "onChange");
        Objects.requireNonNull(onCancel, "onCancel");

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
            "Queued steering messages: deliver one-at-a-time or all at once",
            settings.steeringMode(),
            List.of("one-at-a-time", "all"),
            null
        ));
        items.add(new SettingItem(
            "follow-up-mode",
            "Follow-up mode",
            "Queued follow-ups: deliver one-at-a-time or all at once",
            settings.followUpMode(),
            List.of("one-at-a-time", "all"),
            null
        ));
        items.add(new SettingItem(
            "transport",
            "Transport",
            "Preferred provider transport for new requests",
            settings.transport(),
            List.of("auto", "sse", "websocket"),
            null
        ));
        if (settings.reasoningAvailable()) {
            items.add(new SettingItem(
                "thinking",
                "Thinking level",
                "Reasoning depth for the current model",
                settings.thinkingLevel(),
                settings.availableThinkingLevels(),
                null
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
        lines.add(PiCliAnsi.muted(
            "Type to search. %s or space changes. %s cancels."
                .formatted(keyHint(EditorAction.SUBMIT), keyHint(EditorAction.SELECT_CANCEL))
        ));
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
        };
    }

    private static String keyHint(EditorAction action) {
        var keys = EditorKeybindings.global().getKeys(action);
        return keys.isEmpty() ? action.name().toLowerCase(Locale.ROOT) : keys.getFirst();
    }
}
