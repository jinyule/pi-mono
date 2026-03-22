package dev.pi.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Tui {
    public static final String CURSOR_MARKER = "\u001b_pi:c\u0007";
    private static final String SEGMENT_RESET = "\u001b[0m\u001b]8;;\u0007";

    private final Terminal terminal;
    private final DiffRenderer diffRenderer;
    private final List<Component> children = new ArrayList<>();
    private final List<ManagedOverlay> overlayStack = new ArrayList<>();

    private Component focusedComponent;
    private boolean showHardwareCursor;
    private boolean started;

    public Tui(Terminal terminal) {
        this(terminal, false);
    }

    public Tui(Terminal terminal, boolean showHardwareCursor) {
        this.terminal = Objects.requireNonNull(terminal, "terminal");
        this.diffRenderer = new DiffRenderer(terminal);
        this.showHardwareCursor = showHardwareCursor;
    }

    public void addChild(Component component) {
        children.add(Objects.requireNonNull(component, "component"));
    }

    public void removeChild(Component component) {
        children.remove(component);
    }

    public void clear() {
        children.clear();
    }

    public void invalidate() {
        for (var child : children) {
            child.invalidate();
        }
        for (var overlay : overlayStack) {
            overlay.component().invalidate();
        }
    }

    public void setFocus(Component component) {
        if (focusedComponent instanceof Focusable focusable) {
            focusable.setFocused(false);
        }

        focusedComponent = component;

        if (component instanceof Focusable focusable) {
            focusable.setFocused(true);
        }
    }

    public OverlayHandle showOverlay(Component component, OverlayOptions options) {
        var overlay = new ManagedOverlay(component, options == null ? OverlayOptions.defaults() : options, focusedComponent);
        overlayStack.add(overlay);
        if (!overlay.isHidden()) {
            setFocus(component);
        }
        terminal.hideCursor();
        requestRender();
        return overlay;
    }

    public OverlayHandle showOverlay(Component component) {
        return showOverlay(component, OverlayOptions.defaults());
    }

    public boolean showHardwareCursor() {
        return showHardwareCursor;
    }

    public void setShowHardwareCursor(boolean showHardwareCursor) {
        this.showHardwareCursor = showHardwareCursor;
    }

    public boolean clearOnShrink() {
        return diffRenderer.clearOnShrink();
    }

    public void setClearOnShrink(boolean clearOnShrink) {
        diffRenderer.setClearOnShrink(clearOnShrink);
    }

    public void hideOverlay() {
        if (overlayStack.isEmpty()) {
            return;
        }
        var overlay = overlayStack.remove(overlayStack.size() - 1);
        var topVisible = topVisibleOverlay();
        setFocus(topVisible == null ? overlay.preFocus() : topVisible.component());
        if (!hasOverlay()) {
            terminal.hideCursor();
        }
        requestRender();
    }

    public boolean hasOverlay() {
        return overlayStack.stream().anyMatch(overlay -> !overlay.isHidden());
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        terminal.start(this::handleInput, this::requestRender);
        terminal.hideCursor();
        requestRender();
    }

    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        terminal.hideCursor();
        terminal.stop();
    }

    public void requestRender() {
        var width = terminal.columns();
        var height = terminal.rows();
        var lines = render(width);
        if (hasOverlay()) {
            lines = compositeOverlays(lines, width, height);
        }
        var cursor = extractCursorPosition(lines, height);
        lines = applyLineResets(lines);
        diffRenderer.render(lines, cursor, hasOverlay(), showHardwareCursor);
    }

    public List<String> render(int width) {
        var lines = new ArrayList<String>();
        for (var child : children) {
            lines.addAll(child.render(width));
        }
        return lines;
    }

    private void handleInput(String data) {
        if (focusedComponent != null) {
            focusedComponent.handleInput(data);
        }
    }

    private List<String> compositeOverlays(List<String> baseLines, int terminalWidth, int terminalHeight) {
        var result = new ArrayList<>(baseLines);
        var minLinesNeeded = result.size();
        var rendered = new ArrayList<RenderedOverlay>();

        for (var overlay : overlayStack) {
            if (overlay.isHidden()) {
                continue;
            }
            var layout = resolveOverlayLayout(overlay.options(), 0, terminalWidth, terminalHeight);
            var overlayLines = new ArrayList<>(overlay.component().render(layout.width()));
            if (layout.maxHeight() != null && overlayLines.size() > layout.maxHeight()) {
                overlayLines = new ArrayList<>(overlayLines.subList(0, layout.maxHeight()));
            }
            var finalLayout = resolveOverlayLayout(overlay.options(), overlayLines.size(), terminalWidth, terminalHeight);
            rendered.add(new RenderedOverlay(overlayLines, finalLayout.row(), finalLayout.column(), finalLayout.width()));
            minLinesNeeded = Math.max(minLinesNeeded, finalLayout.row() + overlayLines.size());
        }

        var workingHeight = Math.max(diffRenderer.previousLines().size(), minLinesNeeded);
        while (result.size() < workingHeight) {
            result.add("");
        }

        var viewportStart = Math.max(0, workingHeight - terminalHeight);
        for (var overlay : rendered) {
            for (var index = 0; index < overlay.lines().size(); index += 1) {
                var targetIndex = viewportStart + overlay.row() + index;
                if (targetIndex < 0 || targetIndex >= result.size()) {
                    continue;
                }
                var overlayLine = TerminalText.takeVisibleColumns(overlay.lines().get(index), overlay.width());
                result.set(
                    targetIndex,
                    compositeLine(result.get(targetIndex), overlayLine, overlay.column(), overlay.width(), terminalWidth)
                );
            }
        }

        return result;
    }

    private OverlayLayout resolveOverlayLayout(OverlayOptions options, int overlayHeight, int terminalWidth, int terminalHeight) {
        var margin = options.margin();
        var marginTop = margin.top();
        var marginRight = margin.right();
        var marginBottom = margin.bottom();
        var marginLeft = margin.left();
        var availableWidth = Math.max(1, terminalWidth - marginLeft - marginRight);
        var availableHeight = Math.max(1, terminalHeight - marginTop - marginBottom);
        var width = options.width() == null ? Math.min(80, availableWidth) : options.width();
        if (options.minWidth() != null) {
            width = Math.max(width, options.minWidth());
        }
        width = Math.max(1, Math.min(width, availableWidth));
        var maxHeight = options.maxHeight() == null ? null : Math.max(1, Math.min(options.maxHeight(), availableHeight));
        var row = options.row() != null
            ? options.row()
            : resolveAnchorRow(options.anchor(), overlayHeight, availableHeight, marginTop);
        var column = options.column() != null
            ? options.column()
            : resolveAnchorColumn(options.anchor(), width, availableWidth, marginLeft);
        row += options.offsetY();
        column += options.offsetX();
        row = Math.max(marginTop, Math.min(row, terminalHeight - marginBottom - overlayHeight));
        column = Math.max(marginLeft, Math.min(column, terminalWidth - marginRight - width));
        return new OverlayLayout(width, row, column, maxHeight);
    }

    private static int resolveAnchorRow(OverlayAnchor anchor, int height, int availableHeight, int marginTop) {
        return switch (anchor) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> marginTop;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> marginTop + availableHeight - height;
            case LEFT_CENTER, CENTER, RIGHT_CENTER -> marginTop + Math.floorDiv(availableHeight - height, 2);
        };
    }

    private static int resolveAnchorColumn(OverlayAnchor anchor, int width, int availableWidth, int marginLeft) {
        return switch (anchor) {
            case TOP_LEFT, LEFT_CENTER, BOTTOM_LEFT -> marginLeft;
            case TOP_RIGHT, RIGHT_CENTER, BOTTOM_RIGHT -> marginLeft + availableWidth - width;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> marginLeft + Math.floorDiv(availableWidth - width, 2);
        };
    }

    private static String compositeLine(String baseLine, String overlayLine, int startColumn, int overlayWidth, int totalWidth) {
        var before = TerminalText.padRightVisible(TerminalText.takeVisibleColumns(baseLine, startColumn), startColumn);
        var overlay = TerminalText.padRightVisible(overlayLine, overlayWidth);
        var after = TerminalText.dropVisibleColumns(baseLine, startColumn + overlayWidth);
        var result = before + SEGMENT_RESET + overlay + SEGMENT_RESET + after;
        if (TerminalText.visibleWidth(result) <= totalWidth) {
            return result;
        }
        return TerminalText.takeVisibleColumns(result, totalWidth);
    }

    private static List<String> applyLineResets(List<String> lines) {
        var result = new ArrayList<String>(lines.size());
        for (var line : lines) {
            result.add(line + SEGMENT_RESET);
        }
        return result;
    }

    private static CursorPosition extractCursorPosition(List<String> lines, int height) {
        var viewportTop = Math.max(0, lines.size() - height);
        for (var row = lines.size() - 1; row >= viewportTop; row -= 1) {
            var line = lines.get(row);
            var markerIndex = line.indexOf(CURSOR_MARKER);
            if (markerIndex < 0) {
                continue;
            }
            var beforeMarker = line.substring(0, markerIndex);
            var column = TerminalText.visibleWidth(beforeMarker);
            lines.set(row, line.substring(0, markerIndex) + line.substring(markerIndex + CURSOR_MARKER.length()));
            return new CursorPosition(row, column);
        }
        return null;
    }

    private ManagedOverlay topVisibleOverlay() {
        for (var index = overlayStack.size() - 1; index >= 0; index -= 1) {
            var overlay = overlayStack.get(index);
            if (!overlay.isHidden()) {
                return overlay;
            }
        }
        return null;
    }

    private record OverlayLayout(
        int width,
        int row,
        int column,
        Integer maxHeight
    ) {
    }

    private record RenderedOverlay(
        List<String> lines,
        int row,
        int column,
        int width
    ) {
    }

    private final class ManagedOverlay implements OverlayHandle, Overlay {
        private final Component component;
        private final OverlayOptions options;
        private final Component preFocus;
        private boolean hidden;

        private ManagedOverlay(Component component, OverlayOptions options, Component preFocus) {
            this.component = Objects.requireNonNull(component, "component");
            this.options = Objects.requireNonNull(options, "options");
            this.preFocus = preFocus;
        }

        @Override
        public void hide() {
            if (!overlayStack.remove(this)) {
                return;
            }
            if (focusedComponent == component) {
                var topVisible = topVisibleOverlay();
                setFocus(topVisible == null ? preFocus : topVisible.component());
            }
            if (!hasOverlay()) {
                terminal.hideCursor();
            }
            requestRender();
        }

        @Override
        public void setHidden(boolean hidden) {
            if (this.hidden == hidden) {
                return;
            }
            this.hidden = hidden;
            if (hidden) {
                if (focusedComponent == component) {
                    var topVisible = topVisibleOverlay();
                    setFocus(topVisible == null ? preFocus : topVisible.component());
                }
            } else {
                setFocus(component);
            }
            requestRender();
        }

        @Override
        public boolean isHidden() {
            return hidden;
        }

        @Override
        public Component component() {
            return component;
        }

        @Override
        public OverlayOptions options() {
            return options;
        }

        public Component preFocus() {
            return preFocus;
        }
    }
}
