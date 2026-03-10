package dev.pi.tools;

public record ResizedImage(
    byte[] data,
    String mimeType,
    int originalWidth,
    int originalHeight,
    int width,
    int height,
    boolean wasResized
) {
}
