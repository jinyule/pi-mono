package dev.pi.ai.provider.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public interface AnthropicMessagesTransport {
    void stream(AnthropicMessagesRequest request, Consumer<JsonNode> onEvent) throws Exception;

    record AnthropicMessagesRequest(
        URI uri,
        Map<String, String> headers,
        JsonNode body
    ) {
        public AnthropicMessagesRequest {
            Objects.requireNonNull(uri, "uri");
            headers = headers == null ? Map.of() : Map.copyOf(headers);
            Objects.requireNonNull(body, "body");
        }
    }
}
