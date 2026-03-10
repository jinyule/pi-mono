package dev.pi.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

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

    public static String truncateToWidth(String text, int width) {
        return truncateToWidth(text, width, "...");
    }

    public static String truncateToWidth(String text, int width, String ellipsis) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(ellipsis, "ellipsis");
        if (width <= 0) {
            return "";
        }
        if (visibleWidth(text) <= width) {
            return text;
        }
        var ellipsisWidth = visibleWidth(ellipsis);
        if (ellipsisWidth >= width) {
            return takeVisibleColumns(ellipsis, width);
        }
        return takeVisibleColumns(text, width - ellipsisWidth) + ellipsis;
    }

    public static List<String> wrapText(String text, int width) {
        Objects.requireNonNull(text, "text");
        if (width <= 0) {
            return List.of();
        }

        var result = new ArrayList<String>();
        for (var rawLine : text.split("\\R", -1)) {
            if (rawLine.isEmpty()) {
                result.add("");
                continue;
            }

            var remaining = rawLine;
            var currentLine = new StringBuilder();
            while (!remaining.isEmpty()) {
                var token = nextToken(remaining);
                remaining = remaining.substring(token.length());
                if (token.isBlank() && currentLine.length() == 0) {
                    continue;
                }

                var candidate = currentLine + token;
                if (visibleWidth(candidate) <= width) {
                    currentLine.append(token);
                    continue;
                }

                if (currentLine.length() > 0) {
                    result.add(rstrip(currentLine.toString()));
                    currentLine = new StringBuilder();
                }

                if (visibleWidth(token) <= width) {
                    if (!token.isBlank()) {
                        currentLine.append(token);
                    }
                    continue;
                }

                var tokenRemainder = token;
                while (!tokenRemainder.isEmpty()) {
                    var segment = takeVisibleColumns(tokenRemainder, width);
                    result.add(rstrip(segment));
                    tokenRemainder = dropVisibleColumns(tokenRemainder, width);
                }
            }

            if (currentLine.length() > 0) {
                result.add(rstrip(currentLine.toString()));
            }
        }

        return result.isEmpty() ? List.of("") : List.copyOf(result);
    }

    public static String applyBackgroundToLine(String text, int width, UnaryOperator<String> background) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(background, "background");
        return background.apply(padRightVisible(takeVisibleColumns(text, width), width));
    }

    private static String nextToken(String text) {
        var whitespace = Character.isWhitespace(text.charAt(0));
        var index = 0;
        while (index < text.length() && Character.isWhitespace(text.charAt(index)) == whitespace) {
            index += 1;
        }
        return text.substring(0, index);
    }

    private static String rstrip(String text) {
        var index = text.length();
        while (index > 0 && Character.isWhitespace(text.charAt(index - 1))) {
            index -= 1;
        }
        return text.substring(0, index);
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
