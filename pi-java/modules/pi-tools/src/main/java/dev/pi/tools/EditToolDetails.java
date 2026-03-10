package dev.pi.tools;

import java.util.Objects;

public record EditToolDetails(
    String diff,
    Integer firstChangedLine
) {
    public EditToolDetails {
        diff = Objects.requireNonNull(diff, "diff");
    }
}
