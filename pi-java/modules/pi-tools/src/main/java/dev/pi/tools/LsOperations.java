package dev.pi.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public interface LsOperations {
    boolean exists(Path absolutePath);

    boolean isDirectory(Path absolutePath) throws IOException;

    List<String> readdir(Path absolutePath) throws IOException;

    static LsOperations local() {
        return new LsOperations() {
            @Override
            public boolean exists(Path absolutePath) {
                return Files.exists(absolutePath);
            }

            @Override
            public boolean isDirectory(Path absolutePath) {
                return Files.isDirectory(absolutePath);
            }

            @Override
            public List<String> readdir(Path absolutePath) throws IOException {
                try (var stream = Files.list(absolutePath)) {
                    return stream
                        .map(path -> path.getFileName() == null ? path.toString() : path.getFileName().toString())
                        .toList();
                }
            }
        };
    }
}
