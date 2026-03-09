package dev.pi.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StreamOptions {
    private final Double temperature;
    private final Integer maxTokens;
    private final String apiKey;
    private final Transport transport;
    private final CacheRetention cacheRetention;
    private final String sessionId;
    private final Map<String, String> headers;
    private final Long maxRetryDelayMs;
    private final Map<String, Object> metadata;

    protected StreamOptions(Builder<?> builder) {
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.apiKey = builder.apiKey;
        this.transport = builder.transport;
        this.cacheRetention = builder.cacheRetention;
        this.sessionId = builder.sessionId;
        this.headers = Map.copyOf(builder.headers);
        this.maxRetryDelayMs = builder.maxRetryDelayMs;
        this.metadata = Map.copyOf(builder.metadata);
    }

    public static Builder<?> builder() {
        return new Builder<>();
    }

    public Double temperature() {
        return temperature;
    }

    public Integer maxTokens() {
        return maxTokens;
    }

    public String apiKey() {
        return apiKey;
    }

    public Transport transport() {
        return transport;
    }

    public CacheRetention cacheRetention() {
        return cacheRetention;
    }

    public String sessionId() {
        return sessionId;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public Long maxRetryDelayMs() {
        return maxRetryDelayMs;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public static class Builder<TSelf extends Builder<TSelf>> {
        private Double temperature;
        private Integer maxTokens;
        private String apiKey;
        private Transport transport;
        private CacheRetention cacheRetention;
        private String sessionId;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Long maxRetryDelayMs;
        private final Map<String, Object> metadata = new LinkedHashMap<>();

        protected TSelf self() {
            @SuppressWarnings("unchecked")
            var self = (TSelf) this;
            return self;
        }

        public TSelf temperature(Double temperature) {
            this.temperature = temperature;
            return self();
        }

        public TSelf maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return self();
        }

        public TSelf apiKey(String apiKey) {
            this.apiKey = apiKey;
            return self();
        }

        public TSelf transport(Transport transport) {
            this.transport = transport;
            return self();
        }

        public TSelf cacheRetention(CacheRetention cacheRetention) {
            this.cacheRetention = cacheRetention;
            return self();
        }

        public TSelf sessionId(String sessionId) {
            this.sessionId = sessionId;
            return self();
        }

        public TSelf headers(Map<String, String> headers) {
            this.headers.clear();
            this.headers.putAll(headers);
            return self();
        }

        public TSelf header(String key, String value) {
            this.headers.put(key, value);
            return self();
        }

        public TSelf maxRetryDelayMs(Long maxRetryDelayMs) {
            this.maxRetryDelayMs = maxRetryDelayMs;
            return self();
        }

        public TSelf metadata(Map<String, Object> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return self();
        }

        public TSelf metadata(String key, Object value) {
            this.metadata.put(key, value);
            return self();
        }

        public StreamOptions build() {
            return new StreamOptions(this);
        }
    }
}

