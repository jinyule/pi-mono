package dev.pi.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CacheRetention {
    NONE("none"),
    SHORT("short"),
    LONG("long");

    private final String value;

    CacheRetention(String value) {
        this.value = value;
    }

    @JsonCreator
    public static CacheRetention fromValue(String value) {
        for (var retention : values()) {
            if (retention.value.equals(value)) {
                return retention;
            }
        }
        throw new IllegalArgumentException("Unknown cache retention: " + value);
    }

    @JsonValue
    public String value() {
        return value;
    }
}

