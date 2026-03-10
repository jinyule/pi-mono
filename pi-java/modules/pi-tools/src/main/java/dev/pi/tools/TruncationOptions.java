package dev.pi.tools;

public record TruncationOptions(
    int maxLines,
    int maxBytes
) {
    public TruncationOptions {
        if (maxLines <= 0) {
            throw new IllegalArgumentException("maxLines must be positive");
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
    }

    public static TruncationOptions defaults() {
        return new TruncationOptions(TextTruncator.DEFAULT_MAX_LINES, TextTruncator.DEFAULT_MAX_BYTES);
    }
}
