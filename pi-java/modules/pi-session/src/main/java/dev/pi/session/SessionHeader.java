package dev.pi.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionHeader(
    String type,
    Integer version,
    String id,
    String timestamp,
    String cwd,
    String parentSession,
    String provider,
    String modelId,
    String thinkingLevel
) implements SessionFileEntry {
    public SessionHeader(
        Integer version,
        String id,
        String timestamp,
        String cwd,
        String parentSession
    ) {
        this("session", version, id, timestamp, cwd, parentSession, null, null, null);
    }

    public SessionHeader {
        if (!"session".equals(type)) {
            throw new IllegalArgumentException("SessionHeader type must be 'session'");
        }
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(timestamp, "timestamp");
        cwd = cwd == null ? "" : cwd;
    }
}
