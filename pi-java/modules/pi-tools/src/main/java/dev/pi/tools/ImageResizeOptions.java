package dev.pi.tools;

public record ImageResizeOptions(
    int maxWidth,
    int maxHeight,
    int maxBytes,
    int jpegQuality
) {
    public static final int DEFAULT_MAX_BYTES = (int) (4.5 * 1024 * 1024);

    public ImageResizeOptions {
        if (maxWidth <= 0) {
            throw new IllegalArgumentException("maxWidth must be positive");
        }
        if (maxHeight <= 0) {
            throw new IllegalArgumentException("maxHeight must be positive");
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        if (jpegQuality <= 0 || jpegQuality > 100) {
            throw new IllegalArgumentException("jpegQuality must be between 1 and 100");
        }
    }

    public static ImageResizeOptions defaults() {
        return new ImageResizeOptions(2000, 2000, DEFAULT_MAX_BYTES, 80);
    }
}
