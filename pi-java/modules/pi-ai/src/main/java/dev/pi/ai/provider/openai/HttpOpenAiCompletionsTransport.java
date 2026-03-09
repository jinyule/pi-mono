package dev.pi.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pi.ai.stream.SseEventParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class HttpOpenAiCompletionsTransport implements OpenAiCompletionsTransport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;

    public HttpOpenAiCompletionsTransport() {
        this(HttpClient.newHttpClient());
    }

    public HttpOpenAiCompletionsTransport(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public void stream(OpenAiCompletionsRequest request, Consumer<JsonNode> onChunk) throws Exception {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(onChunk, "onChunk");

        var builder = HttpRequest.newBuilder(request.uri())
            .header("Authorization", "Bearer " + request.apiKey())
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream");

        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }

        var body = OBJECT_MAPPER.writeValueAsString(request.body());
        var httpRequest = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                var errorBody = reader.lines().reduce("", (left, right) -> left + right);
                throw new IllegalStateException(
                    "OpenAI Completions request failed with status " + response.statusCode() +
                    (errorBody.isBlank() ? "" : ": " + errorBody)
                );
            }
        }

        var parser = new SseEventParser();
        try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            var chunk = new char[8192];
            int read;
            while ((read = reader.read(chunk)) >= 0) {
                if (read == 0) {
                    continue;
                }
                var text = new String(chunk, 0, read);
                for (var event : parser.append(text)) {
                    consumeEvent(event.data(), onChunk);
                }
            }
        }

        for (var event : parser.finish()) {
            consumeEvent(event.data(), onChunk);
        }
    }

    private static void consumeEvent(String data, Consumer<JsonNode> onChunk) throws Exception {
        if (data == null || data.isBlank() || "[DONE]".equals(data)) {
            return;
        }
        onChunk.accept(OBJECT_MAPPER.readTree(data));
    }
}
