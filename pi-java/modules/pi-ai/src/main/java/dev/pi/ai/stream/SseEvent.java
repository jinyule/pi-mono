package dev.pi.ai.stream;

public record SseEvent(
    String event,
    String data,
    String id,
    Long retryMillis
) {
    public SseEvent {
        event = event == null || event.isBlank() ? "message" : event;
        data = data == null ? "" : data;
    }
}
