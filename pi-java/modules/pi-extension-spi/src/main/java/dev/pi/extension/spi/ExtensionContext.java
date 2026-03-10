package dev.pi.extension.spi;

import dev.pi.session.SessionManager;
import dev.pi.session.SettingsManager;
import java.nio.file.Path;
import java.util.Optional;

public interface ExtensionContext {
    Path cwd();

    SessionManager sessionManager();

    SettingsManager settingsManager();

    default Optional<ExtensionUiContext> ui() {
        return Optional.empty();
    }

    default boolean hasUi() {
        return ui().isPresent();
    }
}
