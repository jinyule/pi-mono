package dev.pi.extension.spi;

import java.util.Optional;

public interface ExtensionApi {
    <E extends ExtensionEvent, R> void on(Class<E> eventType, ExtensionHandler<E, R> handler);

    void registerTool(ToolDefinition<?> toolDefinition);

    void registerCommand(CommandDefinition commandDefinition);

    void registerShortcut(ShortcutDefinition shortcutDefinition);

    void registerFlag(FlagDefinition flagDefinition);

    Optional<Object> getFlag(String name);

    <TMessage, TView> void registerMessageRenderer(String customType, MessageRenderer<TMessage, TView> renderer);
}
