package dev.pi.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public interface OpenAiCompletionsTransport {
    void stream(OpenAiCompletionsRequest request, Consumer<JsonNode> onChunk) throws Exception;

    record OpenAiCompletionsRequest(
        URI uri,
        String apiKey,
        Map<String, String> headers,
        JsonNode body
    ) {
        public OpenAiCompletionsRequest {
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
