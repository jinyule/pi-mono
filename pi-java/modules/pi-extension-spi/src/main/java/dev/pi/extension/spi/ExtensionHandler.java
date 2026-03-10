package dev.pi.extension.spi;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ExtensionHandler<E extends ExtensionEvent, R> {
    CompletionStage<R> handle(E event, ExtensionContext context);
}
