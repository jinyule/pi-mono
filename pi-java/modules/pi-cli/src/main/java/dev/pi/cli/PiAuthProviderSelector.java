package dev.pi.cli;

import dev.pi.tui.Component;
import dev.pi.tui.Focusable;
import dev.pi.tui.SelectItem;
import dev.pi.tui.SelectList;
import dev.pi.tui.SelectListTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class PiAuthProviderSelector implements Component, Focusable {
    private final String title;
    private final String emptyMessage;
    private final SelectList selectList;
    private boolean focused;

    public PiAuthProviderSelector(
        String title,
        String emptyMessage,
        List<PiInteractiveSession.AuthProvider> providers,
        Consumer<String> onSelect,
        Runnable onCancel
    ) {
        this.title = title == null ? "" : title;
        this.emptyMessage = emptyMessage == null ? "" : emptyMessage;
        Objects.requireNonNull(onSelect, "onSelect");
        Objects.requireNonNull(onCancel, "onCancel");
        this.selectList = new SelectList(
            providers == null
                ? List.of()
                : providers.stream()
                    .map(provider -> new SelectItem(provider.providerId(), provider.displayName(), provider.loggedIn() ? "saved" : null))
                    .toList(),
            Math.max(1, Math.min(10, providers == null ? 0 : providers.size())),
            theme()
        );
        this.selectList.setOnSelect(item -> onSelect.accept(item.value()));
        this.selectList.setOnCancel(onCancel);
    }

    @Override
    public List<String> render(int width) {
        var lines = new ArrayList<String>();
        var safeWidth = Math.max(1, width);
        lines.add(PiCliAnsi.borderMuted("\u2500".repeat(safeWidth)));
        lines.add(PiCliAnsi.accentBold(title));
        lines.add("");
        if (selectList.getSelectedItem() == null && selectList.render(safeWidth).size() == 1) {
            lines.add(PiCliAnsi.muted("  " + emptyMessage));
        } else {
            lines.addAll(selectList.render(safeWidth));
            lines.add("");
            lines.add(
                PiCliAnsi.muted("  ")
                    + PiCliKeyHints.editorHint(dev.pi.tui.EditorAction.SUBMIT, "to select")
                    + PiCliAnsi.muted(" \u00b7 ")
                    + PiCliKeyHints.editorHint(dev.pi.tui.EditorAction.SELECT_CANCEL, "to cancel")
            );
        }
        lines.add(PiCliAnsi.borderMuted("\u2500".repeat(safeWidth)));
        return List.copyOf(lines);
    }

    @Override
    public void handleInput(String data) {
        selectList.handleInput(data);
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    private static SelectListTheme theme() {
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
}
