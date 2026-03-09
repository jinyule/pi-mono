package dev.pi.ai.auth;

import dev.pi.ai.model.SimpleStreamOptions;
import dev.pi.ai.model.StreamOptions;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CredentialResolver {
    private final List<CredentialSource> sources;

    public CredentialResolver(List<CredentialSource> sources) {
        this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
    }

    public CredentialResolver(CredentialSource... sources) {
        this(List.of(Objects.requireNonNull(sources, "sources")));
    }

    public static CredentialResolver defaultResolver() {
        return new CredentialResolver(new EnvironmentCredentialSource());
    }

    public Optional<Credential> resolve(String provider) {
        Objects.requireNonNull(provider, "provider");
        for (var source : sources) {
            var credential = source.resolve(provider);
            if (credential.isPresent()) {
                return credential;
            }
        }
        return Optional.empty();
    }

    public StreamOptions withResolvedCredential(String provider, StreamOptions options) {
        if (hasExplicitCredential(options == null ? null : options.apiKey())) {
            return options;
        }

        var credential = resolve(provider).orElse(null);
        if (credential == null) {
            return options == null ? StreamOptions.builder().build() : options;
        }

        var builder = StreamOptions.builder();
        copyCommonOptions(options, builder);
        builder.apiKey(credential.secret());
        return builder.build();
    }

    public SimpleStreamOptions withResolvedCredential(String provider, SimpleStreamOptions options) {
        if (hasExplicitCredential(options == null ? null : options.apiKey())) {
            return options;
        }

        var credential = resolve(provider).orElse(null);
        if (credential == null && options != null) {
            return options;
        }

        var builder = SimpleStreamOptions.builder();
        copyCommonOptions(options, builder);
        if (options != null) {
            builder.reasoning(options.reasoning());
            builder.thinkingBudgets(options.thinkingBudgets());
        }
        if (credential != null) {
            builder.apiKey(credential.secret());
        }
        return builder.build();
    }

    private static void copyCommonOptions(StreamOptions options, StreamOptions.Builder<?> builder) {
        if (options == null) {
            return;
        }
        builder.temperature(options.temperature());
        builder.maxTokens(options.maxTokens());
        if (options.apiKey() != null) {
            builder.apiKey(options.apiKey());
        }
        if (options.transport() != null) {
            builder.transport(options.transport());
        }
        if (options.cacheRetention() != null) {
            builder.cacheRetention(options.cacheRetention());
        }
        if (options.sessionId() != null) {
            builder.sessionId(options.sessionId());
        }
        builder.headers(options.headers());
        if (options.maxRetryDelayMs() != null) {
            builder.maxRetryDelayMs(options.maxRetryDelayMs());
        }
        builder.metadata(options.metadata());
    }

    private static boolean hasExplicitCredential(String apiKey) {
        return apiKey != null && !apiKey.isBlank();
    }
}
