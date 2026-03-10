package dev.pi.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DiffRenderer {
    private final Terminal terminal;

    private List<String> previousLines = List.of();
    private int previousWidth;
    private int cursorRow;
    private int maxLinesRendered;
    private int previousViewportTop;
    private int fullRedrawCount;
    private boolean clearOnShrink = true;

    public DiffRenderer(Terminal terminal) {
        this.terminal = Objects.requireNonNull(terminal, "terminal");
    }

    public synchronized void render(List<String> lines) {
        Objects.requireNonNull(lines, "lines");

        var width = terminal.columns();
        var height = terminal.rows();
        var newLines = List.copyOf(new ArrayList<>(lines));
        var widthChanged = previousWidth != 0 && previousWidth != width;

        if (previousLines.isEmpty() && !widthChanged) {
            fullRender(newLines, width, height, false);
            return;
        }

        if (widthChanged) {
            fullRender(newLines, width, height, true);
            return;
        }

        if (clearOnShrink && newLines.size() < maxLinesRendered) {
            fullRender(newLines, width, height, true);
            return;
        }

        var firstChanged = -1;
        var lastChanged = -1;
        var maxLines = Math.max(newLines.size(), previousLines.size());
        for (var index = 0; index < maxLines; index += 1) {
            var oldLine = index < previousLines.size() ? previousLines.get(index) : "";
            var newLine = index < newLines.size() ? newLines.get(index) : "";
            if (!oldLine.equals(newLine)) {
                if (firstChanged == -1) {
                    firstChanged = index;
                }
                lastChanged = index;
            }
        }

        var appendedLines = newLines.size() > previousLines.size();
        if (appendedLines) {
            if (firstChanged == -1) {
                firstChanged = previousLines.size();
            }
            lastChanged = newLines.size() - 1;
        }

        if (firstChanged == -1) {
            previousViewportTop = Math.max(0, maxLinesRendered - height);
            return;
        }

        var previousContentViewportTop = Math.max(0, previousLines.size() - height);
        if (firstChanged < previousContentViewportTop) {
            fullRender(newLines, width, height, true);
            return;
        }

        if (firstChanged >= newLines.size()) {
            fullRender(newLines, width, height, true);
            return;
        }

        var viewportTop = Math.max(0, maxLinesRendered - height);
        var previousViewportTopLocal = previousViewportTop;
        var appendStart = appendedLines && firstChanged == previousLines.size() && firstChanged > 0;
        var moveTargetRow = appendStart ? firstChanged - 1 : firstChanged;
        var buffer = new StringBuilder();
        var previousViewportBottom = previousViewportTopLocal + height - 1;

        if (moveTargetRow > previousViewportBottom) {
            var currentScreenRow = Math.max(0, Math.min(height - 1, cursorRow - previousViewportTopLocal));
            var moveToBottom = height - 1 - currentScreenRow;
            if (moveToBottom > 0) {
                buffer.append("\u001b[").append(moveToBottom).append('B');
            }

            var scroll = moveTargetRow - previousViewportBottom;
            buffer.append("\r\n".repeat(scroll));
            previousViewportTopLocal += scroll;
            viewportTop += scroll;
            cursorRow = moveTargetRow;
        }

        var lineDiff = computeLineDiff(cursorRow, previousViewportTopLocal, moveTargetRow, viewportTop);
        if (lineDiff > 0) {
            buffer.append("\u001b[").append(lineDiff).append('B');
        } else if (lineDiff < 0) {
            buffer.append("\u001b[").append(-lineDiff).append('A');
        }

        buffer.append(appendStart ? "\r\n" : "\r");

        var renderEnd = Math.min(lastChanged, newLines.size() - 1);
        for (var index = firstChanged; index <= renderEnd; index += 1) {
            if (index > firstChanged) {
                buffer.append("\r\n");
            }
            buffer.append("\u001b[2K").append(newLines.get(index));
        }

        terminal.write(SynchronizedOutput.wrap(buffer.toString()));

        cursorRow = Math.max(0, newLines.size() - 1);
        maxLinesRendered = Math.max(maxLinesRendered, newLines.size());
        previousViewportTop = Math.max(0, maxLinesRendered - height);
        previousLines = newLines;
        previousWidth = width;
    }

    public synchronized void reset() {
        previousLines = List.of();
        previousWidth = 0;
        cursorRow = 0;
        maxLinesRendered = 0;
        previousViewportTop = 0;
        fullRedrawCount = 0;
    }

    public synchronized boolean clearOnShrink() {
        return clearOnShrink;
    }

    public synchronized void setClearOnShrink(boolean clearOnShrink) {
        this.clearOnShrink = clearOnShrink;
    }

    public synchronized int fullRedrawCount() {
        return fullRedrawCount;
    }

    public synchronized List<String> previousLines() {
        return previousLines;
    }

    private void fullRender(List<String> newLines, int width, int height, boolean clear) {
        fullRedrawCount += 1;
        var buffer = new StringBuilder();
        if (clear) {
            buffer.append("\u001b[3J\u001b[2J\u001b[H");
        }
        for (var index = 0; index < newLines.size(); index += 1) {
            if (index > 0) {
                buffer.append("\r\n");
            }
            buffer.append(newLines.get(index));
        }
        terminal.write(SynchronizedOutput.wrap(buffer.toString()));
        cursorRow = Math.max(0, newLines.size() - 1);
        maxLinesRendered = clear ? newLines.size() : Math.max(maxLinesRendered, newLines.size());
        previousViewportTop = Math.max(0, maxLinesRendered - height);
        previousLines = newLines;
        previousWidth = width;
    }

    private static int computeLineDiff(int currentRow, int previousViewportTop, int targetRow, int viewportTop) {
        var currentScreenRow = currentRow - previousViewportTop;
        var targetScreenRow = targetRow - viewportTop;
        return targetScreenRow - currentScreenRow;
    }
}
