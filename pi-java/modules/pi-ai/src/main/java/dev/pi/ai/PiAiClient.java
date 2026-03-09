package dev.pi.ai;

import dev.pi.ai.auth.CredentialResolver;
import dev.pi.ai.model.Context;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.SimpleStreamOptions;
import dev.pi.ai.model.StreamOptions;
import dev.pi.ai.provider.ApiProvider;
import dev.pi.ai.registry.ApiProviderRegistry;
import dev.pi.ai.registry.ModelRegistry;
import dev.pi.ai.stream.AssistantMessageEventStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class PiAiClient {
    private final ApiProviderRegistry apiProviders;
    private final ModelRegistry modelRegistry;
    private final CredentialResolver credentialResolver;

    public PiAiClient(
        ApiProviderRegistry apiProviders,
        ModelRegistry modelRegistry,
        CredentialResolver credentialResolver
    ) {
        this.apiProviders = Objects.requireNonNull(apiProviders, "apiProviders");
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry");
        this.credentialResolver = Objects.requireNonNull(credentialResolver, "credentialResolver");
    }

    public static PiAiClient createDefault() {
        return new PiAiClient(new ApiProviderRegistry(), new ModelRegistry(), CredentialResolver.defaultResolver());
    }

    public ApiProviderRegistry apiProviders() {
        return apiProviders;
    }

    public ModelRegistry modelRegistry() {
        return modelRegistry;
    }

    public CredentialResolver credentialResolver() {
        return credentialResolver;
    }

    public Model getModel(String provider, String modelId) {
        return modelRegistry.require(provider, modelId);
    }

    public List<String> getProviders() {
        return modelRegistry.getProviders();
    }

    public List<Model> getModels(String provider) {
        return modelRegistry.getModels(provider);
    }

    public AssistantMessageEventStream stream(
        Model model,
        Context context,
        StreamOptions options
    ) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");

        var provider = resolveApiProvider(model.api());
        var resolvedOptions = credentialResolver.withResolvedCredential(model.provider(), options);
        return provider.stream(model, context, resolvedOptions);
    }

    public CompletableFuture<Message.AssistantMessage> complete(
        Model model,
        Context context,
        StreamOptions options
    ) {
        return stream(model, context, options).result();
    }

    public AssistantMessageEventStream streamSimple(
        Model model,
        Context context,
        SimpleStreamOptions options
    ) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");

        var provider = resolveApiProvider(model.api());
        var resolvedOptions = credentialResolver.withResolvedCredential(model.provider(), options);
        return provider.streamSimple(model, context, resolvedOptions);
    }

    public CompletableFuture<Message.AssistantMessage> completeSimple(
        Model model,
        Context context,
        SimpleStreamOptions options
    ) {
        return streamSimple(model, context, options).result();
    }

    private ApiProvider resolveApiProvider(String api) {
        return apiProviders.require(api);
    }
}
