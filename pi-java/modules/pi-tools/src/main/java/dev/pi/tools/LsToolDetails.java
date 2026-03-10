package dev.pi.tools;

public record LsToolDetails(
    TruncationResult truncation,
    Integer entryLimitReached
) {
}
