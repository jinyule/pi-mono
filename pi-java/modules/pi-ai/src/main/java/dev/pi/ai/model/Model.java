package dev.pi.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Model(
    String id,
    String name,
    String api,
    String provider,
    String baseUrl,
    boolean reasoning,
    List<String> input,
    Usage.Cost cost,
    int contextWindow,
    int maxTokens,
    Map<String, String> headers,
    JsonNode compat
) {
    public Model {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(baseUrl, "baseUrl");
        input = List.copyOf(Objects.requireNonNull(input, "input"));
        cost = Objects.requireNonNull(cost, "cost");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        compat = compat == null ? JsonNodeFactory.instance.nullNode() : compat.deepCopy();
    }
}

