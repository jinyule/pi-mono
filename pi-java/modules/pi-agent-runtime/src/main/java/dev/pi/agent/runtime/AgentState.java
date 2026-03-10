package dev.pi.agent.runtime;

import dev.pi.ai.model.Model;
import dev.pi.ai.model.ThinkingLevel;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record AgentState(
    String systemPrompt,
    Model model,
    ThinkingLevel thinkingLevel,
    List<AgentTool<?>> tools,
    List<AgentMessage> messages,
    boolean isStreaming,
    AgentMessage streamMessage,
    Set<String> pendingToolCalls,
    String error
) {
    public AgentState {
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        Objects.requireNonNull(model, "model");
        thinkingLevel = thinkingLevel == null ? ThinkingLevel.MINIMAL : thinkingLevel;
        tools = tools == null ? List.of() : List.copyOf(tools);
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        pendingToolCalls = Set.copyOf(Objects.requireNonNullElseGet(pendingToolCalls, LinkedHashSet::new));
    }

    public AgentState startStreaming(AgentMessage streamMessage) {
        return new AgentState(
            systemPrompt,
            model,
            thinkingLevel,
            tools,
            messages,
            true,
            streamMessage,
            pendingToolCalls,
            null
        );
    }

    public AgentState finishStreaming(List<AgentMessage> messages) {
        return new AgentState(
            systemPrompt,
            model,
            thinkingLevel,
            tools,
            messages,
            false,
            null,
            pendingToolCalls,
            error
        );
    }

    public AgentState withError(String error) {
        return new AgentState(
            systemPrompt,
            model,
            thinkingLevel,
            tools,
            messages,
            false,
            null,
            pendingToolCalls,
            error
        );
    }
}
