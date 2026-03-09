package dev.pi.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;

public sealed interface AssistantMessageEvent permits
    AssistantMessageEvent.Start,
    AssistantMessageEvent.TextStart,
    AssistantMessageEvent.TextDelta,
    AssistantMessageEvent.TextEnd,
    AssistantMessageEvent.ThinkingStart,
    AssistantMessageEvent.ThinkingDelta,
    AssistantMessageEvent.ThinkingEnd,
    AssistantMessageEvent.ToolcallStart,
    AssistantMessageEvent.ToolcallDelta,
    AssistantMessageEvent.ToolcallEnd,
    AssistantMessageEvent.Done,
    AssistantMessageEvent.Error {

    String type();

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Start(String type, Message.AssistantMessage partial) implements AssistantMessageEvent {
        public Start(Message.AssistantMessage partial) {
            this("start", partial);
        }

        public Start {
            requireType(type, "start");
            Objects.requireNonNull(partial, "partial");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TextStart(String type, int contentIndex, Message.AssistantMessage partial) implements AssistantMessageEvent {
        public TextStart(int contentIndex, Message.AssistantMessage partial) {
            this("text_start", contentIndex, partial);
        }

        public TextStart {
            requireType(type, "text_start");
            Objects.requireNonNull(partial, "partial");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TextDelta(String type, int contentIndex, String delta, Message.AssistantMessage partial) implements AssistantMessageEvent {
        public TextDelta(int contentIndex, String delta, Message.AssistantMessage partial) {
            this("text_delta", contentIndex, delta, partial);
        }

        public TextDelta {
            requireType(type, "text_delta");
            Objects.requireNonNull(delta, "delta");
            Objects.requireNonNull(partial, "partial");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TextEnd(String type, int contentIndex, String content, Message.AssistantMessage partial) implements AssistantMessageEvent {
        public TextEnd(int contentIndex, String content, Message.AssistantMessage partial) {
            this("text_end", contentIndex, content, partial);
        }

        public TextEnd {
            requireType(type, "text_end");
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(partial, "partial");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ThinkingStart(String type, int contentIndex, Message.AssistantMessage partial) implements AssistantMessageEvent {
        public ThinkingStart(int contentIndex, Message.AssistantMessage partial) {
            this("thinking_start", contentIndex, partial);
        }

        public ThinkingStart {
            requireType(type, "thinking_start");
            Objects.requireNonNull(partial, "partial");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ThinkingDelta(String type, int contentIndex, String delta, Message.AssistantMessage partial) implements AssistantMessageEvent {
        public ThinkingDelta(int contentIndex, String delta, Message.AssistantMessage partial) {
            this("thinking_delta", contentIndex, delta, partial);
        }

        public ThinkingDelta {
            requireType(type, "thinking_delta");
            Objects.requireNonNull(delta, "delta");
            Objects.requireNonNull(partial, "partial");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ThinkingEnd(String type, int contentIndex, String content, Message.AssistantMessage partial) implements AssistantMessageEvent {
        public ThinkingEnd(int contentIndex, String content, Message.AssistantMessage partial) {
            this("thinking_end", contentIndex, content, partial);
        }

        public ThinkingEnd {
            requireType(type, "thinking_end");
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(partial, "partial");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ToolcallStart(String type, int contentIndex, Message.AssistantMessage partial) implements AssistantMessageEvent {
        public ToolcallStart(int contentIndex, Message.AssistantMessage partial) {
            this("toolcall_start", contentIndex, partial);
        }

        public ToolcallStart {
            requireType(type, "toolcall_start");
            Objects.requireNonNull(partial, "partial");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ToolcallDelta(String type, int contentIndex, String delta, Message.AssistantMessage partial) implements AssistantMessageEvent {
        public ToolcallDelta(int contentIndex, String delta, Message.AssistantMessage partial) {
            this("toolcall_delta", contentIndex, delta, partial);
        }

        public ToolcallDelta {
            requireType(type, "toolcall_delta");
            Objects.requireNonNull(delta, "delta");
            Objects.requireNonNull(partial, "partial");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ToolcallEnd(String type, int contentIndex, ToolCall toolCall, Message.AssistantMessage partial) implements AssistantMessageEvent {
        public ToolcallEnd(int contentIndex, ToolCall toolCall, Message.AssistantMessage partial) {
            this("toolcall_end", contentIndex, toolCall, partial);
        }

        public ToolcallEnd {
            requireType(type, "toolcall_end");
            Objects.requireNonNull(toolCall, "toolCall");
            Objects.requireNonNull(partial, "partial");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Done(String type, StopReason reason, Message.AssistantMessage message) implements AssistantMessageEvent {
        public Done(StopReason reason, Message.AssistantMessage message) {
            this("done", reason, message);
        }

        public Done {
            requireType(type, "done");
            if (reason != StopReason.STOP && reason != StopReason.LENGTH && reason != StopReason.TOOL_USE) {
                throw new IllegalArgumentException("Done reason must be stop, length, or toolUse");
            }
            Objects.requireNonNull(message, "message");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Error(String type, StopReason reason, Message.AssistantMessage error) implements AssistantMessageEvent {
        public Error(StopReason reason, Message.AssistantMessage error) {
            this("error", reason, error);
        }

        public Error {
            requireType(type, "error");
            if (reason != StopReason.ERROR && reason != StopReason.ABORTED) {
                throw new IllegalArgumentException("Error reason must be error or aborted");
            }
            Objects.requireNonNull(error, "error");
        }
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("Event type must be '" + expected + "'");
        }
    }
}
