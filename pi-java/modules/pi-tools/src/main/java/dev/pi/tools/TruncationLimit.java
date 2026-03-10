package dev.pi.tools;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum TruncationLimit {
    LINES,
    BYTES;

    @JsonValue
    public String jsonValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
