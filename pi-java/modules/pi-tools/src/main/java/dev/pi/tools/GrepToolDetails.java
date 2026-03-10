package dev.pi.tools;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GrepToolDetails(
    TruncationResult truncation,
    Integer matchLimitReached,
    Boolean linesTruncated
) {
}
