package dev.pi.extension.spi;

@FunctionalInterface
public interface MessageRenderer<TMessage, TView> {
    TView render(TMessage message, MessageRenderContext context);
}
