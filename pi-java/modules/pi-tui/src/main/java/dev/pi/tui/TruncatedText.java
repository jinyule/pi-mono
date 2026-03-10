package dev.pi.tui;

import java.util.ArrayList;
import java.util.List;

public final class TruncatedText implements Component {
    private final String text;
    private final int paddingX;
    private final int paddingY;

    public TruncatedText(String text) {
        this(text, 0, 0);
    }

    public TruncatedText(String text, int paddingX, int paddingY) {
        this.text = text == null ? "" : text;
        this.paddingX = paddingX;
        this.paddingY = paddingY;
    }

    @Override
    public List<String> render(int width) {
        var result = new ArrayList<String>();
        var emptyLine = " ".repeat(Math.max(0, width));
        for (var index = 0; index < paddingY; index += 1) {
            result.add(emptyLine);
        }

        var availableWidth = Math.max(1, width - paddingX * 2);
        var newlineIndex = text.indexOf('\n');
        var singleLineText = newlineIndex >= 0 ? text.substring(0, newlineIndex) : text;
        var displayText = TerminalText.truncateToWidth(singleLineText, availableWidth);
        var withPadding = " ".repeat(Math.max(0, paddingX)) + displayText + " ".repeat(Math.max(0, paddingX));
        result.add(TerminalText.padRightVisible(withPadding, width));

        for (var index = 0; index < paddingY; index += 1) {
            result.add(emptyLine);
        }

        return List.copyOf(result);
    }
}
