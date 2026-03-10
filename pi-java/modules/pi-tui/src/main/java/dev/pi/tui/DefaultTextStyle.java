package dev.pi.tui;

import java.util.function.UnaryOperator;

public record DefaultTextStyle(
    UnaryOperator<String> color,
    UnaryOperator<String> background,
    boolean bold,
    boolean italic,
    boolean strikethrough,
    boolean underline
) {
    public DefaultTextStyle {
        color = color == null ? UnaryOperator.identity() : color;
        background = background == null ? null : background;
    }
}
