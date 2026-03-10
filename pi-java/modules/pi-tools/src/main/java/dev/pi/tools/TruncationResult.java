package dev.pi.tools;

public record TruncationResult(
    String content,
    boolean truncated,
    TruncationLimit truncatedBy,
    int totalLines,
    int totalBytes,
    int outputLines,
    int outputBytes,
    boolean lastLinePartial,
    boolean firstLineExceedsLimit,
    int maxLines,
    int maxBytes
) {
}
