package dev.pi.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EditToolDetails(
    String diff,
    Integer firstChangedLine
) {
    public EditToolDetails {
        diff = Objects.requireNonNull(diff, "diff");
    }
}
