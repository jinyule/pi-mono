package dev.pi.tui;

import java.util.List;

public interface Component {
    List<String> render(int width);

    default void handleInput(String data) {
    }

    default void invalidate() {
    }
}
