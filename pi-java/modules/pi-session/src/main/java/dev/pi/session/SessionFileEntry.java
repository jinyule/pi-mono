package dev.pi.session;

public sealed interface SessionFileEntry permits SessionHeader, SessionEntry {
    String type();
}
