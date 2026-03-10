package dev.pi.tools;

public record FindToolDetails(
    TruncationResult truncation,
    Integer resultLimitReached
) {
}
