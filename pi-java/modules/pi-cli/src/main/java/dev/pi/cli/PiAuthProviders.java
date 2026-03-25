package dev.pi.cli;

import dev.pi.session.AuthStorage;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

final class PiAuthProviders {
    private static final List<String> PACKAGE_HOST_PROVIDERS = List.of("github", "gitlab");

    private PiAuthProviders() {
    }

    static List<String> packageHostProviders() {
        return PACKAGE_HOST_PROVIDERS;
    }

    static List<PiInteractiveSession.AuthProvider> mergeProviders(Iterable<String> knownProviders, AuthStorage authStorage) {
        Objects.requireNonNull(authStorage, "authStorage");
        var providers = new LinkedHashMap<String, PiInteractiveSession.AuthProvider>();
        for (var provider : PACKAGE_HOST_PROVIDERS) {
            addProvider(providers, provider, authStorage);
        }
        if (knownProviders != null) {
            for (var provider : knownProviders) {
                addProvider(providers, provider, authStorage);
            }
        }
        for (var provider : authStorage.providers()) {
            addProvider(providers, provider, authStorage);
        }
        return List.copyOf(providers.values());
    }

    static List<String> mergeProviderIds(Iterable<String> knownProviders, AuthStorage authStorage) {
        Objects.requireNonNull(authStorage, "authStorage");
        var providerIds = new LinkedHashSet<String>();
        providerIds.addAll(PACKAGE_HOST_PROVIDERS);
        if (knownProviders != null) {
            for (var provider : knownProviders) {
                if (provider != null && !provider.isBlank()) {
                    providerIds.add(provider.trim());
                }
            }
        }
        providerIds.addAll(authStorage.providers());
        return List.copyOf(providerIds);
    }

    static String displayName(String provider) {
        return switch (provider) {
            case "anthropic" -> "Anthropic";
            case "openai" -> "OpenAI";
            case "google" -> "Google";
            case "bedrock" -> "AWS Bedrock";
            case "github" -> "GitHub";
            case "gitlab" -> "GitLab";
            default -> provider;
        };
    }

    private static void addProvider(
        LinkedHashMap<String, PiInteractiveSession.AuthProvider> providers,
        String provider,
        AuthStorage authStorage
    ) {
        if (provider == null || provider.isBlank()) {
            return;
        }
        var normalized = provider.trim();
        providers.putIfAbsent(
            normalized,
            new PiInteractiveSession.AuthProvider(normalized, displayName(normalized), authStorage.has(normalized))
        );
    }
}
