package dev.pi.ai.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.ai.model.SimpleStreamOptions;
import dev.pi.ai.model.StreamOptions;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CredentialResolverTest {
    @Test
    void resolvesCredentialsInSourceOrder() {
        var resolver = new CredentialResolver(
            provider -> Optional.empty(),
            provider -> Optional.of(new ApiKeyCredential("second-source-key")),
            provider -> Optional.of(new ApiKeyCredential("third-source-key"))
        );

        assertThat(resolver.resolve("openai"))
            .containsInstanceOf(ApiKeyCredential.class)
            .get()
            .extracting(Credential::secret)
            .isEqualTo("second-source-key");
    }

    @Test
    void mapsEnvironmentVariablesToProviderCredentials() {
        var source = new EnvironmentCredentialSource(Map.of(
            "OPENAI_API_KEY", "openai-key",
            "ANTHROPIC_API_KEY", "anthropic-api-key",
            "ANTHROPIC_OAUTH_TOKEN", "anthropic-oauth-token",
            "GH_TOKEN", "github-token"
        ));

        assertThat(source.resolve("openai"))
            .containsInstanceOf(ApiKeyCredential.class)
            .get()
            .extracting(Credential::secret)
            .isEqualTo("openai-key");

        assertThat(source.resolve("anthropic"))
            .containsInstanceOf(BearerTokenCredential.class)
            .get()
            .extracting(Credential::secret)
            .isEqualTo("anthropic-oauth-token");

        assertThat(source.resolve("github-copilot"))
            .containsInstanceOf(BearerTokenCredential.class)
            .get()
            .extracting(Credential::secret)
            .isEqualTo("github-token");
    }

    @Test
    void injectsResolvedCredentialsOnlyWhenOptionsDoNotProvideOne() {
        var resolver = new CredentialResolver(provider -> Optional.of(new ApiKeyCredential("resolved-key")));

        var resolvedStreamOptions = resolver.withResolvedCredential("openai", StreamOptions.builder().build());
        var explicitStreamOptions = resolver.withResolvedCredential("openai", StreamOptions.builder().apiKey("explicit-key").build());
        var resolvedSimpleOptions = resolver.withResolvedCredential("openai", SimpleStreamOptions.builder().build());
        var explicitSimpleOptions = resolver.withResolvedCredential(
            "openai",
            SimpleStreamOptions.builder().apiKey("explicit-simple-key").build()
        );

        assertThat(resolvedStreamOptions.apiKey()).isEqualTo("resolved-key");
        assertThat(explicitStreamOptions.apiKey()).isEqualTo("explicit-key");
        assertThat(resolvedSimpleOptions.apiKey()).isEqualTo("resolved-key");
        assertThat(explicitSimpleOptions.apiKey()).isEqualTo("explicit-simple-key");
    }
}
