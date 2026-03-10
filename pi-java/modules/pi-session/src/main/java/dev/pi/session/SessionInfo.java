package dev.pi.session;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

public record SessionInfo(
    Path path,
    String id,
    String cwd,
    String name,
    String parentSessionPath,
    Instant created,
    Instant modified,
    int messageCount,
    String firstMessage,
    String allMessagesText
) {
    public SessionInfo {
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        id = Objects.requireNonNull(id, "id");
        cwd = cwd == null ? "" : cwd;
        created = Objects.requireNonNull(created, "created");
        modified = Objects.requireNonNull(modified, "modified");
        firstMessage = firstMessage == null || firstMessage.isBlank() ? "(no messages)" : firstMessage;
        allMessagesText = allMessagesText == null ? "" : allMessagesText;
    }
}
