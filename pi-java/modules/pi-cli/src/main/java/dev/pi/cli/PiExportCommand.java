package dev.pi.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pi.agent.runtime.AgentMessages;
import dev.pi.ai.model.Message;
import dev.pi.session.SessionEntry;
import dev.pi.session.SessionManager;
import dev.pi.session.SessionTreeNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PiExportCommand {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Path export(Path inputPath, Path outputPath) throws IOException {
        Objects.requireNonNull(inputPath, "inputPath");
        var normalizedInputPath = inputPath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedInputPath)) {
            throw new IllegalArgumentException("Session file not found: " + normalizedInputPath);
        }

        var session = SessionManager.open(normalizedInputPath);
        var resolvedOutput = resolveOutputPath(normalizedInputPath, outputPath);
        var parent = resolvedOutput.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(resolvedOutput, renderHtml(session), StandardCharsets.UTF_8);
        return resolvedOutput;
    }

    private static Path resolveOutputPath(Path inputPath, Path outputPath) {
        if (outputPath != null) {
            return outputPath.toAbsolutePath().normalize();
        }
        var fileName = inputPath.getFileName().toString();
        if (fileName.endsWith(".jsonl")) {
            fileName = fileName.substring(0, fileName.length() - ".jsonl".length());
        }
        return Path.of("pi-java-session-" + fileName + ".html").toAbsolutePath().normalize();
    }

    private static String renderHtml(SessionManager session) {
        var activePathIds = session.branch().stream()
            .map(SessionEntry::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var sessionName = latestSessionName(session.entries());
        var metadata = renderMetadata(session, sessionName, activePathIds.size());
        var tree = renderTree(session.tree(), activePathIds, session.leafId());
        var entries = renderEntries(session.entries(), session, activePathIds);

        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>pi-java session export</title>
              <style>
                :root { color-scheme: dark; }
                body { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; margin: 0; background: #111827; color: #f3f4f6; }
                .layout { max-width: 1280px; margin: 0 auto; padding: 32px 24px 48px; display: grid; grid-template-columns: 320px minmax(0, 1fr); gap: 24px; }
                .sidebar { position: sticky; top: 24px; align-self: start; }
                .panel, .entry { background: #1f2937; border: 1px solid #374151; border-radius: 12px; }
                .panel { padding: 16px; margin-bottom: 16px; }
                .panel h2, .content h2 { margin: 0 0 12px; font-size: 16px; }
                .content h1 { margin: 0 0 8px; font-size: 28px; }
                .lead { color: #9ca3af; font-size: 14px; margin-bottom: 24px; }
                .meta-list { display: grid; gap: 10px; }
                .meta-row { display: grid; gap: 4px; }
                .meta-key { color: #9ca3af; font-size: 12px; text-transform: uppercase; letter-spacing: 0.08em; }
                .meta-value { font-size: 14px; word-break: break-word; }
                .tree, .tree ul { list-style: none; margin: 0; padding-left: 16px; }
                .tree { padding-left: 0; }
                .tree-node { margin: 8px 0; padding-left: 12px; border-left: 2px solid #374151; }
                .tree-node.active { border-left-color: #60a5fa; }
                .tree-node.leaf { border-left-color: #34d399; }
                .tree-entry { display: grid; gap: 4px; }
                .tree-entry-title { font-size: 13px; }
                .tree-entry-meta { color: #9ca3af; font-size: 12px; }
                .content { min-width: 0; }
                .entries { display: grid; gap: 12px; }
                .entry { padding: 16px; }
                .entry.active { border-color: #60a5fa; box-shadow: inset 0 0 0 1px rgba(96, 165, 250, 0.25); }
                .entry-header { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 10px; }
                .entry-title { font-size: 16px; font-weight: 600; }
                .badge { display: inline-flex; align-items: center; border-radius: 999px; padding: 2px 8px; font-size: 12px; border: 1px solid #4b5563; color: #d1d5db; }
                .badge.label { border-color: #8b5cf6; color: #ddd6fe; }
                .badge.branch { border-color: #2563eb; color: #bfdbfe; }
                .badge.leaf { border-color: #059669; color: #a7f3d0; }
                .entry-meta { color: #9ca3af; font-size: 12px; margin-bottom: 10px; }
                .entry-body { display: grid; gap: 10px; }
                .details-title { color: #9ca3af; font-size: 12px; text-transform: uppercase; letter-spacing: 0.08em; }
                pre { margin: 0; white-space: pre-wrap; word-break: break-word; background: #111827; border: 1px solid #374151; border-radius: 8px; padding: 12px; }
                @media (max-width: 960px) {
                  .layout { grid-template-columns: 1fr; }
                  .sidebar { position: static; }
                }
              </style>
            </head>
            <body>
              <div class="layout">
                <aside class="sidebar">
                  %s
                  %s
                </aside>
                <main class="content">
                  <header>
                    <h1>pi-java session export</h1>
                    <div class="lead">Standalone HTML export of the full session document and current leaf context.</div>
                  </header>
                  <h2>Session entries</h2>
                  <div class="entries">%s</div>
                </main>
              </div>
            </body>
            </html>
            """.formatted(
            metadata,
            tree,
            entries
        );
    }

    private static String renderMetadata(SessionManager session, String sessionName, int activeBranchLength) {
        var rows = List.of(
            metaRow("Session", session.sessionId()),
            metaRow("Name", blankFallback(sessionName)),
            metaRow("Cwd", blankFallback(session.header().cwd())),
            metaRow("Created", session.header().timestamp()),
            metaRow("Current leaf", blankFallback(session.leafId())),
            metaRow("Entry count", Integer.toString(session.entries().size())),
            metaRow("Active branch length", Integer.toString(activeBranchLength)),
            metaRow("Parent session", blankFallback(session.header().parentSession())),
            metaRow("Header provider", blankFallback(session.header().provider())),
            metaRow("Header model", blankFallback(session.header().modelId())),
            metaRow("Header thinking", blankFallback(session.header().thinkingLevel()))
        );
        return """
            <section class="panel">
              <h2>Session metadata</h2>
              <div class="meta-list">%s</div>
            </section>
            """.formatted(String.join("", rows));
    }

    private static String renderTree(List<SessionTreeNode> nodes, Set<String> activePathIds, String leafId) {
        var content = nodes.isEmpty()
            ? "<li class=\"tree-node\"><div class=\"tree-entry\"><div class=\"tree-entry-title\">(empty session)</div></div></li>"
            : renderTreeNodes(nodes, activePathIds, leafId);
        return """
            <section class="panel">
              <h2>Session tree</h2>
              <ul class="tree">%s</ul>
            </section>
            """.formatted(content);
    }

    private static String renderTreeNodes(List<SessionTreeNode> nodes, Set<String> activePathIds, String leafId) {
        var html = new StringBuilder();
        for (var node : nodes) {
            var entry = node.entry();
            var isActive = activePathIds.contains(entry.id());
            var isLeaf = Objects.equals(entry.id(), leafId);
            html.append("<li class=\"tree-node");
            if (isActive) {
                html.append(" active");
            }
            if (isLeaf) {
                html.append(" leaf");
            }
            html.append("\">");
            html.append("<div class=\"tree-entry\">");
            html.append("<div class=\"tree-entry-title\">").append(escapeHtml(treeEntryTitle(entry, node.label()))).append("</div>");
            html.append("<div class=\"tree-entry-meta\">").append(escapeHtml(entry.id())).append("</div>");
            html.append("</div>");
            if (!node.children().isEmpty()) {
                html.append("<ul>").append(renderTreeNodes(node.children(), activePathIds, leafId)).append("</ul>");
            }
            html.append("</li>");
        }
        return html.toString();
    }

    private static String renderEntries(List<SessionEntry> entries, SessionManager session, Set<String> activePathIds) {
        if (entries.isEmpty()) {
            return "<section class=\"entry\"><div class=\"entry-body\"><pre>(empty session)</pre></div></section>";
        }
        return entries.stream()
            .map(entry -> renderEntry(entry, session, activePathIds))
            .reduce((left, right) -> left + System.lineSeparator() + right)
            .orElse("");
    }

    private static String renderEntry(SessionEntry entry, SessionManager session, Set<String> activePathIds) {
        var badges = new StringBuilder();
        var label = session.label(entry.id());
        if (label != null && !label.isBlank()) {
            badges.append("<span class=\"badge label\">").append(escapeHtml(label)).append("</span>");
        }
        if (activePathIds.contains(entry.id())) {
            badges.append("<span class=\"badge branch\">active branch</span>");
        }
        if (Objects.equals(entry.id(), session.leafId())) {
            badges.append("<span class=\"badge leaf\">current leaf</span>");
        }
        var classes = activePathIds.contains(entry.id()) ? "entry active" : "entry";
        return """
            <section class="%s">
              <div class="entry-header">
                <div class="entry-title">%s</div>
                %s
              </div>
              <div class="entry-meta">%s</div>
              <div class="entry-body">%s</div>
            </section>
            """.formatted(
            classes,
            escapeHtml(entryTitle(entry)),
            badges,
            escapeHtml(entryMeta(entry)),
            renderEntryBody(entry)
        );
    }

    private static String entryTitle(SessionEntry entry) {
        return switch (entry) {
            case SessionEntry.MessageEntry messageEntry -> messageTitle(messageEntry);
            case SessionEntry.ThinkingLevelChangeEntry ignored -> "Thinking level";
            case SessionEntry.ModelChangeEntry ignored -> "Model change";
            case SessionEntry.CompactionEntry ignored -> "Compaction";
            case SessionEntry.BranchSummaryEntry ignored -> "Branch summary";
            case SessionEntry.CustomEntry ignored -> "Custom entry";
            case SessionEntry.CustomMessageEntry ignored -> "Custom message";
            case SessionEntry.LabelEntry ignored -> "Label";
            case SessionEntry.SessionInfoEntry ignored -> "Session info";
        };
    }

    private static String messageTitle(SessionEntry.MessageEntry entry) {
        var role = entry.message().path("role").asText("");
        return switch (role) {
            case "user" -> "User message";
            case "assistant" -> "Assistant message";
            case "toolResult" -> "Tool result";
            default -> "Message";
        };
    }

    private static String entryMeta(SessionEntry entry) {
        var parentId = entry.parentId() == null ? "root" : entry.parentId();
        return "id=%s · parent=%s · timestamp=%s".formatted(
            entry.id(),
            parentId,
            entry.timestamp()
        );
    }

    private static String renderEntryBody(SessionEntry entry) {
        return switch (entry) {
            case SessionEntry.MessageEntry messageEntry -> renderMessageBody(messageEntry);
            case SessionEntry.ThinkingLevelChangeEntry thinkingLevelChangeEntry ->
                renderValueBlock("Level", thinkingLevelChangeEntry.thinkingLevel());
            case SessionEntry.ModelChangeEntry modelChangeEntry ->
                renderValueBlock("Provider", modelChangeEntry.provider()) + renderValueBlock("Model", modelChangeEntry.modelId());
            case SessionEntry.CompactionEntry compactionEntry ->
                renderValueBlock("Summary", compactionEntry.summary())
                    + renderValueBlock("First kept entry", blankFallback(compactionEntry.firstKeptEntryId()))
                    + renderValueBlock("Tokens before", String.valueOf(compactionEntry.tokensBefore()))
                    + renderJsonBlock("Details", compactionEntry.details())
                    + renderValueBlock("From hook", String.valueOf(compactionEntry.fromHook()));
            case SessionEntry.BranchSummaryEntry branchSummaryEntry ->
                renderValueBlock("From entry", branchSummaryEntry.fromId())
                    + renderValueBlock("Summary", branchSummaryEntry.summary())
                    + renderJsonBlock("Details", branchSummaryEntry.details())
                    + renderValueBlock("From hook", String.valueOf(branchSummaryEntry.fromHook()));
            case SessionEntry.CustomEntry customEntry ->
                renderValueBlock("Type", customEntry.customType()) + renderJsonBlock("Data", customEntry.data());
            case SessionEntry.CustomMessageEntry customMessageEntry ->
                renderValueBlock("Type", customMessageEntry.customType())
                    + renderJsonBlock("Content", customMessageEntry.content())
                    + renderJsonBlock("Details", customMessageEntry.details())
                    + renderValueBlock("Display", String.valueOf(customMessageEntry.display()));
            case SessionEntry.LabelEntry labelEntry ->
                renderValueBlock("Target", labelEntry.targetId()) + renderValueBlock("Label", blankFallback(labelEntry.label()));
            case SessionEntry.SessionInfoEntry sessionInfoEntry -> renderValueBlock("Name", blankFallback(sessionInfoEntry.name()));
        };
    }

    private static String renderMessageBody(SessionEntry.MessageEntry entry) {
        try {
            var message = OBJECT_MAPPER.treeToValue(entry.message(), Message.class);
            var rendered = PiMessageRenderer.renderMessage(AgentMessages.fromLlmMessage(message));
            return renderPre(rendered);
        } catch (IOException exception) {
            return renderJsonBlock("Raw message", entry.message());
        }
    }

    private static String renderValueBlock(String title, String value) {
        return """
            <div>
              <div class="details-title">%s</div>
              <pre>%s</pre>
            </div>
            """.formatted(escapeHtml(title), escapeHtml(blankFallback(value)));
    }

    private static String renderJsonBlock(String title, JsonNode json) {
        if (json == null || json.isNull() || json.isMissingNode()) {
            return "";
        }
        return """
            <div>
              <div class="details-title">%s</div>
              <pre>%s</pre>
            </div>
            """.formatted(escapeHtml(title), escapeHtml(prettyJson(json)));
    }

    private static String renderPre(String text) {
        return "<pre>%s</pre>".formatted(escapeHtml(blankFallback(text)));
    }

    private static String treeEntryTitle(SessionEntry entry, String label) {
        var prefix = label == null || label.isBlank() ? "" : label + " · ";
        return prefix + switch (entry) {
            case SessionEntry.MessageEntry messageEntry -> summarizeMessage(messageEntry);
            case SessionEntry.ThinkingLevelChangeEntry thinkingLevelChangeEntry -> "Thinking: " + thinkingLevelChangeEntry.thinkingLevel();
            case SessionEntry.ModelChangeEntry modelChangeEntry -> "Model: " + modelChangeEntry.provider() + "/" + modelChangeEntry.modelId();
            case SessionEntry.CompactionEntry compactionEntry -> "Compaction: " + summarizeText(compactionEntry.summary());
            case SessionEntry.BranchSummaryEntry branchSummaryEntry -> "Branch: " + summarizeText(branchSummaryEntry.summary());
            case SessionEntry.CustomEntry customEntry -> "Custom: " + customEntry.customType();
            case SessionEntry.CustomMessageEntry customMessageEntry -> "Custom message: " + customMessageEntry.customType();
            case SessionEntry.LabelEntry labelEntry -> "Label: " + blankFallback(labelEntry.label());
            case SessionEntry.SessionInfoEntry sessionInfoEntry -> "Session info: " + blankFallback(sessionInfoEntry.name());
        };
    }

    private static String summarizeMessage(SessionEntry.MessageEntry entry) {
        try {
            var message = OBJECT_MAPPER.treeToValue(entry.message(), Message.class);
            return summarizeText(PiMessageRenderer.renderMessage(AgentMessages.fromLlmMessage(message)));
        } catch (IOException exception) {
            return "Message";
        }
    }

    private static String summarizeText(String text) {
        var normalized = blankFallback(text).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 77) + "...";
    }

    private static String latestSessionName(List<SessionEntry> entries) {
        String sessionName = null;
        for (var entry : entries) {
            if (entry instanceof SessionEntry.SessionInfoEntry sessionInfoEntry
                && sessionInfoEntry.name() != null
                && !sessionInfoEntry.name().isBlank()) {
                sessionName = sessionInfoEntry.name().trim();
            }
        }
        return sessionName;
    }

    private static String metaRow(String key, String value) {
        return """
            <div class="meta-row">
              <div class="meta-key">%s</div>
              <div class="meta-value">%s</div>
            </div>
            """.formatted(escapeHtml(key), escapeHtml(blankFallback(value)));
    }

    private static String prettyJson(JsonNode json) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (IOException exception) {
            return json.toString();
        }
    }

    private static String blankFallback(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
