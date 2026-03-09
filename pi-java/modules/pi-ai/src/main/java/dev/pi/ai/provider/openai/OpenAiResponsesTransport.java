package dev.pi.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public interface OpenAiResponsesTransport {
    void stream(OpenAiResponsesRequest request, Consumer<JsonNode> onEvent) throws Exception;

    record OpenAiResponsesRequest(
        URI uri,
        String apiKey,
        Map<String, String> headers,
        JsonNode body
    ) {
        public OpenAiResponsesRequest {
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
