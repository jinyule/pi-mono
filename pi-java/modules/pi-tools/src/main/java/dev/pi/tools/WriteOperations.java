package dev.pi.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public interface WriteOperations {
    void writeFile(Path absolutePath, String content) throws IOException;

    void mkdir(Path dir) throws IOException;

    static WriteOperations local() {
        return new WriteOperations() {
            @Override
            public void writeFile(Path absolutePath, String content) throws IOException {
                Files.writeString(absolutePath, content, StandardCharsets.UTF_8);
            }

            @Override
            public void mkdir(Path dir) throws IOException {
                Files.createDirectories(dir);
            }
        };
    }
}
