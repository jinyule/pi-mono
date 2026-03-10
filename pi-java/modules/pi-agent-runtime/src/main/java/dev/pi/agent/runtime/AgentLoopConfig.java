package dev.pi.agent.runtime;

import dev.pi.ai.PiAiClient;
import dev.pi.ai.model.CacheRetention;
import dev.pi.ai.model.Context;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.SimpleStreamOptions;
import dev.pi.ai.model.ThinkingBudgets;
import dev.pi.ai.model.ThinkingLevel;
import dev.pi.ai.model.Transport;
import dev.pi.ai.stream.AssistantMessageEventStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class AgentLoopConfig {
    private final Model model;
    private final ThinkingLevel thinkingLevel;
    private final MessageConverter convertToLlm;
    private final ContextTransformer transformContext;
    private final ApiKeyProvider apiKeyProvider;
    private final MessageSource steeringMessages;
    private final MessageSource followUpMessages;
    private final AssistantStreamFunction streamFunction;
    private final Double temperature;
    private final Integer maxTokens;
    private final String apiKey;
    private final Transport transport;
    private final CacheRetention cacheRetention;
    private final String sessionId;
    private final Map<String, String> headers;
    private final Long maxRetryDelayMs;
    private final ThinkingBudgets thinkingBudgets;

    private AgentLoopConfig(Builder builder) {
        this.model = Objects.requireNonNull(builder.model, "model");
        this.thinkingLevel = builder.thinkingLevel;
        this.convertToLlm = Objects.requireNonNull(builder.convertToLlm, "convertToLlm");
        this.transformContext = builder.transformContext;
        this.apiKeyProvider = builder.apiKeyProvider;
        this.steeringMessages = builder.steeringMessages;
        this.followUpMessages = builder.followUpMessages;
        this.streamFunction = Objects.requireNonNull(builder.streamFunction, "streamFunction");
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.apiKey = builder.apiKey;
        this.transport = builder.transport;
        this.cacheRetention = builder.cacheRetention;
        this.sessionId = builder.sessionId;
        this.headers = Map.copyOf(builder.headers);
        this.maxRetryDelayMs = builder.maxRetryDelayMs;
        this.thinkingBudgets = builder.thinkingBudgets;
    }

    public static Builder builder(Model model, AssistantStreamFunction streamFunction, MessageConverter convertToLlm) {
        return new Builder(model, streamFunction, convertToLlm);
    }

    public Model model() {
        return model;
    }

    public ThinkingLevel thinkingLevel() {
        return thinkingLevel;
    }

    public MessageConverter convertToLlm() {
        return convertToLlm;
    }

    public ContextTransformer transformContext() {
        return transformContext;
    }

    public ApiKeyProvider apiKeyProvider() {
        return apiKeyProvider;
    }

    public MessageSource steeringMessages() {
        return steeringMessages;
    }

    public MessageSource followUpMessages() {
        return followUpMessages;
    }

    public AssistantStreamFunction streamFunction() {
        return streamFunction;
    }

    public String apiKey() {
        return apiKey;
    }

    public SimpleStreamOptions toRequestOptions(String resolvedApiKey) {
        var builder = SimpleStreamOptions.builder();
        builder.temperature(temperature);
        builder.maxTokens(maxTokens);
        builder.apiKey(resolvedApiKey);
        builder.transport(transport);
        builder.cacheRetention(cacheRetention);
        builder.sessionId(sessionId);
        builder.headers(headers);
        builder.maxRetryDelayMs(maxRetryDelayMs);
        if (thinkingLevel != null) {
            builder.reasoning(thinkingLevel);
        }
        builder.thinkingBudgets(thinkingBudgets);
        return builder.build();
    }

    @FunctionalInterface
    public interface MessageConverter {
        CompletionStage<List<Message>> convert(List<AgentMessage> messages);
    }

    @FunctionalInterface
    public interface ContextTransformer {
        CompletionStage<List<AgentMessage>> transform(List<AgentMessage> messages);
    }

    @FunctionalInterface
    public interface ApiKeyProvider {
        CompletionStage<String> resolve(String provider);
    }

    @FunctionalInterface
    public interface MessageSource {
        CompletionStage<List<AgentMessage>> get();
    }

    @FunctionalInterface
    public interface AssistantStreamFunction {
        AssistantMessageEventStream stream(Model model, Context context, SimpleStreamOptions options);

        static AssistantStreamFunction fromClient(PiAiClient client) {
            Objects.requireNonNull(client, "client");
            return client::streamSimple;
        }
    }

    public static final class Builder {
        private final Model model;
        private final AssistantStreamFunction streamFunction;
        private MessageConverter convertToLlm;
        private ThinkingLevel thinkingLevel;
        private ContextTransformer transformContext;
        private ApiKeyProvider apiKeyProvider;
        private MessageSource steeringMessages;
        private MessageSource followUpMessages;
        private Double temperature;
        private Integer maxTokens;
        private String apiKey;
        private Transport transport;
        private CacheRetention cacheRetention;
        private String sessionId;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Long maxRetryDelayMs;
        private ThinkingBudgets thinkingBudgets;

        private Builder(Model model, AssistantStreamFunction streamFunction, MessageConverter convertToLlm) {
            this.model = Objects.requireNonNull(model, "model");
            this.streamFunction = Objects.requireNonNull(streamFunction, "streamFunction");
            this.convertToLlm = Objects.requireNonNull(convertToLlm, "convertToLlm");
        }

        public Builder thinkingLevel(ThinkingLevel thinkingLevel) {
            this.thinkingLevel = thinkingLevel;
            return this;
        }

        public Builder convertToLlm(MessageConverter convertToLlm) {
            this.convertToLlm = Objects.requireNonNull(convertToLlm, "convertToLlm");
            return this;
        }

        public Builder transformContext(ContextTransformer transformContext) {
            this.transformContext = transformContext;
            return this;
        }

        public Builder apiKeyProvider(ApiKeyProvider apiKeyProvider) {
            this.apiKeyProvider = apiKeyProvider;
            return this;
        }

        public Builder steeringMessages(MessageSource steeringMessages) {
            this.steeringMessages = steeringMessages;
            return this;
        }

        public Builder followUpMessages(MessageSource followUpMessages) {
            this.followUpMessages = followUpMessages;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
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
            this.headers.putAll(headers);
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

        public AgentLoopConfig build() {
            return new AgentLoopConfig(this);
        }
    }

    static <T> CompletableFuture<T> completedFuture(T value) {
        return CompletableFuture.completedFuture(value);
    }
}
