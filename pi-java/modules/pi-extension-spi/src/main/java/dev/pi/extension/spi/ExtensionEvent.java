package dev.pi.extension.spi;

public sealed interface ExtensionEvent permits ResourcesDiscoverEvent, SessionShutdownEvent, SessionStartEvent {
    String type();
}
