package dev.pi.extension.spi;

public record SessionStartEvent() implements ExtensionEvent {
    @Override
    public String type() {
        return "session_start";
    }
}
