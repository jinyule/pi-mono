package dev.pi.session;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

public final class SessionMigrations {
    public static final int CURRENT_SESSION_VERSION = 3;

    private SessionMigrations() {
    }

    public static SessionDocument migrateToCurrentVersion(SessionDocument document) {
        Objects.requireNonNull(document, "document");

        var version = document.header().version() == null ? 1 : document.header().version();
        var migratedDocument = document;

        if (version < 2) {
            migratedDocument = migrateV1ToV2(migratedDocument);
        }
        if (version < 3) {
            migratedDocument = migrateV2ToV3(migratedDocument);
        }
        if (!Objects.equals(migratedDocument.header().version(), CURRENT_SESSION_VERSION)) {
            migratedDocument = new SessionDocument(upgradeHeader(migratedDocument.header(), CURRENT_SESSION_VERSION), migratedDocument.entries());
        }
        return migratedDocument;
    }

    private static SessionDocument migrateV1ToV2(SessionDocument document) {
        var usedIds = new HashSet<String>();
        for (var entry : document.entries()) {
            if (entry.id() != null && !entry.id().isBlank()) {
                usedIds.add(entry.id());
            }
        }

        var counter = 1;
        var previousId = (String) null;
        var assignedIds = new ArrayList<String>(document.entries().size());
        var migratedEntries = new ArrayList<SessionEntry>(document.entries().size());

        for (var index = 0; index < document.entries().size(); index++) {
            var entry = document.entries().get(index);
            var id = entry.id();
            if (id == null || id.isBlank()) {
                id = nextId(usedIds, counter);
                counter = Integer.parseInt(id, 16) + 1;
            }
            assignedIds.add(id);

            String firstKeptEntryId = null;
            if (entry instanceof SessionEntry.CompactionEntry compactionEntry) {
                firstKeptEntryId = compactionEntry.firstKeptEntryId();
                if (firstKeptEntryId == null && compactionEntry.firstKeptEntryIndex() != null) {
                    var firstKeptEntryIndex = compactionEntry.firstKeptEntryIndex();
                    if (firstKeptEntryIndex > 0 && firstKeptEntryIndex <= assignedIds.size()) {
                        firstKeptEntryId = assignedIds.get(firstKeptEntryIndex - 1);
                    }
                }
            }

            migratedEntries.add(rebuildEntry(entry, id, previousId, firstKeptEntryId, null));
            previousId = id;
        }

        return new SessionDocument(upgradeHeader(document.header(), 2), migratedEntries);
    }

    private static SessionDocument migrateV2ToV3(SessionDocument document) {
        var migratedEntries = document.entries().stream()
            .map(SessionMigrations::rewriteHookMessageRole)
            .toList();
        return new SessionDocument(upgradeHeader(document.header(), 3), migratedEntries);
    }

    private static SessionHeader upgradeHeader(SessionHeader header, int version) {
        return new SessionHeader(
            "session",
            version,
            header.id(),
            header.timestamp(),
            header.cwd(),
            header.parentSession(),
            header.provider(),
            header.modelId(),
            header.thinkingLevel()
        );
    }

    private static SessionEntry rewriteHookMessageRole(SessionEntry entry) {
        if (!(entry instanceof SessionEntry.MessageEntry messageEntry)) {
            return entry;
        }
        if (!"hookMessage".equals(messageEntry.message().path("role").asText(null))) {
            return entry;
        }
        var migratedMessage = messageEntry.message().deepCopy();
        if (migratedMessage instanceof ObjectNode objectNode) {
            objectNode.put("role", "custom");
        }
        return new SessionEntry.MessageEntry(
            messageEntry.id(),
            messageEntry.parentId(),
            messageEntry.timestamp(),
            migratedMessage
        );
    }

    private static SessionEntry rebuildEntry(
        SessionEntry entry,
        String id,
        String parentId,
        String firstKeptEntryId,
        Integer firstKeptEntryIndex
    ) {
        return switch (entry) {
            case SessionEntry.MessageEntry messageEntry -> new SessionEntry.MessageEntry(
                id,
                parentId,
                messageEntry.timestamp(),
                messageEntry.message()
            );
            case SessionEntry.ThinkingLevelChangeEntry thinkingLevelChangeEntry -> new SessionEntry.ThinkingLevelChangeEntry(
                id,
                parentId,
                thinkingLevelChangeEntry.timestamp(),
                thinkingLevelChangeEntry.thinkingLevel()
            );
            case SessionEntry.ModelChangeEntry modelChangeEntry -> new SessionEntry.ModelChangeEntry(
                id,
                parentId,
                modelChangeEntry.timestamp(),
                modelChangeEntry.provider(),
                modelChangeEntry.modelId()
            );
            case SessionEntry.CompactionEntry compactionEntry -> new SessionEntry.CompactionEntry(
                "compaction",
                id,
                parentId,
                compactionEntry.timestamp(),
                compactionEntry.summary(),
                firstKeptEntryId != null ? firstKeptEntryId : compactionEntry.firstKeptEntryId(),
                firstKeptEntryIndex,
                compactionEntry.tokensBefore(),
                compactionEntry.details(),
                compactionEntry.fromHook()
            );
            case SessionEntry.BranchSummaryEntry branchSummaryEntry -> new SessionEntry.BranchSummaryEntry(
                id,
                parentId,
                branchSummaryEntry.timestamp(),
                branchSummaryEntry.fromId(),
                branchSummaryEntry.summary(),
                branchSummaryEntry.details(),
                branchSummaryEntry.fromHook()
            );
            case SessionEntry.CustomEntry customEntry -> new SessionEntry.CustomEntry(
                id,
                parentId,
                customEntry.timestamp(),
                customEntry.customType(),
                customEntry.data()
            );
            case SessionEntry.CustomMessageEntry customMessageEntry -> new SessionEntry.CustomMessageEntry(
                id,
                parentId,
                customMessageEntry.timestamp(),
                customMessageEntry.customType(),
                customMessageEntry.content(),
                customMessageEntry.details(),
                customMessageEntry.display()
            );
            case SessionEntry.LabelEntry labelEntry -> new SessionEntry.LabelEntry(
                id,
                parentId,
                labelEntry.timestamp(),
                labelEntry.targetId(),
                labelEntry.label()
            );
            case SessionEntry.SessionInfoEntry sessionInfoEntry -> new SessionEntry.SessionInfoEntry(
                id,
                parentId,
                sessionInfoEntry.timestamp(),
                sessionInfoEntry.name()
            );
        };
    }

    private static String nextId(HashSet<String> usedIds, int counter) {
        var next = counter;
        while (true) {
            var id = "%08x".formatted(next++);
            if (usedIds.add(id)) {
                return id;
            }
        }
    }
}
