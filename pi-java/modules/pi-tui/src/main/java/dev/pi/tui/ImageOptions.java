package dev.pi.tui;

public record ImageOptions(
    Integer maxWidthCells,
    Integer maxHeightCells,
    String filename,
    Integer imageId
) {
    public static ImageOptions defaults() {
        return new ImageOptions(null, null, null, null);
    }
}
