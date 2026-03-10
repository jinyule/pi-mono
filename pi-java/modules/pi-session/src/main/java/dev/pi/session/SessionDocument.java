package dev.pi.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record SessionDocument(
    SessionHeader header,
    List<SessionEntry> entries
) {
    public SessionDocument {
        Objects.requireNonNull(header, "header");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    public List<SessionFileEntry> fileEntries() {
        var fileEntries = new ArrayList<SessionFileEntry>(entries.size() + 1);
        fileEntries.add(header);
        fileEntries.addAll(entries);
        return List.copyOf(fileEntries);
    }
}
