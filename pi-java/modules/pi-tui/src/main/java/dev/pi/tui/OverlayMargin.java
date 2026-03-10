package dev.pi.tui;

public record OverlayMargin(
    int top,
    int right,
    int bottom,
    int left
) {
    public static OverlayMargin uniform(int value) {
        return new OverlayMargin(value, value, value, value);
    }
}
