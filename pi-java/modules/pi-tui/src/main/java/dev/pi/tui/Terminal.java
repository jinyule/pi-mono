package dev.pi.tui;

public interface Terminal {
    void start(InputHandler onInput, Runnable onResize);

    void stop();

    void write(String data);

    int columns();

    int rows();

    default void moveBy(int lines) {
    }

    default void hideCursor() {
    }

    default void showCursor() {
    }

    default void clearLine() {
    }

    default void clearFromCursor() {
    }

    default void clearScreen() {
    }

    default void setTitle(String title) {
    }
}
