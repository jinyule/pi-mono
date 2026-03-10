package dev.pi.tui;

public interface SelectListTheme {
    String selectedPrefix(String text);

    String selectedText(String text);

    String description(String text);

    String scrollInfo(String text);

    String noMatch(String text);
}
