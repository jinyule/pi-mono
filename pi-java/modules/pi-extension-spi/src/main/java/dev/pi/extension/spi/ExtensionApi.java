package dev.pi.extension.spi;

public interface ExtensionApi {
    void registerTool(ToolDefinition<?> toolDefinition);

    void registerCommand(CommandDefinition commandDefinition);

    <TMessage, TView> void registerMessageRenderer(String customType, MessageRenderer<TMessage, TView> renderer);
}
