package dev.pi.sdk;

import dev.pi.agent.runtime.AgentLoopConfig;
import dev.pi.agent.runtime.AgentTool;
import dev.pi.ai.model.CacheRetention;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.ThinkingBudgets;
import dev.pi.ai.model.ThinkingLevel;
import dev.pi.ai.model.Transport;
import dev.pi.session.InstructionResources;
import dev.pi.session.SessionManager;
import dev.pi.session.SettingsManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CreateAgentSessionOptions(
    Model model,
    AgentLoopConfig.AssistantStreamFunction streamFunction,
    SessionManager sessionManager,
    SettingsManager settingsManager,
    InstructionResources instructionResources,
    String systemPrompt,
    String appendSystemPrompt,
    ThinkingLevel thinkingLevel,
    List<AgentTool<?>> tools,
    AgentLoopConfig.MessageConverter convertToLlm,
    AgentLoopConfig.ContextTransformer transformContext,
    AgentLoopConfig.ApiKeyProvider apiKeyProvider,
    String apiKey,
    Transport transport,
    CacheRetention cacheRetention,
    String sessionId,
    Map<String, String> headers,
    Long maxRetryDelayMs,
    ThinkingBudgets thinkingBudgets
) {
    public CreateAgentSessionOptions {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(streamFunction, "streamFunction");
        Objects.requireNonNull(sessionManager, "sessionManager");
        settingsManager = settingsManager == null ? SettingsManager.inMemory() : settingsManager;
        instructionResources = instructionResources == null ? InstructionResources.empty() : instructionResources;
        tools = tools == null ? List.of() : List.copyOf(tools);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public static Builder builder(
        Model model,
        AgentLoopConfig.AssistantStreamFunction streamFunction,
        SessionManager sessionManager
    ) {
        return new Builder(model, streamFunction, sessionManager);
    }

    public static final class Builder {
        private final Model model;
        private final AgentLoopConfig.AssistantStreamFunction streamFunction;
        private final SessionManager sessionManager;
        private SettingsManager settingsManager;
        private InstructionResources instructionResources;
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
            AgentLoopConfig.AssistantStreamFunction streamFunction,
            SessionManager sessionManager
        ) {
            this.model = Objects.requireNonNull(model, "model");
            this.streamFunction = Objects.requireNonNull(streamFunction, "streamFunction");
            this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        }

        public Builder settingsManager(SettingsManager settingsManager) {
            this.settingsManager = settingsManager;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder instructionResources(InstructionResources instructionResources) {
            this.instructionResources = instructionResources;
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

        public CreateAgentSessionOptions build() {
            return new CreateAgentSessionOptions(
                model,
                streamFunction,
                sessionManager,
                settingsManager,
                instructionResources,
                systemPrompt,
                appendSystemPrompt,
                thinkingLevel,
                tools,
                convertToLlm,
                transformContext,
                apiKeyProvider,
                apiKey,
                transport,
                cacheRetention,
                sessionId,
                headers,
                maxRetryDelayMs,
                thinkingBudgets
            );
        }
    }
}
