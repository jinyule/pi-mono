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
import dev.pi.sdk.CreateAgentSessionOptions;
import dev.pi.sdk.PiSdkSession;
import dev.pi.session.InstructionResourceLoader;
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
import java.util.function.Consumer;

public final class PiAgentSession implements PiInteractiveSession {
    private final PiSdkSession sdkSession;
    private final SettingsManager settingsManager;
    private final InstructionResourceLoader instructionResourceLoader;
    private volatile InstructionResources instructionResources;
    private final String systemPrompt;
    private final String appendSystemPrompt;
    private final ReloadAction reloadAction;

    private PiAgentSession(
        PiSdkSession sdkSession,
        SettingsManager settingsManager,
        InstructionResourceLoader instructionResourceLoader,
        InstructionResources instructionResources,
        String systemPrompt,
        String appendSystemPrompt,
        ReloadAction reloadAction
    ) {
        this.sdkSession = Objects.requireNonNull(sdkSession, "sdkSession");
        this.settingsManager = Objects.requireNonNull(settingsManager, "settingsManager");
        this.instructionResourceLoader = instructionResourceLoader;
        this.instructionResources = Objects.requireNonNull(instructionResources, "instructionResources");
        this.systemPrompt = systemPrompt;
        this.appendSystemPrompt = appendSystemPrompt;
        this.reloadAction = reloadAction;
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
        return sdkSession.agent();
    }

    public SessionManager sessionManager() {
        return sdkSession.sessionManager();
    }

    public SettingsManager settingsManager() {
        return settingsManager;
    }

    public InstructionResources instructionResources() {
        return instructionResources;
    }

    @Override
    public String sessionId() {
        return sdkSession.sessionId();
    }

    @Override
    public AgentState state() {
        return sdkSession.state();
    }

    @Override
    public Subscription subscribe(Consumer<AgentEvent> listener) {
        return sdkSession.subscribe(listener);
    }

    @Override
    public Subscription subscribeState(Consumer<AgentState> listener) {
        return sdkSession.subscribeState(listener);
    }

    @Override
    public CompletionStage<Void> prompt(String text) {
        return sdkSession.prompt(text);
    }

    @Override
    public CompletionStage<Void> prompt(AgentMessage.UserMessage message) {
        return sdkSession.prompt(message);
    }

    public CompletionStage<Void> prompt(AgentMessage message) {
        return sdkSession.prompt(message);
    }

    @Override
    public CompletionStage<Void> resume() {
        return sdkSession.resume();
    }

    @Override
    public CompletionStage<Void> waitForIdle() {
        return sdkSession.waitForIdle();
    }

    @Override
    public void abort() {
        sdkSession.abort();
    }

    @Override
    public String cycleThinkingLevel() {
        var model = sdkSession.state().model();
        if (!model.reasoning()) {
            throw new UnsupportedOperationException("Thinking level is not available for current model");
        }
        var next = nextThinkingLevel(sdkSession.state().thinkingLevel());
        sdkSession.agent().setThinkingLevel(next);
        try {
            sessionManager().appendThinkingLevelChange(next == null ? "off" : next.value());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist thinking level", exception);
        }
        return next == null ? "off" : next.value();
    }

    @Override
    public String leafId() {
        return sessionManager().leafId();
    }

    @Override
    public List<SessionTreeNode> tree() {
        return sessionManager().tree();
    }

    @Override
    public TreeNavigationResult navigateTree(String targetId) {
        Objects.requireNonNull(targetId, "targetId");
        if (sdkSession.state().isStreaming()) {
            throw new IllegalStateException("Cannot navigate tree while agent is processing");
        }

        var entry = sessionManager().entry(targetId);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown session entry: " + targetId);
        }

        var editorText = switch (entry) {
            case SessionEntry.MessageEntry messageEntry when "user".equals(messageEntry.message().path("role").asText()) -> {
                sessionManager().navigate(messageEntry.parentId());
                yield extractUserEditorText(messageEntry);
            }
            default -> {
                sessionManager().navigate(targetId);
                yield null;
            }
        };

