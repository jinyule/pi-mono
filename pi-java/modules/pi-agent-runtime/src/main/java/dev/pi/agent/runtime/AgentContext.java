package dev.pi.agent.runtime;

import java.util.List;
import java.util.Objects;

public record AgentContext(
    String systemPrompt,
    List<AgentMessage> messages,
    List<AgentTool<?>> tools
) {
    public AgentContext {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    public AgentContext appendMessages(List<AgentMessage> additionalMessages) {
        var merged = new java.util.ArrayList<AgentMessage>(messages.size() + additionalMessages.size());
        merged.addAll(messages);
        merged.addAll(additionalMessages);
        return new AgentContext(systemPrompt, List.copyOf(merged), tools);
    }

    public AgentContext appendMessage(AgentMessage message) {
        return appendMessages(List.of(message));
    }
}
