package dev.pi.tui;

public interface SelectListTheme {
    String selectedPrefix(String text);

    String selectedText(String text);

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
