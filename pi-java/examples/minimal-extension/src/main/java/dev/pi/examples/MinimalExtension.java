package dev.pi.examples;

import dev.pi.extension.spi.CommandDefinition;
import dev.pi.extension.spi.ExtensionApi;
import dev.pi.extension.spi.FlagDefinition;
import dev.pi.extension.spi.PiExtension;
import dev.pi.extension.spi.SessionStartEvent;
import dev.pi.extension.spi.ShortcutDefinition;
import java.util.concurrent.CompletableFuture;

public final class MinimalExtension implements PiExtension {
    private static final String COMMAND_NAME = "example.command";

    @Override
    public String id() {
        return "minimal-extension";
    }

    @Override
    public String version() {
        return "0.1.0";
    }

    @Override
    public void register(ExtensionApi api) {
        api.on(SessionStartEvent.class, (event, context) -> CompletableFuture.completedFuture(null));
        api.registerCommand(new CommandDefinition(
            COMMAND_NAME,
            "Minimal example command",
            (arguments, context) -> CompletableFuture.completedFuture(null)
        ));
        api.registerFlag(new FlagDefinition(
            "example.enabled",
            "Enable the minimal example extension",
            FlagDefinition.Type.BOOLEAN,
            Boolean.TRUE
        ));
        api.registerShortcut(new ShortcutDefinition(
            "ctrl+alt+e",
            "Run the example shortcut",
            context -> CompletableFuture.completedFuture(null)
        ));
    }
}
