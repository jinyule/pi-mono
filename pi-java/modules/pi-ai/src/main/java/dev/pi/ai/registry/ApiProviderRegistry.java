package dev.pi.ai.registry;

import dev.pi.ai.provider.ApiProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ApiProviderRegistry {
    private final Map<String, Registration> providers = new LinkedHashMap<>();

    public void register(ApiProvider provider) {
        register(provider, null);
    }

    public void register(ApiProvider provider, String sourceId) {
        var normalizedApi = normalizeApi(Objects.requireNonNull(provider, "provider").api());
        providers.put(normalizedApi, new Registration(provider, sourceId));
    }

    public Optional<ApiProvider> get(String api) {
        return Optional.ofNullable(providers.get(normalizeApi(api))).map(Registration::provider);
    }

    public ApiProvider require(String api) {
        return get(api).orElseThrow(() -> new IllegalArgumentException("No API provider registered for api: " + api));
    }

    public List<ApiProvider> getProviders() {
        var snapshot = new ArrayList<ApiProvider>(providers.size());
        for (var registration : providers.values()) {
            snapshot.add(registration.provider());
        }
        return List.copyOf(snapshot);
    }

    public void unregisterSource(String sourceId) {
        Objects.requireNonNull(sourceId, "sourceId");
        providers.entrySet().removeIf(entry -> sourceId.equals(entry.getValue().sourceId()));
    }

    public void clear() {
        providers.clear();
    }

    private static String normalizeApi(String api) {
        Objects.requireNonNull(api, "api");
        if (api.isBlank()) {
            throw new IllegalArgumentException("api must not be blank");
        }
        return api;
    }

    private record Registration(ApiProvider provider, String sourceId) {}
}
