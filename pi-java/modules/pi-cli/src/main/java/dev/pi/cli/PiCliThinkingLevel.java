package dev.pi.cli;

import dev.pi.ai.model.ThinkingLevel;

public enum PiCliThinkingLevel {
    OFF("off", null),
    MINIMAL("minimal", ThinkingLevel.MINIMAL),
    LOW("low", ThinkingLevel.LOW),
    MEDIUM("medium", ThinkingLevel.MEDIUM),
    HIGH("high", ThinkingLevel.HIGH),
    XHIGH("xhigh", ThinkingLevel.XHIGH);

    private final String value;
    private final ThinkingLevel reasoningLevel;

    PiCliThinkingLevel(String value, ThinkingLevel reasoningLevel) {
        this.value = value;
        this.reasoningLevel = reasoningLevel;
    }

    public static PiCliThinkingLevel fromValue(String value) {
        for (var level : values()) {
            if (level.value.equals(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown CLI thinking level: " + value);
    }

    public String value() {
        return value;
    }

    public ThinkingLevel toReasoningLevel() {
        return reasoningLevel;
    }
}
