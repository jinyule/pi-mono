package dev.pi.tools;

public record GrepToolDetails(
    TruncationResult truncation,
    Integer matchLimitReached,
    Boolean linesTruncated
) {
}
