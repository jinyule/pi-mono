package dev.pi.extension.spi;

import java.util.List;
import java.util.Objects;

public record ExtensionEventDispatchResult<R>(
    List<R> results,
    List<ExtensionEventFailure> failures
) {
    public ExtensionEventDispatchResult {
        results = List.copyOf(Objects.requireNonNull(results, "results"));
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
    }
}
