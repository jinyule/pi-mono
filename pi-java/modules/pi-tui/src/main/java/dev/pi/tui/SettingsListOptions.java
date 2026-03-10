package dev.pi.tui;

public record SettingsListOptions(
    boolean enableSearch
) {
    public static SettingsListOptions defaults() {
        return new SettingsListOptions(false);
    }
}
