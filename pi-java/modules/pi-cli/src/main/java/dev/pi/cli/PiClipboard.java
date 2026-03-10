package dev.pi.cli;

import dev.pi.tui.Terminal;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@FunctionalInterface
public interface PiClipboard {
    void copy(String text);

    static PiClipboard osc52(Terminal terminal) {
        Objects.requireNonNull(terminal, "terminal");
        return text -> {
            var encoded = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
            terminal.write("\u001b]52;c;" + encoded + "\u0007");
        };
    }

    static PiClipboard system() {
        return text -> {
            if (GraphicsEnvironment.isHeadless()) {
                throw new IllegalStateException("System clipboard unavailable in headless environment");
            }
            try {
                Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            } catch (IllegalStateException exception) {
                throw new IllegalStateException("System clipboard unavailable", exception);
            }
        };
    }

    static PiClipboard combined(PiClipboard... clipboards) {
        Objects.requireNonNull(clipboards, "clipboards");
        var configured = List.of(clipboards);
        return text -> {
            RuntimeException failure = null;
            var copied = false;
            for (var clipboard : configured) {
                if (clipboard == null) {
                    continue;
                }
                try {
                    clipboard.copy(text);
                    copied = true;
                } catch (RuntimeException exception) {
                    if (failure == null) {
                        failure = exception;
                    }
                }
            }
            if (!copied) {
                if (failure != null) {
                    throw failure;
                }
                throw new IllegalStateException("No clipboard backends configured");
            }
        };
    }
}
