package dev.pi.tools;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadToolDetails(
    TruncationResult truncation
) {
}
