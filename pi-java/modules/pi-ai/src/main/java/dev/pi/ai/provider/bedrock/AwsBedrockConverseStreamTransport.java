package dev.pi.ai.provider.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ImageFormat;
import software.amazon.awssdk.services.bedrockruntime.model.ImageSource;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningTextBlock;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultStatus;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

public final class AwsBedrockConverseStreamTransport implements BedrockConverseStreamTransport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void stream(BedrockConverseStreamRequest request, Consumer<JsonNode> onEvent) throws Exception {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(onEvent, "onEvent");

        var builder = BedrockRuntimeAsyncClient.builder()
            .region(Region.of(request.region()))
            .credentialsProvider(resolveCredentialsProvider(request.profile()))
            .httpClientBuilder(NettyNioAsyncHttpClient.builder());
        if (request.endpointOverride() != null) {
            builder.endpointOverride(request.endpointOverride());
        }

        var completion = new CompletableFuture<Void>();
        try (var client = builder.build()) {
            var responseHandler = ConverseStreamResponseHandler.builder()
                .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                    .onMessageStart(event -> onEvent.accept(toMessageStartEvent(event)))
                    .onContentBlockStart(event -> onEvent.accept(toContentBlockStartEvent(event)))
                    .onContentBlockDelta(event -> onEvent.accept(toContentBlockDeltaEvent(event)))
                    .onContentBlockStop(event -> onEvent.accept(toContentBlockStopEvent(event)))
                    .onMessageStop(event -> onEvent.accept(toMessageStopEvent(event)))
                    .onMetadata(event -> onEvent.accept(toMetadataEvent(event)))
                    .onDefault(ignored -> {
                    })
                    .build())
                .onComplete(() -> completion.complete(null))
                .onError(throwable -> completion.completeExceptionally(unwrap(throwable)))
                .build();

