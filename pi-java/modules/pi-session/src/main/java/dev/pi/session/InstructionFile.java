package dev.pi.session;

import java.nio.file.Path;
import java.util.Objects;

public record InstructionFile(
    Path path,
    String content
) {
    public InstructionFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(content, "content");
    }
}
