package dev.pi.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThinkingBudgets(
    Integer minimal,
    Integer low,
    Integer medium,
    Integer high
) {}