            client.converseStream(toSdkRequest(request), responseHandler)
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        completion.completeExceptionally(unwrap(throwable));
                    }
                });

            completion.join();
        } catch (CompletionException exception) {
            throw rethrow(unwrap(exception));
        }
    }

    private static ConverseStreamRequest toSdkRequest(BedrockConverseStreamRequest request) {
        var body = request.body();
        var builder = ConverseStreamRequest.builder()
            .modelId(request.modelId());

        var messages = body.path("messages");
        if (messages.isArray() && !messages.isEmpty()) {
            builder.messages(toMessages(messages));
        }

        var system = body.path("system");
        if (system.isArray() && !system.isEmpty()) {
            builder.system(toSystemBlocks(system));
        }

        var inferenceConfig = body.path("inferenceConfig");
        if (inferenceConfig.isObject() && !inferenceConfig.isEmpty()) {
            builder.inferenceConfig(toInferenceConfig(inferenceConfig));
        }

        var toolConfig = body.path("toolConfig");
        if (toolConfig.isObject() && !toolConfig.isEmpty()) {
            builder.toolConfig(toToolConfiguration(toolConfig));
        }

        var additionalModelRequestFields = body.path("additionalModelRequestFields");
        if (additionalModelRequestFields.isObject() && !additionalModelRequestFields.isEmpty()) {
            builder.additionalModelRequestFields(toDocument(additionalModelRequestFields));
        }

        return builder.build();
    }

    private static List<Message> toMessages(JsonNode messagesNode) {
        var messages = new ArrayList<Message>(messagesNode.size());
        for (JsonNode messageNode : messagesNode) {
            messages.add(Message.builder()
                .role("assistant".equals(messageNode.path("role").asText()) ? ConversationRole.ASSISTANT : ConversationRole.USER)
                .content(toContentBlocks(messageNode.path("content")))
                .build());
        }
        return List.copyOf(messages);
    }

    private static List<SystemContentBlock> toSystemBlocks(JsonNode systemNode) {
        var blocks = new ArrayList<SystemContentBlock>(systemNode.size());
        for (JsonNode blockNode : systemNode) {
            if (blockNode.has("text")) {
                blocks.add(SystemContentBlock.fromText(blockNode.path("text").asText()));
            }
        }
        return List.copyOf(blocks);
    }

    private static List<ContentBlock> toContentBlocks(JsonNode contentNode) {
        var blocks = new ArrayList<ContentBlock>(contentNode.size());
        for (JsonNode blockNode : contentNode) {
            if (blockNode.has("text")) {
                blocks.add(ContentBlock.fromText(blockNode.path("text").asText()));
            } else if (blockNode.has("image")) {
                blocks.add(ContentBlock.fromImage(toImageBlock(blockNode.path("image"))));
            } else if (blockNode.has("toolUse")) {
                blocks.add(ContentBlock.fromToolUse(toToolUseBlock(blockNode.path("toolUse"))));
            } else if (blockNode.has("toolResult")) {
                blocks.add(ContentBlock.fromToolResult(toToolResultBlock(blockNode.path("toolResult"))));
            } else if (blockNode.has("reasoningContent")) {
                blocks.add(ContentBlock.fromReasoningContent(toReasoningContentBlock(blockNode.path("reasoningContent"))));
            }
        }
        return List.copyOf(blocks);
    }

    private static InferenceConfiguration toInferenceConfig(JsonNode node) {
        var builder = InferenceConfiguration.builder();
        if (node.path("maxTokens").isInt()) {
            builder.maxTokens(node.path("maxTokens").asInt());
        }
        if (node.path("temperature").isNumber()) {
            builder.temperature((float) node.path("temperature").asDouble());
        }
        return builder.build();
    }

    private static ToolConfiguration toToolConfiguration(JsonNode node) {
        var tools = new ArrayList<Tool>();
        for (JsonNode toolNode : node.path("tools")) {
            var toolSpecNode = toolNode.path("toolSpec");
            tools.add(Tool.builder()
                .toolSpec(ToolSpecification.builder()
                    .name(toolSpecNode.path("name").asText())
                    .description(toolSpecNode.path("description").asText(""))
                    .inputSchema(ToolInputSchema.builder().json(toDocument(toolSpecNode.path("inputSchema").path("json"))).build())
                    .build())
                .build());
        }
        return ToolConfiguration.builder().tools(tools).build();
    }

    private static ToolUseBlock toToolUseBlock(JsonNode node) {
        return ToolUseBlock.builder()
            .toolUseId(node.path("toolUseId").asText())
            .name(node.path("name").asText())
            .input(toDocument(node.path("input")))
            .build();
    }

    private static ToolResultBlock toToolResultBlock(JsonNode node) {
        var content = new ArrayList<ToolResultContentBlock>();
        for (JsonNode blockNode : node.path("content")) {
            var builder = ToolResultContentBlock.builder();
            if (blockNode.has("text")) {
                builder.text(blockNode.path("text").asText());
            } else if (blockNode.has("image")) {
                builder.image(toImageBlock(blockNode.path("image")));
            }
            content.add(builder.build());
        }

        var builder = ToolResultBlock.builder()
            .toolUseId(node.path("toolUseId").asText())
            .content(content);
        if (node.path("status").isTextual()) {
            builder.status(ToolResultStatus.fromValue(node.path("status").asText()));
        }
        return builder.build();
    }

    private static ReasoningContentBlock toReasoningContentBlock(JsonNode node) {
        var builder = ReasoningContentBlock.builder();
        var reasoningText = node.path("reasoningText");
        if (reasoningText.isObject()) {
            var reasoningTextBuilder = ReasoningTextBlock.builder()
                .text(reasoningText.path("text").asText(""));
            if (reasoningText.path("signature").isTextual()) {
                reasoningTextBuilder.signature(reasoningText.path("signature").asText());
            }
            builder.reasoningText(reasoningTextBuilder.build());
        }
        var redactedContent = node.path("redactedContent");
        if (redactedContent.isObject()) {
            builder.redactedContent(SdkBytes.fromByteArray(Base64.getDecoder().decode(redactedContent.path("data").asText())));
        }
        return builder.build();
    }

    private static ImageBlock toImageBlock(JsonNode node) {
        return ImageBlock.builder()
            .format(ImageFormat.fromValue(node.path("format").asText()))
            .source(ImageSource.builder()
                .bytes(SdkBytes.fromByteArray(Base64.getDecoder().decode(node.path("source").path("bytes").asText())))
                .build())
            .build();
    }

    private static JsonNode toMessageStartEvent(
        software.amazon.awssdk.services.bedrockruntime.model.MessageStartEvent event
    ) {
        var node = OBJECT_MAPPER.createObjectNode().put("type", "messageStart");
        if (event.role() != null) {
            node.put("role", event.role().toString());
        }
        return node;
    }

    private static JsonNode toContentBlockStartEvent(
        software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent event
    ) {
        var node = OBJECT_MAPPER.createObjectNode()
            .put("type", "contentBlockStart")
            .put("contentBlockIndex", event.contentBlockIndex());
        var start = node.putObject("start");
        if (event.start() != null && event.start().toolUse() != null) {
            var toolUse = start.putObject("toolUse");
            toolUse.put("toolUseId", event.start().toolUse().toolUseId());
            toolUse.put("name", event.start().toolUse().name());
        }
        return node;
    }

    private static JsonNode toContentBlockDeltaEvent(
        software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent event
    ) {
        var node = OBJECT_MAPPER.createObjectNode()
            .put("type", "contentBlockDelta")
            .put("contentBlockIndex", event.contentBlockIndex());
        var deltaNode = node.putObject("delta");
        if (event.delta() != null) {
            if (event.delta().text() != null) {
                deltaNode.put("text", event.delta().text());
            }
            if (event.delta().toolUse() != null && event.delta().toolUse().input() != null) {
                deltaNode.putObject("toolUse").put("input", event.delta().toolUse().input());
            }
            if (event.delta().reasoningContent() != null) {
                var reasoningContent = deltaNode.putObject("reasoningContent");
                if (event.delta().reasoningContent().text() != null) {
                    reasoningContent.put("text", event.delta().reasoningContent().text());
                }
                if (event.delta().reasoningContent().signature() != null) {
                    reasoningContent.put("signature", event.delta().reasoningContent().signature());
                }
            }
        }
        return node;
    }

    private static JsonNode toContentBlockStopEvent(
        software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStopEvent event
    ) {
        return OBJECT_MAPPER.createObjectNode()
            .put("type", "contentBlockStop")
            .put("contentBlockIndex", event.contentBlockIndex());
    }

    private static JsonNode toMessageStopEvent(
        software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent event
    ) {
        var node = OBJECT_MAPPER.createObjectNode().put("type", "messageStop");
        if (event.stopReason() != null) {
            node.put("stopReason", event.stopReason().toString());
        }
        return node;
    }

    private static JsonNode toMetadataEvent(ConverseStreamMetadataEvent event) {
        var node = OBJECT_MAPPER.createObjectNode().put("type", "metadata");
        if (event.usage() != null) {
            var usage = node.putObject("usage");
            usage.put("inputTokens", event.usage().inputTokens());
            usage.put("outputTokens", event.usage().outputTokens());
            usage.put("totalTokens", event.usage().totalTokens());
            usage.put("cacheReadInputTokens", 0);
            usage.put("cacheWriteInputTokens", 0);
        }
        return node;
    }

    private static Document toDocument(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Document.fromNull();
        }
        if (node.isTextual()) {
            return Document.fromString(node.asText());
        }
        if (node.isBoolean()) {
            return Document.fromBoolean(node.asBoolean());
        }
        if (node.isNumber()) {
            if (node.isInt()) {
                return Document.fromNumber(node.intValue());
            }
            if (node.isLong()) {
                return Document.fromNumber(node.longValue());
            }
            if (node.isFloat()) {
                return Document.fromNumber(node.floatValue());
            }
            if (node.isDouble()) {
                return Document.fromNumber(node.doubleValue());
            }
            if (node.isBigInteger()) {
                return Document.fromNumber(node.bigIntegerValue());
            }
            return Document.fromNumber(node.decimalValue());
        }
        if (node.isArray()) {
            var values = new ArrayList<Document>(node.size());
            for (JsonNode child : node) {
                values.add(toDocument(child));
            }
            return Document.fromList(values);
        }
        if (node.isObject()) {
            var values = new LinkedHashMap<String, Document>();
            node.fields().forEachRemaining(entry -> values.put(entry.getKey(), toDocument(entry.getValue())));
            return Document.fromMap(values);
        }
        return Document.fromString(node.asText());
    }

    private static AwsCredentialsProvider resolveCredentialsProvider(String profile) {
        if (profile == null || profile.isBlank()) {
            return DefaultCredentialsProvider.create();
        }
        return ProfileCredentialsProvider.builder()
            .profileName(profile)
            .build();
    }

    private static Throwable unwrap(Throwable throwable) {
        var current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static Exception rethrow(Throwable throwable) {
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new IllegalStateException(throwable);
    }
}
