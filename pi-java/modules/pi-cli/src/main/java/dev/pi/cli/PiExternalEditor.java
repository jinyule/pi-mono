package dev.pi.cli;

import dev.pi.tui.Tui;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
interface PiExternalEditor {
    String edit(String currentText);

    static PiExternalEditor system(Tui tui) {
        return currentText -> {
            var editorCommand = resolveEditorCommand();
            if (editorCommand == null) {
                throw new UnsupportedOperationException("No editor configured. Set $VISUAL or $EDITOR environment variable.");
            }

            Path tempFile = null;
            try {
                tempFile = Files.createTempFile("pi-editor-", ".pi.md");
                Files.writeString(tempFile, currentText == null ? "" : currentText, StandardCharsets.UTF_8);
                tui.stop();
                try {
                    var process = new ProcessBuilder(command(editorCommand, tempFile.toString()))
                        .inheritIO()
                        .start();
                    var exitCode = process.waitFor();
                    if (exitCode != 0) {
                        return null;
                    }
                    return Files.readString(tempFile, StandardCharsets.UTF_8);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Failed to open external editor", exception);
                } finally {
                    tui.start();
                    tui.requestRender();
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to open external editor", exception);
            } finally {
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException ignored) {
                    }
                }
            }
        };
    }

    private static String resolveEditorCommand() {
        var visual = System.getenv("VISUAL");
        if (visual != null && !visual.isBlank()) {
            return visual.trim();
        }
        var editor = System.getenv("EDITOR");
        if (editor != null && !editor.isBlank()) {
            return editor.trim();
        }
        return null;
    }

    private static List<String> command(String editorCommand, String filePath) {
        var command = new ArrayList<String>();
        for (var token : editorCommand.trim().split("\\s+")) {
            if (!token.isBlank()) {
                command.add(token);
            }
        }
        command.add(filePath);
        return List.copyOf(command);
    }
}
