package dev.pi.session;

import dev.pi.ai.model.Message;
import java.util.List;
import java.util.Objects;

public record SessionContext(
    List<Message> messages,
    String thinkingLevel,
    ModelSelection model
) {
    public SessionContext {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        thinkingLevel = thinkingLevel == null ? "off" : thinkingLevel;
    }

    public record ModelSelection(
        String provider,
        String modelId
    ) {
        public ModelSelection {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(modelId, "modelId");
        }
    }
}
