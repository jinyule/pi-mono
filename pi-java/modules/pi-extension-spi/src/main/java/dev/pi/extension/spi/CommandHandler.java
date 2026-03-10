package dev.pi.extension.spi;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface CommandHandler {
    CompletionStage<Void> execute(String arguments, ExtensionCommandContext context);
}
