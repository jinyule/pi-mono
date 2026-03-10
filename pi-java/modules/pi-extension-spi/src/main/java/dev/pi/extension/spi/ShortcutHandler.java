package dev.pi.extension.spi;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ShortcutHandler {
    CompletionStage<Void> execute(ExtensionContext context);
}
