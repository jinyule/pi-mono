package dev.pi.ai.provider.google;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public interface GoogleGenerativeAiTransport {
    void stream(GoogleGenerativeAiRequest request, Consumer<JsonNode> onEvent) throws Exception;

    record GoogleGenerativeAiRequest(
        URI uri,
        String apiKey,
        Map<String, String> headers,
        JsonNode body
    ) {
        public GoogleGenerativeAiRequest {
            Objects.requireNonNull(uri, "uri");
            Objects.requireNonNull(apiKey, "apiKey");
            if (apiKey.isBlank()) {
                throw new IllegalArgumentException("apiKey must not be blank");
            }
            headers = headers == null ? Map.of() : Map.copyOf(headers);
            Objects.requireNonNull(body, "body");
        }
    }
}
