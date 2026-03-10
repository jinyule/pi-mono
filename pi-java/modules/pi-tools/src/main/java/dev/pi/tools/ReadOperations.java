package dev.pi.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public interface ReadOperations {
    byte[] readFile(Path absolutePath) throws IOException;

    void access(Path absolutePath) throws IOException;

    String detectImageMimeType(Path absolutePath) throws IOException;

    static ReadOperations local() {
        return new ReadOperations() {
            @Override
            public byte[] readFile(Path absolutePath) throws IOException {
                return Files.readAllBytes(absolutePath);
            }

            @Override
            public void access(Path absolutePath) throws IOException {
                if (!Files.isReadable(absolutePath)) {
                    throw new IOException("Path is not readable: " + absolutePath);
                }
            }

            @Override
            public String detectImageMimeType(Path absolutePath) throws IOException {
                return SupportedImageMimeTypes.detectFromFile(absolutePath);
            }
        };
    }
}
