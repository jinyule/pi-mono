package dev.pi.cli;

import dev.pi.agent.runtime.Agent;
import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentLoopConfig;
import dev.pi.agent.runtime.AgentMessage;
import dev.pi.agent.runtime.AgentMessages;
import dev.pi.agent.runtime.AgentState;
import dev.pi.agent.runtime.AgentTool;
import dev.pi.ai.model.CacheRetention;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.ThinkingBudgets;
import dev.pi.ai.model.ThinkingLevel;
import dev.pi.ai.model.Transport;
import dev.pi.ai.stream.Subscription;
import dev.pi.session.InstructionFile;
import dev.pi.session.InstructionResources;
import dev.pi.session.SessionEntry;
import dev.pi.session.SessionManager;
import dev.pi.session.SessionTreeNode;
import dev.pi.session.SettingsManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class PiAgentSession implements PiInteractiveSession {
    private final Agent agent;
    private final SessionManager sessionManager;
    private final SettingsManager settingsManager;
    private final InstructionResources instructionResources;
    private final CopyOnWriteArrayList<SessionPersistenceError> persistenceErrors = new CopyOnWriteArrayList<>();
    private final Subscription persistenceSubscription;

    private PiAgentSession(
        Agent agent,
        SessionManager sessionManager,
        SettingsManager settingsManager,
        InstructionResources instructionResources
    ) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.settingsManager = Objects.requireNonNull(settingsManager, "settingsManager");
        this.instructionResources = Objects.requireNonNull(instructionResources, "instructionResources");
        this.persistenceSubscription = agent.subscribe(this::persistEvent);
    }

    public static Builder builder(
        Model model,
        SessionManager sessionManager,
        SettingsManager settingsManager,
        InstructionResources instructionResources
    ) {
        return new Builder(model, sessionManager, settingsManager, instructionResources);
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

    public InstructionResources instructionResources() {
        return instructionResources;
    }

    @Override
    public String sessionId() {
        return sessionManager.sessionId();
    }

    @Override
    public AgentState state() {
        return agent.state();
    }

    @Override
    public Subscription subscribe(Consumer<AgentEvent> listener) {
        return agent.subscribe(listener);
    }

    @Override
    public Subscription subscribeState(Consumer<AgentState> listener) {
        return agent.subscribeState(listener);
    }

    @Override
    public CompletionStage<Void> prompt(String text) {
        return agent.prompt(text);
    }

    public CompletionStage<Void> prompt(AgentMessage message) {
        return agent.prompt(message);
    }

    @Override
    public CompletionStage<Void> resume() {
        return agent.resume();
    }

    @Override
    public CompletionStage<Void> waitForIdle() {
        return agent.waitForIdle();
    }

    @Override
    public void abort() {
        agent.abort();
    }

    @Override
    public String leafId() {
        return sessionManager.leafId();
    }

    @Override
    public List<SessionTreeNode> tree() {
        return sessionManager.tree();
    }

    @Override
    public TreeNavigationResult navigateTree(String targetId) {
        Objects.requireNonNull(targetId, "targetId");
        if (agent.state().isStreaming()) {
            throw new IllegalStateException("Cannot navigate tree while agent is processing");
        }

        var entry = sessionManager.entry(targetId);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown session entry: " + targetId);
        }

        var editorText = switch (entry) {
            case SessionEntry.MessageEntry messageEntry when "user".equals(messageEntry.message().path("role").asText()) -> {
                sessionManager.navigate(messageEntry.parentId());
                yield extractUserEditorText(messageEntry);
            }
            default -> {
                sessionManager.navigate(targetId);
                yield null;
            }
        };

        restoreAgentMessages();
        return new TreeNavigationResult(sessionManager.leafId(), editorText);
    }

    @Override
    public List<ForkMessage> forkMessages() {
        var messages = new ArrayList<ForkMessage>();
        for (var entry : sessionManager.entries()) {
            if (!(entry instanceof SessionEntry.MessageEntry messageEntry)) {
                continue;
            }
            if (!"user".equals(messageEntry.message().path("role").asText())) {
                continue;
            }
            var text = extractUserEditorText(messageEntry);
            if (!text.isBlank()) {
                messages.add(new ForkMessage(messageEntry.id(), text));
            }
        }
        return List.copyOf(messages);
    }

    @Override
    public ForkResult fork(String entryId) {
        Objects.requireNonNull(entryId, "entryId");
        if (agent.state().isStreaming()) {
            throw new IllegalStateException("Cannot fork while agent is processing");
        }

        var entry = sessionManager.entry(entryId);
        if (!(entry instanceof SessionEntry.MessageEntry messageEntry) || !"user".equals(messageEntry.message().path("role").asText())) {
            throw new IllegalArgumentException("Invalid entry ID for forking");
        }

        var selectedText = extractUserEditorText(messageEntry);
        try {
            sessionManager.createBranchedSession(messageEntry.parentId());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to fork session", exception);
        }
        agent.setSessionId(sessionManager.sessionId());
        seedSessionMetadataIfNeeded(agent.state().thinkingLevel());
        restoreAgentMessages();
        return new ForkResult(selectedText, sessionManager.sessionId());
    }

    @Override
    public CompactionResult compact(String customInstructions) {
        if (agent.state().isStreaming()) {
            throw new IllegalStateException("Wait for the current response to finish before compacting");
        }
        try {
            var result = PiCompactor.compact(sessionManager, customInstructions);
            restoreAgentMessages();
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to compact session", exception);
        }
    }

    public List<SessionPersistenceError> drainPersistenceErrors() {
        var drained = List.copyOf(persistenceErrors);
        persistenceErrors.clear();
        return drained;
    }

    private static String extractUserEditorText(SessionEntry.MessageEntry entry) {
        var content = entry.message().path("content");
        if (content.isTextual()) {
            return content.asText("");
        }
        if (!content.isArray()) {
            return "";
        }

        var lines = new ArrayList<String>();
        for (var item : content) {
            if (!"text".equals(item.path("type").asText())) {
                continue;
            }
            var text = item.path("text").asText("");
            if (!text.isBlank()) {
                lines.add(text);
            }
        }
        return String.join("\n", lines);
    }

    private void restoreAgentMessages() {
        var restoredMessages = sessionManager.buildSessionContext().messages().stream()
            .map(AgentMessages::fromLlmMessage)
            .toList();
        agent.replaceMessages(restoredMessages);
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

    public record SessionPersistenceError(
        String operation,
        IOException error
    ) {
        public SessionPersistenceError {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(error, "error");
        }
    }

    public static final class Builder {
        private final Model model;
        private final SessionManager sessionManager;
        private final SettingsManager settingsManager;
        private final InstructionResources instructionResources;
        private AgentLoopConfig.AssistantStreamFunction streamFunction;
        private String systemPrompt;
        private String appendSystemPrompt;
        private ThinkingLevel thinkingLevel;
        private List<AgentTool<?>> tools = List.of();
        private AgentLoopConfig.MessageConverter convertToLlm;
        private AgentLoopConfig.ContextTransformer transformContext;
        private AgentLoopConfig.ApiKeyProvider apiKeyProvider;
        private String apiKey;
        private Transport transport;
        private CacheRetention cacheRetention;
        private String sessionId;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Long maxRetryDelayMs;
        private ThinkingBudgets thinkingBudgets;

        private Builder(
            Model model,
            SessionManager sessionManager,
            SettingsManager settingsManager,
            InstructionResources instructionResources
        ) {
            this.model = Objects.requireNonNull(model, "model");
            this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
            this.settingsManager = Objects.requireNonNull(settingsManager, "settingsManager");
            this.instructionResources = Objects.requireNonNull(instructionResources, "instructionResources");
        }

        public Builder streamFunction(AgentLoopConfig.AssistantStreamFunction streamFunction) {
            this.streamFunction = streamFunction;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder appendSystemPrompt(String appendSystemPrompt) {
            this.appendSystemPrompt = appendSystemPrompt;
            return this;
        }

        public Builder thinkingLevel(ThinkingLevel thinkingLevel) {
            this.thinkingLevel = thinkingLevel;
            return this;
        }

        public Builder tools(List<AgentTool<?>> tools) {
            this.tools = tools == null ? List.of() : List.copyOf(tools);
            return this;
        }

        public Builder convertToLlm(AgentLoopConfig.MessageConverter convertToLlm) {
            this.convertToLlm = convertToLlm;
            return this;
        }

        public Builder transformContext(AgentLoopConfig.ContextTransformer transformContext) {
            this.transformContext = transformContext;
            return this;
        }

        public Builder apiKeyProvider(AgentLoopConfig.ApiKeyProvider apiKeyProvider) {
            this.apiKeyProvider = apiKeyProvider;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder transport(Transport transport) {
            this.transport = transport;
            return this;
        }

        public Builder cacheRetention(CacheRetention cacheRetention) {
            this.cacheRetention = cacheRetention;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers.clear();
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }

        public Builder header(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        public Builder maxRetryDelayMs(Long maxRetryDelayMs) {
            this.maxRetryDelayMs = maxRetryDelayMs;
            return this;
        }

        public Builder thinkingBudgets(ThinkingBudgets thinkingBudgets) {
            this.thinkingBudgets = thinkingBudgets;
            return this;
        }

        public PiAgentSession build() {
            if (streamFunction == null) {
                throw new IllegalStateException("PiAgentSession streamFunction must be configured");
            }

            var sessionContext = sessionManager.buildSessionContext();
            var restoredMessages = sessionContext.messages().stream()
                .map(AgentMessages::fromLlmMessage)
                .toList();
            var resolvedThinkingLevel = thinkingLevel != null ? thinkingLevel : toThinkingLevel(sessionContext.thinkingLevel());
            var agent = Agent.builder(model)
                .systemPrompt(composeSystemPrompt(systemPrompt, appendSystemPrompt, instructionResources))
                .thinkingLevel(resolvedThinkingLevel)
                .tools(tools)
                .messages(restoredMessages)
                .streamFunction(streamFunction)
                .sessionId(sessionId == null ? sessionManager.sessionId() : sessionId)
                .headers(headers)
                .build();

            if (convertToLlm != null) {
                agent = Agent.builder(model)
                    .systemPrompt(composeSystemPrompt(systemPrompt, appendSystemPrompt, instructionResources))
                    .thinkingLevel(resolvedThinkingLevel)
                    .tools(tools)
                    .messages(restoredMessages)
                    .streamFunction(streamFunction)
                    .convertToLlm(convertToLlm)
                    .transformContext(transformContext)
                    .apiKeyProvider(apiKeyProvider)
                    .apiKey(apiKey)
                    .transport(transport)
                    .cacheRetention(cacheRetention)
                    .sessionId(sessionId == null ? sessionManager.sessionId() : sessionId)
                    .headers(headers)
                    .maxRetryDelayMs(maxRetryDelayMs)
                    .thinkingBudgets(thinkingBudgets)
                    .build();
            } else if (
                transformContext != null ||
                apiKeyProvider != null ||
                apiKey != null ||
                transport != null ||
                cacheRetention != null ||
                maxRetryDelayMs != null ||
                thinkingBudgets != null
            ) {
                agent = Agent.builder(model)
                    .systemPrompt(composeSystemPrompt(systemPrompt, appendSystemPrompt, instructionResources))
                    .thinkingLevel(resolvedThinkingLevel)
                    .tools(tools)
                    .messages(restoredMessages)
                    .streamFunction(streamFunction)
                    .transformContext(transformContext)
                    .apiKeyProvider(apiKeyProvider)
                    .apiKey(apiKey)
                    .transport(transport)
                    .cacheRetention(cacheRetention)
                    .sessionId(sessionId == null ? sessionManager.sessionId() : sessionId)
                    .headers(headers)
                    .maxRetryDelayMs(maxRetryDelayMs)
                    .thinkingBudgets(thinkingBudgets)
                    .build();
            }

            var session = new PiAgentSession(agent, sessionManager, settingsManager, instructionResources);
            session.seedSessionMetadataIfNeeded(resolvedThinkingLevel);
            return session;
        }

        private static ThinkingLevel toThinkingLevel(String value) {
            if (value == null || value.isBlank() || "off".equals(value)) {
                return null;
            }
            return ThinkingLevel.fromValue(value);
        }

        private static String composeSystemPrompt(
            String explicitSystemPrompt,
            String appendSystemPrompt,
            InstructionResources instructionResources
        ) {
            var sections = new ArrayList<String>();
            var baseSystemPrompt = explicitSystemPrompt != null && !explicitSystemPrompt.isBlank()
                ? explicitSystemPrompt
                : instructionResources.systemPrompt();
            if (baseSystemPrompt != null && !baseSystemPrompt.isBlank()) {
                sections.add(baseSystemPrompt.trim());
            }
            for (var contextFile : instructionResources.contextFiles()) {
                sections.add(formatContextFile(contextFile));
            }
            for (var prompt : instructionResources.appendSystemPrompts()) {
                if (prompt != null && !prompt.isBlank()) {
                    sections.add(prompt.trim());
                }
            }
            if (appendSystemPrompt != null && !appendSystemPrompt.isBlank()) {
                sections.add(appendSystemPrompt.trim());
            }
            return String.join("\n\n", sections);
        }

        private static String formatContextFile(InstructionFile instructionFile) {
            return "Context from %s:\n%s".formatted(
                instructionFile.path().getFileName(),
                instructionFile.content().trim()
            );
        }
    }
}
