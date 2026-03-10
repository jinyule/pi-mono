package dev.pi.extension.spi;

import java.util.Objects;

public interface PiExtension {
    String id();

    default String version() {
        return "0.0.0";
    }

    default void register(ExtensionApi api) {
        Objects.requireNonNull(api, "api");
    }
}
