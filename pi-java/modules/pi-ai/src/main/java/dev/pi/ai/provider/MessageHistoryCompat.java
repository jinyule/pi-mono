package dev.pi.ai.provider;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.AssistantContent;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.ToolCall;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MessageHistoryCompat {
    private MessageHistoryCompat() {
    }

    public static List<Message> transformMessages(
        List<Message> messages,
        Model model,
        long syntheticTimestamp,
        AssistantMessageTransformer assistantMessageTransformer
    ) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(assistantMessageTransformer, "assistantMessageTransformer");

        var compatContext = new CompatContext();
        var transformed = new ArrayList<Message>(messages.size());

        for (Message message : messages) {
            switch (message) {
                case Message.UserMessage userMessage -> transformed.add(userMessage);
                case Message.ToolResultMessage toolResultMessage -> transformed.add(remapToolResultMessage(toolResultMessage, compatContext));
                case Message.AssistantMessage assistantMessage -> transformed.add(
                    assistantMessageTransformer.transform(assistantMessage, model, compatContext)
                );
            }
        }

        var result = new ArrayList<Message>(transformed.size());
        var pendingToolCalls = new ArrayList<ToolCall>();
        var existingToolResultIds = new LinkedHashSet<String>();

        for (Message message : transformed) {
            switch (message) {
                case Message.UserMessage userMessage -> {
                    if (!pendingToolCalls.isEmpty()) {
                        appendSyntheticToolResults(result, pendingToolCalls, existingToolResultIds, userMessage.timestamp());
                        pendingToolCalls.clear();
                        existingToolResultIds.clear();
                    }
                    result.add(userMessage);
                }
                case Message.ToolResultMessage toolResultMessage -> {
                    existingToolResultIds.add(toolResultMessage.toolCallId());
                    result.add(toolResultMessage);
                }
                case Message.AssistantMessage assistantMessage -> {
                    if (!pendingToolCalls.isEmpty()) {
                        appendSyntheticToolResults(result, pendingToolCalls, existingToolResultIds, syntheticTimestamp);
                        pendingToolCalls.clear();
                        existingToolResultIds.clear();
                    }
                    if (assistantMessage.stopReason() == StopReason.ERROR || assistantMessage.stopReason() == StopReason.ABORTED) {
                        continue;
                    }
                    result.add(assistantMessage);
                    for (AssistantContent block : assistantMessage.content()) {
                        if (block instanceof ToolCall toolCall) {
                            pendingToolCalls.add(toolCall);
                        }
                    }
                }
            }
        }

        return List.copyOf(result);
    }

    public static Message.AssistantMessage rebuildAssistantMessage(
        Message.AssistantMessage assistantMessage,
        List<AssistantContent> content
    ) {
        Objects.requireNonNull(assistantMessage, "assistantMessage");
        Objects.requireNonNull(content, "content");
        return new Message.AssistantMessage(
            List.copyOf(content),
            assistantMessage.api(),
            assistantMessage.provider(),
            assistantMessage.model(),
            assistantMessage.usage(),
            assistantMessage.stopReason(),
            assistantMessage.errorMessage(),
            assistantMessage.timestamp()
        );
    }

    public static String normalizeToolCallId(String id) {
        Objects.requireNonNull(id, "id");
        var normalized = id.replaceAll("[^a-zA-Z0-9_-]", "_");
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    public static final class CompatContext {
        private final Map<String, String> toolCallIdMap = new LinkedHashMap<>();

        public boolean sameProviderAndModel(Message.AssistantMessage assistantMessage, Model model) {
            Objects.requireNonNull(assistantMessage, "assistantMessage");
            Objects.requireNonNull(model, "model");
            return assistantMessage.provider().equals(model.provider()) &&
                assistantMessage.api().equals(model.api()) &&
                assistantMessage.model().equals(model.id());
        }

        public String normalizeToolCallId(String id) {
            return MessageHistoryCompat.normalizeToolCallId(id);
        }

        public String remapToolCallId(String originalId, String remappedId) {
            Objects.requireNonNull(originalId, "originalId");
            Objects.requireNonNull(remappedId, "remappedId");
            if (!originalId.equals(remappedId)) {
                toolCallIdMap.put(originalId, remappedId);
            }
            return remappedId;
        }

        public String remapToolResultId(String originalId) {
            return toolCallIdMap.getOrDefault(originalId, originalId);
        }
    }

    @FunctionalInterface
    public interface AssistantMessageTransformer {
        Message.AssistantMessage transform(
            Message.AssistantMessage assistantMessage,
            Model model,
            CompatContext compatContext
        );
    }

    private static Message.ToolResultMessage remapToolResultMessage(
        Message.ToolResultMessage toolResultMessage,
        CompatContext compatContext
    ) {
        var remappedId = compatContext.remapToolResultId(toolResultMessage.toolCallId());
        if (remappedId.equals(toolResultMessage.toolCallId())) {
            return toolResultMessage;
        }
        return new Message.ToolResultMessage(
            remappedId,
            toolResultMessage.toolName(),
            toolResultMessage.content(),
            toolResultMessage.details(),
            toolResultMessage.isError(),
            toolResultMessage.timestamp()
        );
    }

    private static void appendSyntheticToolResults(
        List<Message> target,
        List<ToolCall> pendingToolCalls,
        Set<String> existingToolResultIds,
        long timestamp
    ) {
        for (ToolCall toolCall : pendingToolCalls) {
            if (!existingToolResultIds.contains(toolCall.id())) {
                target.add(new Message.ToolResultMessage(
                    toolCall.id(),
                    toolCall.name(),
                    List.of(new TextContent("No result provided", null)),
                    JsonNodeFactory.instance.nullNode(),
                    true,
                    timestamp
                ));
            }
        }
    }
}
