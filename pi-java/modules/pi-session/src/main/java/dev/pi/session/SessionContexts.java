package dev.pi.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pi.ai.model.AssistantContent;
import dev.pi.ai.model.ImageContent;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.ai.model.UserContent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SessionContexts {
    public static final String COMPACTION_SUMMARY_PREFIX = """
        The conversation history before this point was compacted into the following summary:

        <summary>
        """;
    public static final String COMPACTION_SUMMARY_SUFFIX = """

        </summary>""";
    public static final String BRANCH_SUMMARY_PREFIX = """
        The following is a summary of a branch that this conversation came back from:

        <summary>
        """;
    public static final String BRANCH_SUMMARY_SUFFIX = "</summary>";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SessionContexts() {
    }

    public static SessionContext buildSessionContext(SessionDocument document) {
        Objects.requireNonNull(document, "document");
        return buildSessionContext(document.header(), document.entries(), null, false);
    }

    public static SessionContext buildSessionContext(SessionDocument document, String leafId) {
        Objects.requireNonNull(document, "document");
        return buildSessionContext(document.header(), document.entries(), leafId, true);
    }

    public static SessionContext buildSessionContext(List<SessionEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        return buildSessionContext(null, entries, null, false);
    }

    public static SessionContext buildSessionContext(List<SessionEntry> entries, String leafId) {
        Objects.requireNonNull(entries, "entries");
        return buildSessionContext(null, entries, leafId, true);
    }

    private static SessionContext buildSessionContext(
        SessionHeader header,
        List<SessionEntry> entries,
        String leafId,
        boolean explicitLeafSelection
    ) {
        if (entries.isEmpty()) {
            return emptyContext();
        }
        if (explicitLeafSelection && leafId == null) {
            return emptyContext();
        }

        var byId = new LinkedHashMap<String, SessionEntry>();
        for (var entry : entries) {
            if (entry.id() != null) {
                byId.put(entry.id(), entry);
            }
        }

        SessionEntry leaf = null;
        if (leafId != null) {
            leaf = byId.get(leafId);
        }
        if (leaf == null) {
            leaf = entries.getLast();
        }
        if (leaf == null) {
            return emptyContext();
        }

        var path = new ArrayList<SessionEntry>();
        SessionEntry current = leaf;
        while (current != null) {
            path.addFirst(current);
            current = current.parentId() == null ? null : byId.get(current.parentId());
        }

        var thinkingLevel = header != null && header.thinkingLevel() != null ? header.thinkingLevel() : "off";
        SessionContext.ModelSelection model = header != null && header.provider() != null && header.modelId() != null
            ? new SessionContext.ModelSelection(header.provider(), header.modelId())
            : null;
        SessionEntry.CompactionEntry compaction = null;

        for (var entry : path) {
            switch (entry) {
                case SessionEntry.ThinkingLevelChangeEntry thinking -> thinkingLevel = thinking.thinkingLevel();
                case SessionEntry.ModelChangeEntry modelChange ->
                    model = new SessionContext.ModelSelection(modelChange.provider(), modelChange.modelId());
                case SessionEntry.MessageEntry messageEntry -> {
                    var message = toMessageOptional(messageEntry.message()).orElse(null);
                    if (message instanceof Message.AssistantMessage assistantMessage) {
                        model = new SessionContext.ModelSelection(assistantMessage.provider(), assistantMessage.model());
                    }
                }
                case SessionEntry.CompactionEntry compactionEntry -> compaction = compactionEntry;
                default -> {
                }
            }
        }

        var messages = new ArrayList<Message>();
        if (compaction != null) {
            messages.add(summaryMessage(COMPACTION_SUMMARY_PREFIX + compaction.summary() + COMPACTION_SUMMARY_SUFFIX, compaction.timestamp()));
            var compactionIndex = path.indexOf(compaction);
            var foundFirstKept = false;
            for (var index = 0; index < compactionIndex; index++) {
                var entry = path.get(index);
                if (Objects.equals(entry.id(), compaction.firstKeptEntryId())) {
                    foundFirstKept = true;
                }
                if (foundFirstKept) {
                    appendMessage(entry, messages);
                }
            }
            for (var index = compactionIndex + 1; index < path.size(); index++) {
                appendMessage(path.get(index), messages);
            }
        } else {
            for (var entry : path) {
                appendMessage(entry, messages);
            }
        }

        return new SessionContext(messages, thinkingLevel, model);
    }

    private static SessionContext emptyContext() {
        return new SessionContext(List.of(), "off", null);
    }

    private static void appendMessage(SessionEntry entry, List<Message> messages) {
        switch (entry) {
            case SessionEntry.MessageEntry messageEntry -> toMessageOptional(messageEntry.message()).ifPresent(messages::add);
            case SessionEntry.CustomMessageEntry customMessageEntry ->
                messages.add(
                    new Message.UserMessage(
                        toUserContent(customMessageEntry.content()),
                        parseIsoTimestamp(customMessageEntry.timestamp())
                    )
                );
            case SessionEntry.BranchSummaryEntry branchSummaryEntry ->
                messages.add(
                    summaryMessage(
                        BRANCH_SUMMARY_PREFIX + branchSummaryEntry.summary() + BRANCH_SUMMARY_SUFFIX,
                        branchSummaryEntry.timestamp()
                    )
                );
            default -> {
            }
        }
    }

    private static Optional<Message> toMessageOptional(JsonNode node) {
        try {
            return Optional.ofNullable(toMessage(node));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Message toMessage(JsonNode node) {
        Objects.requireNonNull(node, "node");
        var role = node.path("role").asText(null);
        if (role == null) {
            throw new IllegalArgumentException("Session message payload is missing role");
        }
        return switch (role) {
            case "user" -> new Message.UserMessage(
                toUserContent(node.get("content")),
                toMessageTimestamp(node)
            );
            case "assistant" -> new Message.AssistantMessage(
                toAssistantContent(node.get("content")),
                node.path("api").asText(),
                node.path("provider").asText(),
                node.path("model").asText(),
                parseUsage(node.path("usage")),
                StopReason.fromValue(node.path("stopReason").asText()),
                node.path("errorMessage").isMissingNode() || node.path("errorMessage").isNull()
                    ? null
                    : node.path("errorMessage").asText(),
                toMessageTimestamp(node)
            );
            case "toolResult" -> new Message.ToolResultMessage(
                node.path("toolCallId").asText(),
                node.path("toolName").asText(),
                toUserContent(node.get("content")),
                node.path("details").isMissingNode() ? null : node.path("details"),
                node.path("isError").asBoolean(false),
                toMessageTimestamp(node)
            );
            case "custom", "hookMessage" -> new Message.UserMessage(
                toUserContent(node.get("content")),
                toMessageTimestamp(node)
            );
            case "branchSummary" -> summaryMessage(
                BRANCH_SUMMARY_PREFIX + node.path("summary").asText("") + BRANCH_SUMMARY_SUFFIX,
                toMessageTimestamp(node)
            );
            case "compactionSummary" -> summaryMessage(
                COMPACTION_SUMMARY_PREFIX + node.path("summary").asText("") + COMPACTION_SUMMARY_SUFFIX,
                toMessageTimestamp(node)
            );
            case "bashExecution" -> new Message.UserMessage(
                List.of(new TextContent(formatBashExecution(node), null)),
                toMessageTimestamp(node)
            );
            default -> throw new IllegalArgumentException("Unsupported session message role: " + role);
        };
    }

    private static Usage parseUsage(JsonNode node) {
        return new Usage(
            node.path("input").asInt(0),
            node.path("output").asInt(0),
            node.path("cacheRead").asInt(0),
            node.path("cacheWrite").asInt(0),
            node.path("totalTokens").asInt(0),
            OBJECT_MAPPER.convertValue(node.path("cost"), Usage.Cost.class)
        );
    }

    private static List<UserContent> toUserContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isNull()) {
            return List.of();
        }
        if (contentNode.isTextual()) {
            return List.of(new TextContent(contentNode.asText(), null));
        }
        if (!contentNode.isArray()) {
            return List.of(new TextContent(contentNode.toString(), null));
        }

        var content = new ArrayList<UserContent>();
        for (var block : contentNode) {
            var type = block.path("type").asText();
            switch (type) {
                case "text" -> content.add(
                    new TextContent(
                        block.path("text").asText(""),
                        block.path("textSignature").isMissingNode() || block.path("textSignature").isNull()
                            ? null
                            : block.path("textSignature").asText()
                    )
                );
                case "image" -> content.add(
                    new ImageContent(
                        block.path("data").asText(),
                        block.path("mimeType").asText()
                    )
                );
                default -> {
                }
            }
        }
        return List.copyOf(content);
    }

    private static List<AssistantContent> toAssistantContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isNull()) {
            return List.of();
        }
        if (contentNode.isTextual()) {
            return List.of(new TextContent(contentNode.asText(), null));
        }
        return OBJECT_MAPPER.convertValue(
            contentNode,
            OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, AssistantContent.class)
        );
    }

    private static Message.UserMessage summaryMessage(String text, String timestamp) {
        return summaryMessage(text, parseIsoTimestamp(timestamp));
    }

    private static Message.UserMessage summaryMessage(String text, long timestamp) {
        return new Message.UserMessage(List.of(new TextContent(text, null)), timestamp);
    }

    private static long toMessageTimestamp(JsonNode node) {
        var timestampNode = node.path("timestamp");
        if (timestampNode.canConvertToLong()) {
            return timestampNode.longValue();
        }
        if (timestampNode.isTextual()) {
            return parseIsoTimestamp(timestampNode.asText());
        }
        return 0L;
    }

    private static long parseIsoTimestamp(String timestamp) {
        return Instant.parse(timestamp).toEpochMilli();
    }

    private static String formatBashExecution(JsonNode node) {
        var builder = new StringBuilder("Ran `")
            .append(node.path("command").asText(""))
            .append("`\n");
        var output = node.path("output").asText("");
        if (!output.isEmpty()) {
            builder.append("```\n").append(output).append("\n```");
        } else {
            builder.append("(no output)");
        }

        if (node.path("cancelled").asBoolean(false)) {
            builder.append("\n\n(command cancelled)");
        } else if (!node.path("exitCode").isMissingNode() && !node.path("exitCode").isNull()) {
            var exitCode = node.path("exitCode").asInt();
            if (exitCode != 0) {
                builder.append("\n\nCommand exited with code ").append(exitCode);
            }
        }

        if (node.path("truncated").asBoolean(false) && node.hasNonNull("fullOutputPath")) {
            builder.append("\n\n[Output truncated. Full output: ")
                .append(node.path("fullOutputPath").asText())
                .append("]");
        }
        return builder.toString();
    }
}
