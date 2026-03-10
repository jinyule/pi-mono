package dev.pi.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public final class Text implements Component {
    private String text;
    private final int paddingX;
    private final int paddingY;
    private UnaryOperator<String> background;

    private String cachedText;
    private Integer cachedWidth;
    private List<String> cachedLines;

    public Text() {
        this("", 1, 1, null);
    }

    public Text(String text) {
        this(text, 1, 1, null);
    }

    public Text(String text, int paddingX, int paddingY, UnaryOperator<String> background) {
        this.text = text == null ? "" : text;
        this.paddingX = paddingX;
        this.paddingY = paddingY;
        this.background = background;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
        invalidate();
    }

    public void setBackground(UnaryOperator<String> background) {
        this.background = background;
        invalidate();
    }

    @Override
    public void invalidate() {
        cachedText = null;
        cachedWidth = null;
        cachedLines = null;
    }

    @Override
    public List<String> render(int width) {
        if (cachedLines != null && text.equals(cachedText) && Integer.valueOf(width).equals(cachedWidth)) {
            return cachedLines;
        }

        if (text.isBlank()) {
            cachedText = text;
            cachedWidth = width;
            cachedLines = List.of();
            return cachedLines;
        }

        var normalized = text.replace("\t", "   ");
        var contentWidth = Math.max(1, width - paddingX * 2);
        var wrapped = TerminalText.wrapText(normalized, contentWidth);
        var leftPadding = " ".repeat(Math.max(0, paddingX));
        var rightPadding = " ".repeat(Math.max(0, paddingX));
        var contentLines = new ArrayList<String>();

        for (var line : wrapped) {
            var withMargins = leftPadding + line + rightPadding;
            contentLines.add(background == null
                ? TerminalText.padRightVisible(withMargins, width)
                : TerminalText.applyBackgroundToLine(withMargins, width, background));
        }

        var emptyLine = " ".repeat(Math.max(0, width));
        var result = new ArrayList<String>();
        for (var index = 0; index < paddingY; index += 1) {
            result.add(background == null ? emptyLine : TerminalText.applyBackgroundToLine(emptyLine, width, background));
        }
        result.addAll(contentLines);
        for (var index = 0; index < paddingY; index += 1) {
            result.add(background == null ? emptyLine : TerminalText.applyBackgroundToLine(emptyLine, width, background));
        }

        cachedText = text;
        cachedWidth = width;
        cachedLines = List.copyOf(result);
        return cachedLines;
    }
}
