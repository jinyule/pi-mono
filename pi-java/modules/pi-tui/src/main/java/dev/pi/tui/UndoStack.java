package dev.pi.tui;

import java.util.ArrayDeque;

public final class UndoStack<S> {
    private final ArrayDeque<S> stack = new ArrayDeque<>();

    public void push(S state) {
        stack.addLast(state);
    }

    public S pop() {
        return stack.pollLast();
    }

    public void clear() {
        stack.clear();
    }
}
