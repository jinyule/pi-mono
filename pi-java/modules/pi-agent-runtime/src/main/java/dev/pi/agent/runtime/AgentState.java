package dev.pi.agent.runtime;

import dev.pi.ai.model.Model;
import dev.pi.ai.model.ThinkingLevel;
import java.util.ArrayList;
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
        tools = tools == null ? List.of() : List.copyOf(tools);
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        pendingToolCalls = Set.copyOf(Objects.requireNonNullElseGet(pendingToolCalls, LinkedHashSet::new));
    }

    public AgentState withSystemPrompt(String systemPrompt) {
        return new AgentState(
            systemPrompt,
            model,
            thinkingLevel,
            tools,
            messages,
            isStreaming,
            streamMessage,
            pendingToolCalls,
            error
        );
    }

    public AgentState withModel(Model model) {
        return new AgentState(
            systemPrompt,
            model,
            thinkingLevel,
            tools,
            messages,
            isStreaming,
            streamMessage,
            pendingToolCalls,
            error
        );
    }

    public AgentState withThinkingLevel(ThinkingLevel thinkingLevel) {
        return new AgentState(
            systemPrompt,
            model,
            thinkingLevel,
            tools,
            messages,
            isStreaming,
            streamMessage,
            pendingToolCalls,
            error
        );
    }

    public AgentState withTools(List<AgentTool<?>> tools) {
        return new AgentState(
            systemPrompt,
            model,
            thinkingLevel,
            tools,
            messages,
            isStreaming,
            streamMessage,
            pendingToolCalls,
            error
        );
    }

    public AgentState withMessages(List<AgentMessage> messages) {
        return new AgentState(
            systemPrompt,
            model,
            thinkingLevel,
            tools,
            messages,
            isStreaming,
            streamMessage,
            pendingToolCalls,
            error
        );
    }

    public AgentState appendMessage(AgentMessage message) {
        var updatedMessages = new ArrayList<>(messages);
        updatedMessages.add(message);
        return withMessages(List.copyOf(updatedMessages));
    }

    public AgentState clearMessages() {
        return withMessages(List.of());
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

    public AgentState withStreamingMessage(AgentMessage streamMessage) {
        return new AgentState(
            systemPrompt,
            model,
            thinkingLevel,
            tools,
            messages,
            isStreaming,
            streamMessage,
            pendingToolCalls,
            error
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

    public AgentState addPendingToolCall(String toolCallId) {
        var updatedPendingToolCalls = new LinkedHashSet<>(pendingToolCalls);
        updatedPendingToolCalls.add(toolCallId);
        return new AgentState(
            systemPrompt,
            model,
            thinkingLevel,
            tools,
            messages,
            isStreaming,
            streamMessage,
            updatedPendingToolCalls,
            error
        );
    }

    public AgentState removePendingToolCall(String toolCallId) {
        var updatedPendingToolCalls = new LinkedHashSet<>(pendingToolCalls);
        updatedPendingToolCalls.remove(toolCallId);
        return new AgentState(
            systemPrompt,
            model,
            thinkingLevel,
            tools,
            messages,
            isStreaming,
            streamMessage,
            updatedPendingToolCalls,
            error
        );
    }

    public AgentState clearPendingToolCalls() {
        return new AgentState(
            systemPrompt,
            model,
            thinkingLevel,
            tools,
            messages,
            isStreaming,
            streamMessage,
            Set.of(),
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

    public AgentState clearError() {
        return new AgentState(
            systemPrompt,
            model,
            thinkingLevel,
            tools,
            messages,
            isStreaming,
            streamMessage,
            pendingToolCalls,
            null
        );
    }
}
