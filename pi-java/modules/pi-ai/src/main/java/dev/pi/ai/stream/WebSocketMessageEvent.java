package dev.pi.ai.stream;

import java.util.Objects;

public sealed interface WebSocketMessageEvent permits
    WebSocketMessageEvent.Message,
    WebSocketMessageEvent.TerminalEvent {

    String type();

    sealed interface TerminalEvent extends WebSocketMessageEvent permits Close, Error {}

    record Message(String type, String text) implements WebSocketMessageEvent {
        public Message(String text) {
            this("message", text);
        }

        public Message {
            if (!"message".equals(type)) {
                throw new IllegalArgumentException("Message event type must be 'message'");
            }
            Objects.requireNonNull(text, "text");
        }
    }

    record Close(String type, int statusCode, String reason) implements TerminalEvent {
        public Close(int statusCode, String reason) {
            this("close", statusCode, reason);
        }

        public Close {
            if (!"close".equals(type)) {
                throw new IllegalArgumentException("Close event type must be 'close'");
            }
            reason = reason == null ? "" : reason;
        }
    }

    record Error(String type, Throwable throwable) implements TerminalEvent {
        public Error(Throwable throwable) {
            this("error", throwable);
        }

        public Error {
            if (!"error".equals(type)) {
                throw new IllegalArgumentException("Error event type must be 'error'");
            }
            Objects.requireNonNull(throwable, "throwable");
        }
    }
}
