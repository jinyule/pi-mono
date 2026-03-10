package dev.pi.tui;

import java.util.Objects;

public final class SynchronizedOutput {
    public static final String BEGIN = "\u001b[?2026h";
    public static final String END = "\u001b[?2026l";

    private SynchronizedOutput() {
    }

    public static String wrap(String content) {
        return BEGIN + Objects.requireNonNull(content, "content") + END;
    }
}
