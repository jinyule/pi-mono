package dev.pi.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ThinkingLevel {
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh");

    private final String value;

    ThinkingLevel(String value) {
        this.value = value;
    }

    @JsonCreator
    public static ThinkingLevel fromValue(String value) {
        for (var level : values()) {
            if (level.value.equals(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown thinking level: " + value);
    }

    @JsonValue
    public String value() {
        return value;
    }
}

