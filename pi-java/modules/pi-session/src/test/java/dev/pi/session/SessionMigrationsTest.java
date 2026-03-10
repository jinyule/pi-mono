package dev.pi.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SessionMigrationsTest {
    private final SessionJsonlCodec codec = new SessionJsonlCodec();

    @Test
    void migratesV1EntriesToCurrentVersion() {
        var jsonl = """
            {"type":"session","id":"sess-1","timestamp":"2025-01-01T00:00:00Z","cwd":"/tmp"}
            {"type":"message","timestamp":"2025-01-01T00:00:01Z","message":{"role":"user","content":[{"type":"text","text":"hi"}],"timestamp":1}}
            {"type":"message","timestamp":"2025-01-01T00:00:02Z","message":{"role":"assistant","content":[{"type":"text","text":"hello"}],"api":"test","provider":"test","model":"test","usage":{"input":1,"output":1,"cacheRead":0,"cacheWrite":0},"stopReason":"stop","timestamp":2}}
            {"type":"compaction","timestamp":"2025-01-01T00:00:03Z","summary":"compact","firstKeptEntryIndex":1,"tokensBefore":1000}
            """;

        var migrated = SessionMigrations.migrateToCurrentVersion(codec.parseDocument(jsonl).orElseThrow());

        assertThat(migrated.header().version()).isEqualTo(3);
        assertThat(migrated.entries()).hasSize(3);
        assertThat(migrated.entries().get(0).id()).isNotBlank();
        assertThat(migrated.entries().get(0).parentId()).isNull();
        assertThat(migrated.entries().get(1).parentId()).isEqualTo(migrated.entries().get(0).id());
        assertThat(migrated.entries().get(2).parentId()).isEqualTo(migrated.entries().get(1).id());
        assertThat(((SessionEntry.CompactionEntry) migrated.entries().get(2)).firstKeptEntryId())
            .isEqualTo(migrated.entries().get(0).id());
        assertThat(((SessionEntry.CompactionEntry) migrated.entries().get(2)).firstKeptEntryIndex()).isNull();
    }

    @Test
    void migratesHookMessageRoleToCustomInV2Sessions() {
        var document = new SessionDocument(
            new SessionHeader("session", 2, "sess-1", "2025-01-01T00:00:00Z", "/tmp", null, null, null, null),
            List.of(
                new SessionEntry.MessageEntry(
                    "abc12345",
                    null,
                    "2025-01-01T00:00:01Z",
                    codec.valueToTree(Map.of(
                        "role", "hookMessage",
                        "content", "hello",
                        "timestamp", 1
                    ))
                )
            )
        );

        var migrated = SessionMigrations.migrateToCurrentVersion(document);

        assertThat(migrated.header().version()).isEqualTo(3);
        assertThat(((SessionEntry.MessageEntry) migrated.entries().getFirst()).message().path("role").asText()).isEqualTo("custom");
        assertThat(migrated.entries().getFirst().id()).isEqualTo("abc12345");
    }

    @Test
    void keepsCurrentSessionsStable() {
        var document = new SessionDocument(
            new SessionHeader("session", 3, "sess-1", "2025-01-01T00:00:00Z", "/tmp", null, null, null, null),
            List.of(
                new SessionEntry.ThinkingLevelChangeEntry("abc12345", null, "2025-01-01T00:00:01Z", "high")
            )
        );

        var migrated = SessionMigrations.migrateToCurrentVersion(document);

        assertThat(migrated).isEqualTo(document);
    }
}
