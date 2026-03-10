package dev.pi.tools;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BashToolDetails(
    TruncationResult truncation,
    String fullOutputPath
) {
}
