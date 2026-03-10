package dev.pi.tui;

public record ImageRenderOptions(
    Integer maxWidthCells,
    Integer maxHeightCells,
    boolean preserveAspectRatio,
    Integer imageId
) {
    public static ImageRenderOptions defaults() {
        return new ImageRenderOptions(null, null, true, null);
    }
}
