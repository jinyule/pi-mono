package dev.pi.extension.spi;

public record SessionShutdownEvent() implements ExtensionEvent {
    @Override
    public String type() {
        return "session_shutdown";
    }
}
