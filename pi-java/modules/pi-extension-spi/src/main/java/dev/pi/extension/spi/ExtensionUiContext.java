package dev.pi.extension.spi;

public interface ExtensionUiContext {
    void info(String message);

    void warn(String message);

    void error(String message);
}
