package dev.pi.extension.spi;

import java.nio.file.Path;
import java.util.Objects;

public record ResourcesDiscoverEvent(
    Path cwd,
    Reason reason
) implements ExtensionEvent {
    public ResourcesDiscoverEvent {
        cwd = Objects.requireNonNull(cwd, "cwd");
        reason = Objects.requireNonNull(reason, "reason");
    }

    @Override
    public String type() {
        return "resources_discover";
    }

    public enum Reason {
        STARTUP,
        RELOAD
    }
}
