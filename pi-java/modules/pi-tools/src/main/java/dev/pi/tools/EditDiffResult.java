package dev.pi.tools;

public record EditDiffResult(
    String diff,
    Integer firstChangedLine
) implements EditDiffPreview {
}
