package dev.pi.sdk;

import dev.pi.agent.runtime.Agent;
import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentMessage;
import dev.pi.agent.runtime.AgentMessages;
import dev.pi.agent.runtime.AgentState;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.ThinkingLevel;
import dev.pi.ai.stream.Subscription;
import dev.pi.session.InstructionResources;
import dev.pi.session.SessionManager;
import dev.pi.session.SettingsManager;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class PiSdkSession {
    private final Agent agent;
    private final SessionManager sessionManager;
    private final SettingsManager settingsManager;
    private final CopyOnWriteArrayList<SessionPersistenceError> persistenceErrors = new CopyOnWriteArrayList<>();
    private final Subscription persistenceSubscription;

    private PiSdkSession(Agent agent, SessionManager sessionManager, SettingsManager settingsManager) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.settingsManager = Objects.requireNonNull(settingsManager, "settingsManager");
        this.persistenceSubscription = agent.subscribe(this::persistEvent);
    }

    public static PiSdkSession create(CreateAgentSessionOptions options) {
        var sessionContext = options.sessionManager().buildSessionContext();
        var restoredMessages = sessionContext.messages().stream()
            .map(AgentMessages::fromLlmMessage)
            .toList();
        var resolvedThinkingLevel = options.thinkingLevel() != null
            ? options.thinkingLevel()
            : toThinkingLevel(sessionContext.thinkingLevel());
        if (resolvedThinkingLevel == null) {
            resolvedThinkingLevel = toThinkingLevel(options.settingsManager().effective().getString("/defaultThinkingLevel"));
        }
        var builder = Agent.builder(options.model())
            .systemPrompt(SessionPromptComposer.compose(
                options.systemPrompt(),
                options.appendSystemPrompt(),
                options.instructionResources()
            ))
            .thinkingLevel(resolvedThinkingLevel)
            .steeringMode(toQueueMode(options.settingsManager().effective().getString("/steeringMode")))
            .followUpMode(toQueueMode(options.settingsManager().effective().getString("/followUpMode")))
            .tools(options.tools())
            .messages(restoredMessages)
            .streamFunction(options.streamFunction())
            .sessionId(options.sessionId() == null ? options.sessionManager().sessionId() : options.sessionId())
            .headers(options.headers());

        if (options.convertToLlm() != null) {
            builder.convertToLlm(options.convertToLlm());
        }
        if (options.transformContext() != null) {
            builder.transformContext(options.transformContext());
        }
        if (options.apiKeyProvider() != null) {
            builder.apiKeyProvider(options.apiKeyProvider());
        }
        if (options.apiKey() != null) {
            builder.apiKey(options.apiKey());
        }
        if (options.transport() != null) {
            builder.transport(options.transport());
        }
        if (options.cacheRetention() != null) {
            builder.cacheRetention(options.cacheRetention());
        }
        if (options.maxRetryDelayMs() != null) {
            builder.maxRetryDelayMs(options.maxRetryDelayMs());
        }
        if (options.thinkingBudgets() != null) {
            builder.thinkingBudgets(options.thinkingBudgets());
        }

        var session = new PiSdkSession(builder.build(), options.sessionManager(), options.settingsManager());
        session.seedSessionMetadataIfNeeded(resolvedThinkingLevel);
        return session;
    }

    public Agent agent() {
        return agent;
    }

    public SessionManager sessionManager() {
        return sessionManager;
    }

    public SettingsManager settingsManager() {
        return settingsManager;
    }

    public String sessionId() {
        return sessionManager.sessionId();
    }

    public AgentState state() {
        return agent.state();
    }

    public Subscription subscribe(Consumer<AgentEvent> listener) {
        return agent.subscribe(listener);
    }

    public Subscription subscribeState(Consumer<AgentState> listener) {
        return agent.subscribeState(listener);
    }

    public CompletionStage<Void> prompt(String text) {
        return agent.prompt(text);
    }

    public CompletionStage<Void> prompt(AgentMessage message) {
        return agent.prompt(message);
    }

    public CompletionStage<Void> resume() {
        return agent.resume();
    }

    public CompletionStage<Void> waitForIdle() {
        return agent.waitForIdle();
    }

    public void abort() {
        agent.abort();
    }

    public void updateSystemPrompt(
        String systemPrompt,
        String appendSystemPrompt,
        InstructionResources instructionResources
    ) {
        agent.setSystemPrompt(SessionPromptComposer.compose(systemPrompt, appendSystemPrompt, instructionResources));
    }

    public List<SessionPersistenceError> drainPersistenceErrors() {
        var drained = List.copyOf(persistenceErrors);
        persistenceErrors.clear();
        return drained;
    }

    private void persistEvent(AgentEvent event) {
        if (!(event instanceof AgentEvent.MessageEnd messageEnd)) {
            return;
        }
        if (messageEnd.message() instanceof AgentMessage.CustomMessage) {
            return;
        }
        try {
            sessionManager.appendMessage(AgentMessages.toLlmMessage(messageEnd.message()));
        } catch (IOException exception) {
            persistenceErrors.add(new SessionPersistenceError("append_message", exception));
        }
    }

    private void seedSessionMetadataIfNeeded(ThinkingLevel thinkingLevel) {
        if (!sessionManager.entries().isEmpty()) {
            return;
        }
        try {
            sessionManager.appendModelChange(agent.state().model().provider(), agent.state().model().id());
            if (thinkingLevel != null) {
                sessionManager.appendThinkingLevelChange(thinkingLevel.value());
            }
        } catch (IOException exception) {
            persistenceErrors.add(new SessionPersistenceError("seed_session_metadata", exception));
        }
    }

    private static ThinkingLevel toThinkingLevel(String value) {
        if (value == null || value.isBlank() || "off".equals(value)) {
            return null;
        }
        return ThinkingLevel.fromValue(value);
    }

    private static Agent.QueueMode toQueueMode(String value) {
        if ("all".equals(value)) {
            return Agent.QueueMode.ALL;
        }
        return Agent.QueueMode.ONE_AT_A_TIME;
    }

    public record SessionPersistenceError(
        String operation,
        IOException error
    ) {
        public SessionPersistenceError {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(error, "error");
        }
    }
}
