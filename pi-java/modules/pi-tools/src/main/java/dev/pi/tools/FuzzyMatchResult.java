package dev.pi.tools;

public record FuzzyMatchResult(
    boolean found,
    int index,
    int matchLength,
    boolean usedFuzzyMatch,
    String contentForReplacement
) {
}
