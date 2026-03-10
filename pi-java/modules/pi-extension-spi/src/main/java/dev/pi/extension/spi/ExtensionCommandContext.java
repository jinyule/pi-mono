package dev.pi.extension.spi;

import java.util.concurrent.CompletionStage;

public interface ExtensionCommandContext extends ExtensionContext {
    CompletionStage<Void> reload();
}
