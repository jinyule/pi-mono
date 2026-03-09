package dev.pi.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Transport {
    SSE("sse"),
    WEBSOCKET("websocket"),
    AUTO("auto");

    private final String value;

    Transport(String value) {
        this.value = value;
    }

    @JsonCreator
    public static Transport fromValue(String value) {
        for (var transport : values()) {
            if (transport.value.equals(value)) {
                return transport;
            }
        }
        throw new IllegalArgumentException("Unknown transport: " + value);
    }

    @JsonValue
    public String value() {
        return value;
    }
}

