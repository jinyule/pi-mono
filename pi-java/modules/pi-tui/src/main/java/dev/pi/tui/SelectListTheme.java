package dev.pi.tui;

public interface SelectListTheme {
    String selectedPrefix(String text);

    String selectedText(String text);

    default String compactDescription(String text, int width) {
        return TerminalText.truncateToWidth(text, Math.max(0, width), "");
    }

    default boolean rightAlignDescription() {
        return true;
    }

    default String selectedDescription(String text) {
        return description(text);
    }

    String description(String text);

    String scrollInfo(String text);

    String noMatch(String text);
}
