package dev.pi.ai.auth;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class EnvironmentCredentialSource implements CredentialSource {
    private static final Map<String, String> API_KEY_ENV_VARS = Map.ofEntries(
        Map.entry("openai", "OPENAI_API_KEY"),
        Map.entry("azure-openai-responses", "AZURE_OPENAI_API_KEY"),
        Map.entry("google", "GEMINI_API_KEY"),
        Map.entry("groq", "GROQ_API_KEY"),
        Map.entry("cerebras", "CEREBRAS_API_KEY"),
        Map.entry("xai", "XAI_API_KEY"),
        Map.entry("openrouter", "OPENROUTER_API_KEY"),
        Map.entry("vercel-ai-gateway", "AI_GATEWAY_API_KEY"),
        Map.entry("zai", "ZAI_API_KEY"),
        Map.entry("mistral", "MISTRAL_API_KEY"),
        Map.entry("minimax", "MINIMAX_API_KEY"),
        Map.entry("minimax-cn", "MINIMAX_CN_API_KEY"),
        Map.entry("huggingface", "HF_TOKEN"),
        Map.entry("opencode", "OPENCODE_API_KEY"),
        Map.entry("kimi-coding", "KIMI_API_KEY")
    );

    private final Map<String, String> environment;

    public EnvironmentCredentialSource() {
        this(System.getenv());
    }

    public EnvironmentCredentialSource(Map<String, String> environment) {
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
    }

    @Override
    public Optional<Credential> resolve(String provider) {
        var normalizedProvider = normalizeProvider(provider);

        return switch (normalizedProvider) {
            case "github-copilot" -> firstPresent("COPILOT_GITHUB_TOKEN", "GH_TOKEN", "GITHUB_TOKEN")
                .map(token -> (Credential) new BearerTokenCredential(token));
            case "anthropic" -> firstPresent("ANTHROPIC_OAUTH_TOKEN")
                .map(token -> (Credential) new BearerTokenCredential(token))
                .or(() -> firstPresent("ANTHROPIC_API_KEY").map(token -> (Credential) new ApiKeyCredential(token)));
            default -> {
                var envVar = API_KEY_ENV_VARS.get(normalizedProvider);
                if (envVar == null) {
                    yield Optional.empty();
                }
                yield firstPresent(envVar).map(token -> (Credential) new ApiKeyCredential(token));
            }
        };
    }

    private Optional<String> firstPresent(String... names) {
        for (var name : names) {
            var value = environment.get(name);
            if (value != null && !value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static String normalizeProvider(String provider) {
        Objects.requireNonNull(provider, "provider");
        if (provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        return provider;
    }
}
