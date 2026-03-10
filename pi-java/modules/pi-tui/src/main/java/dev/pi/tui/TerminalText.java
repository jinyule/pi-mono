package dev.pi.tui;

import java.util.Objects;

public final class TerminalText {
    private static final char ESC = '\u001b';

    private TerminalText() {
    }

    public static int visibleWidth(String text) {
        Objects.requireNonNull(text, "text");
        var width = 0;
        var index = 0;
        while (index < text.length()) {
            var ch = text.charAt(index);
            if (ch == ESC) {
                index = consumeEscape(text, index);
                continue;
            }
            var codePoint = text.codePointAt(index);
            width += 1;
            index += Character.charCount(codePoint);
        }
        return width;
    }

    public static String takeVisibleColumns(String text, int width) {
        Objects.requireNonNull(text, "text");
        if (width <= 0) {
            return "";
        }
        var result = new StringBuilder();
        var visible = 0;
        var index = 0;
        while (index < text.length() && visible < width) {
            var ch = text.charAt(index);
            if (ch == ESC) {
                var next = consumeEscape(text, index);
                result.append(text, index, next);
                index = next;
                continue;
            }
            var codePoint = text.codePointAt(index);
            var count = Character.charCount(codePoint);
            result.append(text, index, index + count);
            visible += 1;
            index += count;
        }
        return result.toString();
    }

    public static String dropVisibleColumns(String text, int width) {
        Objects.requireNonNull(text, "text");
        if (width <= 0) {
            return text;
        }
        var visible = 0;
        var index = 0;
        while (index < text.length() && visible < width) {
            var ch = text.charAt(index);
            if (ch == ESC) {
                index = consumeEscape(text, index);
                continue;
            }
            var codePoint = text.codePointAt(index);
            visible += 1;
            index += Character.charCount(codePoint);
        }
        return text.substring(index);
    }

    public static String padRightVisible(String text, int width) {
        Objects.requireNonNull(text, "text");
        var visible = visibleWidth(text);
        if (visible >= width) {
            return text;
        }
        return text + " ".repeat(width - visible);
    }

    private static int consumeEscape(String text, int start) {
        if (start + 1 >= text.length()) {
            return start + 1;
        }
        var next = text.charAt(start + 1);
        if (next == '[') {
            return consumeCsi(text, start + 2);
        }
        if (next == ']') {
            return consumeOsc(text, start + 2);
        }
        if (next == '_' || next == 'P') {
            return consumeStringTerminated(text, start + 2);
        }
        if (next == 'O') {
            return Math.min(text.length(), start + 3);
        }
        return Math.min(text.length(), start + 2);
    }

    private static int consumeCsi(String text, int index) {
        var current = index;
        while (current < text.length()) {
            var ch = text.charAt(current);
            if (ch >= 0x40 && ch <= 0x7e) {
                return current + 1;
            }
            current += 1;
        }
        return text.length();
    }

    private static int consumeOsc(String text, int index) {
        var current = index;
        while (current < text.length()) {
            var ch = text.charAt(current);
            if (ch == '\u0007') {
                return current + 1;
            }
            if (ch == ESC && current + 1 < text.length() && text.charAt(current + 1) == '\\') {
                return current + 2;
            }
            current += 1;
        }
        return text.length();
    }

    private static int consumeStringTerminated(String text, int index) {
        var current = index;
        while (current < text.length()) {
            if (text.charAt(current) == ESC && current + 1 < text.length() && text.charAt(current + 1) == '\\') {
                return current + 2;
            }
            current += 1;
        }
        return text.length();
    }
}
