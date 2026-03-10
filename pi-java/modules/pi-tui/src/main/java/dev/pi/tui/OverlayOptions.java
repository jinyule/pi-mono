package dev.pi.tui;

public record OverlayOptions(
    Integer width,
    Integer minWidth,
    Integer maxHeight,
    OverlayAnchor anchor,
    int offsetX,
    int offsetY,
    Integer row,
    Integer column,
    OverlayMargin margin
) {
    public OverlayOptions {
        anchor = anchor == null ? OverlayAnchor.CENTER : anchor;
        margin = margin == null ? OverlayMargin.uniform(0) : margin;
    }

    public static OverlayOptions defaults() {
        return new OverlayOptions(null, null, null, OverlayAnchor.CENTER, 0, 0, null, null, OverlayMargin.uniform(0));
    }
}
