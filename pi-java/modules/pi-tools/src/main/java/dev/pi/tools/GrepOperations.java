package dev.pi.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public interface GrepOperations {
    boolean isDirectory(Path absolutePath) throws IOException;

    String readFile(Path absolutePath) throws IOException;

    static GrepOperations local() {
        return new GrepOperations() {
            @Override
            public boolean isDirectory(Path absolutePath) throws IOException {
                if (!Files.exists(absolutePath)) {
                    throw new IOException("Path not found: " + absolutePath);
                }
                return Files.isDirectory(absolutePath);
            }

            @Override
            public String readFile(Path absolutePath) throws IOException {
                return Files.readString(absolutePath, StandardCharsets.UTF_8);
            }
        };
    }
}
