package dev.pi.ai.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.AssistantContent;
import dev.pi.ai.model.AssistantMessageEvent;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.ThinkingContent;
import dev.pi.ai.model.ToolCall;
import dev.pi.ai.model.Usage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AssistantMessageAssembler {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String api;
    private final String provider;
    private final String modelId;
    private final long timestamp;
    private final List<AssistantContent> content = new ArrayList<>();
    private final Map<Integer, StringBuilder> toolCallJsonBuffers = new HashMap<>();

    private Usage usage = zeroUsage();
    private StopReason stopReason = StopReason.STOP;
    private String errorMessage;

    public AssistantMessageAssembler(Model model, long timestamp) {
        this(
            Objects.requireNonNull(model, "model").api(),
            model.provider(),
            model.id(),
            timestamp
        );
    }

    public AssistantMessageAssembler(String api, String provider, String modelId, long timestamp) {
        this.api = requireValue(api, "api");
        this.provider = requireValue(provider, "provider");
        this.modelId = requireValue(modelId, "modelId");
        this.timestamp = timestamp;
    }

    public Message.AssistantMessage partial() {
        return snapshot();
    }

    public AssistantMessageEvent.Start start() {
        return new AssistantMessageEvent.Start(snapshot());
    }

    public AssistantMessageEvent.TextStart startText(int contentIndex) {
        putContent(contentIndex, new TextContent("", null));
        return new AssistantMessageEvent.TextStart(contentIndex, snapshot());
    }

    public AssistantMessageEvent.TextDelta appendText(int contentIndex, String delta) {
        var current = requireTextContent(contentIndex);
        putContent(contentIndex, new TextContent(current.text() + requireValue(delta, "delta"), current.textSignature()));
        return new AssistantMessageEvent.TextDelta(contentIndex, delta, snapshot());
    }

    public AssistantMessageEvent.TextEnd endText(int contentIndex, String textSignature) {
        var current = requireTextContent(contentIndex);
        putContent(contentIndex, new TextContent(current.text(), textSignature));
        return new AssistantMessageEvent.TextEnd(contentIndex, current.text(), snapshot());
    }

    public AssistantMessageEvent.ThinkingStart startThinking(int contentIndex) {
        putContent(contentIndex, new ThinkingContent("", null, false));
        return new AssistantMessageEvent.ThinkingStart(contentIndex, snapshot());
    }

    public AssistantMessageEvent.ThinkingDelta appendThinking(int contentIndex, String delta) {
        var current = requireThinkingContent(contentIndex);
        putContent(
            contentIndex,
            new ThinkingContent(current.thinking() + requireValue(delta, "delta"), current.thinkingSignature(), current.redacted())
        );
        return new AssistantMessageEvent.ThinkingDelta(contentIndex, delta, snapshot());
    }

    public AssistantMessageEvent.ThinkingEnd endThinking(int contentIndex, String thinkingSignature) {
        var current = requireThinkingContent(contentIndex);
        putContent(contentIndex, new ThinkingContent(current.thinking(), thinkingSignature, current.redacted()));
        return new AssistantMessageEvent.ThinkingEnd(contentIndex, current.thinking(), snapshot());
    }

    public AssistantMessageEvent.ToolcallStart startToolCall(int contentIndex, String id, String name) {
        return startToolCall(contentIndex, id, name, null);
    }

    public AssistantMessageEvent.ToolcallStart startToolCall(
        int contentIndex,
        String id,
        String name,
        String thoughtSignature
    ) {
        putContent(contentIndex, new ToolCall(requireValue(id, "id"), requireValue(name, "name"), JsonNodeFactory.instance.objectNode(), thoughtSignature));
        toolCallJsonBuffers.put(contentIndex, new StringBuilder());
        return new AssistantMessageEvent.ToolcallStart(contentIndex, snapshot());
    }

    public AssistantMessageEvent.ToolcallDelta appendToolCallArguments(int contentIndex, String delta) {
        var current = requireToolCall(contentIndex);
        var buffer = requireToolCallBuffer(contentIndex);
        buffer.append(requireValue(delta, "delta"));
        putContent(contentIndex, new ToolCall(current.id(), current.name(), parseStreamingJson(buffer.toString(), current.arguments()), current.thoughtSignature()));
        return new AssistantMessageEvent.ToolcallDelta(contentIndex, delta, snapshot());
    }

    public AssistantMessageEvent.ToolcallEnd endToolCall(int contentIndex) {
        var current = requireToolCall(contentIndex);
        var buffer = toolCallJsonBuffers.remove(contentIndex);
        var parsedArguments = buffer == null
            ? current.arguments()
            : parseStreamingJson(buffer.toString(), current.arguments());
        var completed = new ToolCall(current.id(), current.name(), parsedArguments, current.thoughtSignature());
        putContent(contentIndex, completed);
        return new AssistantMessageEvent.ToolcallEnd(contentIndex, completed, snapshot());
    }

    public AssistantMessageEvent.Done done(StopReason reason, Usage usage) {
        this.stopReason = Objects.requireNonNull(reason, "reason");
        this.usage = Objects.requireNonNull(usage, "usage");
        this.errorMessage = null;
        return new AssistantMessageEvent.Done(reason, snapshot());
    }

    public AssistantMessageEvent.Error error(StopReason reason, Usage usage, String errorMessage) {
        this.stopReason = Objects.requireNonNull(reason, "reason");
        this.usage = Objects.requireNonNull(usage, "usage");
        this.errorMessage = errorMessage;
        return new AssistantMessageEvent.Error(reason, snapshot());
    }

    private Message.AssistantMessage snapshot() {
        return new Message.AssistantMessage(
            List.copyOf(content),
            api,
            provider,
            modelId,
            usage,
            stopReason,
            errorMessage,
            timestamp
        );
    }

    private void putContent(int contentIndex, AssistantContent value) {
        validateIndex(contentIndex);
        if (contentIndex == content.size()) {
            content.add(value);
            return;
        }
        content.set(contentIndex, value);
    }

    private TextContent requireTextContent(int contentIndex) {
        var block = requireContent(contentIndex);
        if (block instanceof TextContent textContent) {
            return textContent;
        }
        throw new IllegalStateException("Received text event for non-text content at index " + contentIndex);
    }

    private ThinkingContent requireThinkingContent(int contentIndex) {
        var block = requireContent(contentIndex);
        if (block instanceof ThinkingContent thinkingContent) {
            return thinkingContent;
        }
        throw new IllegalStateException("Received thinking event for non-thinking content at index " + contentIndex);
    }

    private ToolCall requireToolCall(int contentIndex) {
        var block = requireContent(contentIndex);
        if (block instanceof ToolCall toolCall) {
            return toolCall;
        }
        throw new IllegalStateException("Received toolcall event for non-toolCall content at index " + contentIndex);
    }

    private AssistantContent requireContent(int contentIndex) {
        validateIndex(contentIndex);
        if (contentIndex >= content.size()) {
            throw new IllegalStateException("No content initialized at index " + contentIndex);
        }
        return content.get(contentIndex);
    }

    private StringBuilder requireToolCallBuffer(int contentIndex) {
        var buffer = toolCallJsonBuffers.get(contentIndex);
        if (buffer == null) {
            throw new IllegalStateException("No tool call buffer initialized at index " + contentIndex);
        }
        return buffer;
    }

    private void validateIndex(int contentIndex) {
        if (contentIndex < 0 || contentIndex > content.size()) {
            throw new IllegalArgumentException("contentIndex must be between 0 and " + content.size());
        }
    }

    private static JsonNode parseStreamingJson(String partialJson, JsonNode fallback) {
        if (partialJson == null || partialJson.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }

        try {
            var parsed = OBJECT_MAPPER.readTree(partialJson);
            return parsed == null ? JsonNodeFactory.instance.objectNode() : parsed.deepCopy();
        } catch (Exception ignored) {
            return fallback == null ? JsonNodeFactory.instance.objectNode() : fallback.deepCopy();
        }
    }

    private static Usage zeroUsage() {
        return new Usage(0, 0, 0, 0, 0, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static String requireValue(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
