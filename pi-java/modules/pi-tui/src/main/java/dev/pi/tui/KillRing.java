package dev.pi.tui;

import java.util.ArrayDeque;

public final class KillRing {
    private final ArrayDeque<String> ring = new ArrayDeque<>();

    public void push(String text, boolean prepend, boolean accumulate) {
        if (text == null || text.isEmpty()) {
            return;
        }

        if (accumulate && !ring.isEmpty()) {
            var last = ring.removeLast();
            ring.addLast(prepend ? text + last : last + text);
            return;
        }

        ring.addLast(text);
    }

    public String peek() {
        return ring.peekLast();
    }

    public void rotate() {
        if (ring.size() <= 1) {
            return;
        }
        var last = ring.removeLast();
        ring.addFirst(last);
    }

    public int size() {
        return ring.size();
    }
}
