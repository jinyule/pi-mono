package dev.pi.ai.model;

import java.util.Objects;

public record Usage(
    int input,
    int output,
    int cacheRead,
    int cacheWrite,
    int totalTokens,
    Cost cost
) {
    public Usage {
        cost = Objects.requireNonNullElseGet(cost, () -> new Cost(0.0, 0.0, 0.0, 0.0, 0.0));
    }

    public record Cost(
        double input,
        double output,
        double cacheRead,
        double cacheWrite,
        double total
    ) {}
}

