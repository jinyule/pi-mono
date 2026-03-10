package dev.pi.tui;

import java.util.ArrayList;
import java.util.List;

public final class VirtualTerminal implements Terminal {
    private static final char ESC = '\u001b';

    private final List<StringBuilder> scrollBuffer = new ArrayList<>();
    private InputHandler inputHandler;
    private Runnable resizeHandler;

    private int columns;
    private int rows;
    private int cursorX;
    private int cursorY;
    private boolean cursorVisible;

    public VirtualTerminal() {
        this(80, 24);
    }

    public VirtualTerminal(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
        ensureRow(0);
    }

    @Override
    public void start(InputHandler onInput, Runnable onResize) {
        this.inputHandler = onInput;
        this.resizeHandler = onResize;
    }

    @Override
    public void stop() {
        this.inputHandler = null;
        this.resizeHandler = null;
    }

    @Override
    public void write(String data) {
        parse(data);
    }

    @Override
    public int columns() {
        return columns;
    }

    @Override
    public int rows() {
        return rows;
    }

    @Override
    public boolean kittyProtocolActive() {
        return true;
    }

    @Override
    public void moveBy(int lines) {
        cursorY = Math.max(0, cursorY + lines);
        ensureRow(cursorY);
    }

    @Override
    public void hideCursor() {
        cursorVisible = false;
    }

    @Override
    public void showCursor() {
        cursorVisible = true;
    }

    @Override
    public void clearLine() {
        ensureRow(cursorY);
        var line = scrollBuffer.get(cursorY);
        while (line.length() > cursorX) {
            line.deleteCharAt(line.length() - 1);
        }
    }

    @Override
    public void clearFromCursor() {
        clearLine();
        for (var row = cursorY + 1; row < scrollBuffer.size(); row += 1) {
            scrollBuffer.get(row).setLength(0);
        }
    }

    @Override
    public void clearScreen() {
        scrollBuffer.clear();
        cursorX = 0;
        cursorY = 0;
        ensureRow(0);
    }

    public void sendInput(String data) {
        if (inputHandler != null) {
            inputHandler.onInput(data);
        }
    }

    public void resize(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
        if (resizeHandler != null) {
            resizeHandler.run();
        }
    }

    public List<String> getViewport() {
        var viewportTop = Math.max(0, scrollBuffer.size() - rows);
        var lines = new ArrayList<String>();
        for (var row = 0; row < rows; row += 1) {
            var index = viewportTop + row;
            if (index < scrollBuffer.size()) {
                lines.add(rstrip(scrollBuffer.get(index).toString()));
            } else {
                lines.add("");
            }
        }
        return List.copyOf(lines);
    }

    public List<String> getScrollBuffer() {
        return scrollBuffer.stream().map(line -> rstrip(line.toString())).toList();
    }

    public CursorPosition getCursorPosition() {
        return new CursorPosition(cursorY, cursorX);
    }

    public boolean cursorVisible() {
        return cursorVisible;
    }

    private void parse(String data) {
        for (var index = 0; index < data.length(); ) {
            var ch = data.charAt(index);
            if (ch == ESC) {
                index = consumeEscape(data, index);
                continue;
            }
            if (ch == '\r') {
                cursorX = 0;
                index += 1;
                continue;
            }
            if (ch == '\n') {
                cursorY += 1;
                cursorX = 0;
                ensureRow(cursorY);
                index += 1;
                continue;
            }
            writeCharacter(ch);
            index += 1;
        }
    }

    private int consumeEscape(String text, int start) {
        if (start + 1 >= text.length()) {
            return text.length();
        }
        var next = text.charAt(start + 1);
        if (next == '[') {
            var end = start + 2;
            while (end < text.length()) {
                var ch = text.charAt(end);
                if (ch >= 0x40 && ch <= 0x7e) {
                    handleCsi(text.substring(start + 2, end), ch);
                    return end + 1;
                }
                end += 1;
            }
            return text.length();
        }
        if (next == ']') {
            var end = start + 2;
            while (end < text.length()) {
                var ch = text.charAt(end);
                if (ch == '\u0007') {
                    return end + 1;
                }
                if (ch == ESC && end + 1 < text.length() && text.charAt(end + 1) == '\\') {
                    return end + 2;
                }
                end += 1;
            }
            return text.length();
        }
        if (next == '_' || next == 'P') {
            var end = start + 2;
            while (end < text.length()) {
                if (text.charAt(end) == ESC && end + 1 < text.length() && text.charAt(end + 1) == '\\') {
                    return end + 2;
                }
                end += 1;
            }
            return text.length();
        }
        return Math.min(text.length(), start + 2);
    }

    private void handleCsi(String params, char command) {
        if (params.startsWith("?")) {
            if ("?25h".equals("?" + params.substring(1) + command)) {
                cursorVisible = true;
            } else if ("?25l".equals("?" + params.substring(1) + command)) {
                cursorVisible = false;
            }
            return;
        }

        var parts = params.isEmpty() ? new String[0] : params.split(";");
        var first = parseParam(parts, 0, 1);
        switch (command) {
            case 'A' -> cursorY = Math.max(0, cursorY - first);
            case 'B' -> {
                cursorY += first;
                ensureRow(cursorY);
            }
            case 'C' -> cursorX = Math.min(columns - 1, cursorX + first);
            case 'D' -> cursorX = Math.max(0, cursorX - first);
            case 'G' -> cursorX = Math.max(0, first - 1);
            case 'H' -> {
                cursorY = Math.max(0, parseParam(parts, 0, 1) - 1);
                cursorX = Math.max(0, parseParam(parts, 1, 1) - 1);
                ensureRow(cursorY);
            }
            case 'J' -> clearScreen();
            case 'K' -> {
                ensureRow(cursorY);
                var mode = parseParam(parts, 0, 0);
                if (mode == 2) {
                    scrollBuffer.get(cursorY).setLength(0);
                } else {
                    clearLine();
                }
            }
            case 'm' -> {
            }
            default -> {
            }
        }
    }

    private void writeCharacter(char ch) {
        ensureRow(cursorY);
        var line = scrollBuffer.get(cursorY);
        while (line.length() < cursorX) {
            line.append(' ');
        }
        if (cursorX < line.length()) {
            line.setCharAt(cursorX, ch);
        } else {
            line.append(ch);
        }
        cursorX += 1;
        if (cursorX >= columns) {
            cursorX = 0;
            cursorY += 1;
            ensureRow(cursorY);
        }
    }

    private void ensureRow(int row) {
        while (scrollBuffer.size() <= row) {
            scrollBuffer.add(new StringBuilder());
        }
    }

    private int parseParam(String[] parts, int index, int fallback) {
        if (index >= parts.length || parts[index].isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String rstrip(String value) {
        var end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end -= 1;
        }
        return value.substring(0, end);
    }
}