        restoreAgentMessages();
        return new TreeNavigationResult(sessionManager().leafId(), editorText);
    }

    @Override
    public List<ForkMessage> forkMessages() {
        var messages = new ArrayList<ForkMessage>();
        for (var entry : sessionManager().entries()) {
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
        if (sdkSession.state().isStreaming()) {
            throw new IllegalStateException("Cannot fork while agent is processing");
        }

        var entry = sessionManager().entry(entryId);
        if (!(entry instanceof SessionEntry.MessageEntry messageEntry) || !"user".equals(messageEntry.message().path("role").asText())) {
            throw new IllegalArgumentException("Invalid entry ID for forking");
        }

        var selectedText = extractUserEditorText(messageEntry);
        try {
            sessionManager().createBranchedSession(messageEntry.parentId());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to fork session", exception);
        }
        sdkSession.agent().setSessionId(sessionManager().sessionId());
        seedSessionMetadataIfNeeded(sdkSession.state().thinkingLevel());
        restoreAgentMessages();
        return new ForkResult(selectedText, sessionManager().sessionId());
    }

    @Override
    public CompactionResult compact(String customInstructions) {
        if (sdkSession.state().isStreaming()) {
            throw new IllegalStateException("Wait for the current response to finish before compacting");
        }
        try {
            var result = PiCompactor.compact(sessionManager(), customInstructions);
            restoreAgentMessages();
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to compact session", exception);
        }
    }

    @Override
    public ReloadResult reload() {
        if (sdkSession.state().isStreaming()) {
            throw new IllegalStateException("Wait for the current response to finish before reloading");
        }

        settingsManager.drainErrors();
        settingsManager.reload();
        var settingsErrors = settingsManager.drainErrors();

        var resourceErrors = List.<InstructionResourceLoader.ResourceLoadError>of();
        if (instructionResourceLoader != null) {
            instructionResourceLoader.drainErrors();
            instructionResourceLoader.reload();
            instructionResources = instructionResourceLoader.resources();
            resourceErrors = instructionResourceLoader.drainErrors();
        }

        var extensionWarnings = List.<String>of();
        if (reloadAction != null) {
            try {
                extensionWarnings = List.copyOf(reloadAction.reload());
            } catch (Exception exception) {
                extensionWarnings = List.of(rootMessage(exception));
            }
        }

        sdkSession.updateSystemPrompt(systemPrompt, appendSystemPrompt, instructionResources);
        return new ReloadResult(settingsErrors, resourceErrors, extensionWarnings);
    }

    public List<SessionPersistenceError> drainPersistenceErrors() {
        return sdkSession.drainPersistenceErrors().stream()
            .map(error -> new SessionPersistenceError(error.operation(), error.error()))
            .toList();
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
        var restoredMessages = sessionManager().buildSessionContext().messages().stream()
            .map(AgentMessages::fromLlmMessage)
            .toList();
        sdkSession.agent().replaceMessages(restoredMessages);
    }

    private void seedSessionMetadataIfNeeded(ThinkingLevel thinkingLevel) {
        if (!sessionManager().entries().isEmpty()) {
            return;
        }
        try {
            sessionManager().appendModelChange(sdkSession.state().model().provider(), sdkSession.state().model().id());
            if (thinkingLevel != null) {
                sessionManager().appendThinkingLevelChange(thinkingLevel.value());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to seed session metadata", exception);
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

    @FunctionalInterface
    public interface ReloadAction {
        List<String> reload() throws Exception;
    }

    public static final class Builder {
        private final Model model;
        private final SessionManager sessionManager;
        private final SettingsManager settingsManager;
        private final InstructionResources instructionResources;
        private InstructionResourceLoader instructionResourceLoader;
        private AgentLoopConfig.AssistantStreamFunction streamFunction;
        private String systemPrompt;
        private String appendSystemPrompt;
        private ReloadAction reloadAction;
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

        public Builder instructionResourceLoader(InstructionResourceLoader instructionResourceLoader) {
            this.instructionResourceLoader = instructionResourceLoader;
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

        public Builder reloadAction(ReloadAction reloadAction) {
            this.reloadAction = reloadAction;
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

            var effectiveInstructionResources = instructionResourceLoader == null
                ? instructionResources
                : instructionResourceLoader.resources();
            var options = CreateAgentSessionOptions.builder(model, streamFunction, sessionManager)
                .settingsManager(settingsManager)
                .instructionResources(effectiveInstructionResources)
                .systemPrompt(systemPrompt)
                .appendSystemPrompt(appendSystemPrompt)
                .thinkingLevel(thinkingLevel)
                .tools(tools)
                .convertToLlm(convertToLlm)
                .transformContext(transformContext)
                .apiKeyProvider(apiKeyProvider)
                .apiKey(apiKey)
                .transport(transport)
                .cacheRetention(cacheRetention)
                .sessionId(sessionId)
                .headers(headers)
                .maxRetryDelayMs(maxRetryDelayMs)
                .thinkingBudgets(thinkingBudgets)
                .build();

            return new PiAgentSession(
                PiSdkSession.create(options),
                settingsManager,
                instructionResourceLoader,
                effectiveInstructionResources,
                systemPrompt,
                appendSystemPrompt,
                reloadAction
            );
        }

    }

    private static String rootMessage(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static ThinkingLevel nextThinkingLevel(ThinkingLevel current) {
        return switch (current) {
            case null -> ThinkingLevel.MINIMAL;
            case MINIMAL -> ThinkingLevel.LOW;
            case LOW -> ThinkingLevel.MEDIUM;
            case MEDIUM -> ThinkingLevel.HIGH;
            case HIGH, XHIGH -> null;
        };
    }
}
