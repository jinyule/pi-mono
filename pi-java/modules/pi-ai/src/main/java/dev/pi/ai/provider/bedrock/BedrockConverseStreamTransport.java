package dev.pi.ai.provider.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Objects;
import java.util.function.Consumer;

public interface BedrockConverseStreamTransport {
    void stream(BedrockConverseStreamRequest request, Consumer<JsonNode> onEvent) throws Exception;

    record BedrockConverseStreamRequest(
        String modelId,
        String region,
        String profile,
        URI endpointOverride,
        JsonNode body
    ) {
        public BedrockConverseStreamRequest {
            Objects.requireNonNull(modelId, "modelId");
            Objects.requireNonNull(region, "region");
            profile = profile == null || profile.isBlank() ? null : profile;
            Objects.requireNonNull(body, "body");
        }
    }
}
