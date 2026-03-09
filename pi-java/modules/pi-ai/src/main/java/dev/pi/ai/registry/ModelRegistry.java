package dev.pi.ai.registry;

import dev.pi.ai.model.Model;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ModelRegistry {
    private final Map<String, LinkedHashMap<String, Model>> modelsByProvider = new LinkedHashMap<>();

    public void register(Model model) {
        Objects.requireNonNull(model, "model");
        modelsByProvider
            .computeIfAbsent(normalizeProvider(model.provider()), ignored -> new LinkedHashMap<>())
            .put(model.id(), model);
    }

    public void registerAll(Iterable<Model> models) {
        Objects.requireNonNull(models, "models");
        for (var model : models) {
            register(model);
        }
    }

    public Optional<Model> getModel(String provider, String modelId) {
        Objects.requireNonNull(modelId, "modelId");
        var providerModels = modelsByProvider.get(normalizeProvider(provider));
        if (providerModels == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providerModels.get(modelId));
    }

    public Model require(String provider, String modelId) {
        return getModel(provider, modelId)
            .orElseThrow(() -> new IllegalArgumentException("No model registered for provider=%s id=%s".formatted(provider, modelId)));
    }

    public List<Model> getModels(String provider) {
        var providerModels = modelsByProvider.get(normalizeProvider(provider));
        if (providerModels == null) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(providerModels.values()));
    }

    public List<String> getProviders() {
        return List.copyOf(new ArrayList<>(modelsByProvider.keySet()));
    }

    public void clear() {
        modelsByProvider.clear();
    }

    private static String normalizeProvider(String provider) {
        Objects.requireNonNull(provider, "provider");
        if (provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        return provider;
    }
}
