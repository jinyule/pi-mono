package dev.pi.ai.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SseEventParser {
    private static final char BOM = '\uFEFF';

    private final StringBuilder buffer = new StringBuilder();
    private final StringBuilder dataBuffer = new StringBuilder();

    private String currentEvent = "message";
    private String lastEventId;
    private Long retryMillis;
    private boolean hasDataField;
    private boolean firstChunk = true;

    public List<SseEvent> append(String chunk) {
        Objects.requireNonNull(chunk, "chunk");
        var events = new ArrayList<SseEvent>();
        if (chunk.isEmpty()) {
            return events;
        }

        if (firstChunk && !chunk.isEmpty() && chunk.charAt(0) == BOM) {
            buffer.append(chunk.substring(1));
        } else {
            buffer.append(chunk);
        }
        firstChunk = false;

        int lineStart = 0;
        for (int index = 0; index < buffer.length(); index++) {
            var current = buffer.charAt(index);
            if (current != '\n' && current != '\r') {
                continue;
            }

            var line = buffer.substring(lineStart, index);
            if (current == '\r' && index + 1 < buffer.length() && buffer.charAt(index + 1) == '\n') {
                index++;
            }

            processLine(line, events);
            lineStart = index + 1;
        }

        if (lineStart > 0) {
            buffer.delete(0, lineStart);
        }

        return List.copyOf(events);
    }

    public List<SseEvent> finish() {
        var events = new ArrayList<SseEvent>();
        if (!buffer.isEmpty()) {
            processLine(buffer.toString(), events);
            buffer.setLength(0);
        }
        dispatch(events);
        return List.copyOf(events);
    }

    private void processLine(String line, List<SseEvent> events) {
        if (line.isEmpty()) {
            dispatch(events);
            return;
        }

        if (line.charAt(0) == ':') {
            return;
        }

        var separator = line.indexOf(':');
        var field = separator < 0 ? line : line.substring(0, separator);
        var value = separator < 0 ? "" : normalizeValue(line.substring(separator + 1));

        switch (field) {
            case "data" -> appendData(value);
            case "event" -> currentEvent = value.isBlank() ? "message" : value;
            case "id" -> {
                if (value.indexOf('\0') < 0) {
                    lastEventId = value;
                }
            }
            case "retry" -> parseRetry(value);
            default -> {
            }
        }
    }

    private void appendData(String value) {
        if (hasDataField) {
            dataBuffer.append('\n');
        }
        dataBuffer.append(value);
        hasDataField = true;
    }

    private void parseRetry(String value) {
        if (value.isBlank()) {
            return;
        }
        try {
            retryMillis = Long.parseLong(value);
        } catch (NumberFormatException ignored) {
        }
    }

    private void dispatch(List<SseEvent> events) {
        if (!hasDataField) {
            currentEvent = "message";
            dataBuffer.setLength(0);
            return;
        }

        events.add(new SseEvent(currentEvent, dataBuffer.toString(), lastEventId, retryMillis));
        currentEvent = "message";
        dataBuffer.setLength(0);
        hasDataField = false;
    }

    private static String normalizeValue(String rawValue) {
        if (!rawValue.isEmpty() && rawValue.charAt(0) == ' ') {
            return rawValue.substring(1);
        }
        return rawValue;
    }
}
