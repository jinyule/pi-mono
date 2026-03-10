package dev.pi.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public interface EditOperations {
    String readFile(Path absolutePath) throws IOException;

    void writeFile(Path absolutePath, String content) throws IOException;

    void access(Path absolutePath) throws IOException;

    static EditOperations local() {
        return new EditOperations() {
            @Override
            public String readFile(Path absolutePath) throws IOException {
                return Files.readString(absolutePath, StandardCharsets.UTF_8);
            }

            @Override
            public void writeFile(Path absolutePath, String content) throws IOException {
                Files.writeString(absolutePath, content, StandardCharsets.UTF_8);
            }

            @Override
            public void access(Path absolutePath) throws IOException {
                if (!Files.exists(absolutePath) || !Files.isReadable(absolutePath) || !Files.isWritable(absolutePath)) {
                    throw new IOException("File is not readable and writable: " + absolutePath);
                }
            }
        };
    }
}
