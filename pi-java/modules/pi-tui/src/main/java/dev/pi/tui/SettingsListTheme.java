package dev.pi.tui;

public interface SettingsListTheme {
    String label(String text, boolean selected);

    String value(String text, boolean selected);

    String description(String text);

    String cursor();

    String hint(String text);
}
