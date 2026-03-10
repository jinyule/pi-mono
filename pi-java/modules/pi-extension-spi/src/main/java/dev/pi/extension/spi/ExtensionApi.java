package dev.pi.extension.spi;

public interface ExtensionApi {
    <E extends ExtensionEvent, R> void on(Class<E> eventType, ExtensionHandler<E, R> handler);

    void registerTool(ToolDefinition<?> toolDefinition);

    void registerCommand(CommandDefinition commandDefinition);

    <TMessage, TView> void registerMessageRenderer(String customType, MessageRenderer<TMessage, TView> renderer);
}
