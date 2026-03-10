package dev.pi.cli;

import dev.pi.session.SessionEntry;
import dev.pi.session.SessionManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PiCompactor {
    private PiCompactor() {
    }

    public static PiInteractiveSession.CompactionResult compact(SessionManager sessionManager, String customInstructions) throws IOException {
        Objects.requireNonNull(sessionManager, "sessionManager");
        var branch = sessionManager.branch();
        if (branch.isEmpty()) {
            throw new IllegalStateException("Nothing to compact (no messages yet)");
        }

        var latestCompactionIndex = -1;
        SessionEntry.CompactionEntry previousCompaction = null;
        for (var index = 0; index < branch.size(); index += 1) {
            if (branch.get(index) instanceof SessionEntry.CompactionEntry compactionEntry) {
                latestCompactionIndex = index;
                previousCompaction = compactionEntry;
            }
        }

        var keepStartIndex = latestUserMessageIndex(branch, latestCompactionIndex + 1);
        if (keepStartIndex < 0) {
            throw new IllegalStateException("Nothing to compact (session too small)");
        }

        var entriesToSummarize = branch.subList(latestCompactionIndex + 1, keepStartIndex);
        if (entriesToSummarize.isEmpty()) {
            if (previousCompaction != null) {
                throw new IllegalStateException("Already compacted");
            }
            throw new IllegalStateException("Nothing to compact (session too small)");
        }

        var firstKeptEntryId = branch.get(keepStartIndex).id();
        var summary = buildSummary(previousCompaction, entriesToSummarize, customInstructions);
        var tokensBefore = estimateTokens(branch);
        sessionManager.appendCompaction(summary, firstKeptEntryId, tokensBefore, null, false);
        return new PiInteractiveSession.CompactionResult(summary, firstKeptEntryId, tokensBefore);
    }

    private static int latestUserMessageIndex(List<SessionEntry> branch, int startInclusive) {
        for (var index = branch.size() - 1; index >= startInclusive; index -= 1) {
            if (
                branch.get(index) instanceof SessionEntry.MessageEntry messageEntry &&
                "user".equals(messageEntry.message().path("role").asText())
            ) {
                return index;
            }
        }
        return -1;
    }

    private static String buildSummary(
        SessionEntry.CompactionEntry previousCompaction,
        List<SessionEntry> entriesToSummarize,
        String customInstructions
    ) {
        var sections = new ArrayList<String>();
        sections.add("## Goal\nCompact earlier conversation while preserving recent context.");
        if (customInstructions != null && !customInstructions.isBlank()) {
            sections.add("## Custom Focus\n" + customInstructions.trim());
        }
        if (previousCompaction != null && previousCompaction.summary() != null && !previousCompaction.summary().isBlank()) {
            sections.add("## Previous Summary\n" + previousCompaction.summary().trim());
        }
        sections.add("## Summarized Messages\n" + serializeEntries(entriesToSummarize));
        return String.join("\n\n", sections);
    }

    private static String serializeEntries(List<SessionEntry> entries) {
        var lines = new ArrayList<String>();
        for (var entry : entries) {
            var serialized = serializeEntry(entry);
            if (serialized != null && !serialized.isBlank()) {
                lines.add(serialized);
            }
        }
        return lines.isEmpty() ? "(empty)" : String.join("\n", lines);
    }

    private static String serializeEntry(SessionEntry entry) {
        return switch (entry) {
            case SessionEntry.MessageEntry messageEntry -> serializeMessageEntry(messageEntry);
            case SessionEntry.BranchSummaryEntry branchSummaryEntry -> "[Branch summary]: " + branchSummaryEntry.summary();
            case SessionEntry.CustomMessageEntry customMessageEntry ->
                "[Custom message]: " + summarize(extractContentText(customMessageEntry.content()));
            case SessionEntry.ModelChangeEntry modelChangeEntry ->
                "[Model]: %s/%s".formatted(modelChangeEntry.provider(), modelChangeEntry.modelId());
            case SessionEntry.ThinkingLevelChangeEntry thinkingLevelChangeEntry ->
                "[Thinking level]: " + thinkingLevelChangeEntry.thinkingLevel();
            default -> null;
        };
    }

    private static String serializeMessageEntry(SessionEntry.MessageEntry entry) {
        var message = entry.message();
        var role = message.path("role").asText("message");
        return switch (role) {
            case "user" -> "[User]: " + summarize(extractContentText(message.path("content")));
            case "assistant" -> "[Assistant]: " + summarize(extractContentText(message.path("content")));
            case "toolResult" -> "[Tool result]: " + summarize(extractContentText(message.path("content")));
            default -> "[" + role + "]: " + summarize(extractContentText(message.path("content")));
        };
    }

    private static String extractContentText(com.fasterxml.jackson.databind.JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText("");
        }
        if (!content.isArray()) {
            return "";
        }

        var parts = new ArrayList<String>();
        for (var item : content) {
            var type = item.path("type").asText("");
            switch (type) {
                case "text" -> appendIfPresent(parts, item.path("text").asText(""));
                case "thinking" -> appendIfPresent(parts, "Thinking: " + item.path("thinking").asText(""));
                case "toolCall" -> appendIfPresent(
                    parts,
                    "Tool call %s(%s)".formatted(item.path("name").asText("tool"), item.path("arguments").asText(""))
                );
                default -> {
                }
            }
        }
        return String.join(" ", parts);
    }

    private static void appendIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }

    private static String summarize(String value) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }
        return value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static int estimateTokens(List<SessionEntry> entries) {
        var serialized = serializeEntries(entries);
        return Math.max(1, Math.floorDiv(serialized.length(), 4));
    }
}
