package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.ai.model.Model;
import dev.pi.ai.model.Usage;
import dev.pi.ai.registry.ModelRegistry;
import dev.pi.session.AuthStorage;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class PiAuthCommandTest {
    @Test
    void loginStoresCredentialsAndAuthListShowsSavedProviders() {
        var stdout = new StringBuilder();
        var authStorage = AuthStorage.inMemory();
        var command = new PiAuthCommand(
            new StringReader(""),
            stdout,
            new StringBuilder(),
            authStorage,
            registryWithAnthropic()
        );

        command.runIfMatched("login", "anthropic", "sk-test-token").toCompletableFuture().join();
        command.runIfMatched("auth", "list").toCompletableFuture().join();

        assertThat(authStorage.getApiKey("anthropic")).isEqualTo("sk-test-token");
        assertThat(stdout.toString())
            .contains("Saved credentials for anthropic")
            .contains("Saved credentials:")
            .contains("anthropic (Anthropic)");
    }

    @Test
    void loginPromptsForProviderAndSecretWhenOmitted() {
        var stdout = new StringBuilder();
        var authStorage = AuthStorage.inMemory();
        var command = new PiAuthCommand(
            new StringReader("anthropic\nprompt-token\n"),
            stdout,
            new StringBuilder(),
            authStorage,
            registryWithAnthropic()
        );

        command.runIfMatched("login").toCompletableFuture().join();

        assertThat(authStorage.getApiKey("anthropic")).isEqualTo("prompt-token");
        assertThat(stdout.toString())
            .contains("Select provider to login:")
            .contains("Enter number or provider id:")
            .contains("Enter credentials for anthropic:");
    }

    @Test
    void logoutPromptsForSavedProviderWhenNeeded() {
        var stdout = new StringBuilder();
        var authStorage = AuthStorage.inMemory();
        authStorage.setApiKey("anthropic", "sk-test-token");
        authStorage.setApiKey("github", "ghp-test-token");
        var command = new PiAuthCommand(
            new StringReader("github\n"),
            stdout,
            new StringBuilder(),
            authStorage,
            registryWithAnthropic()
        );

        command.runIfMatched("logout").toCompletableFuture().join();

        assertThat(authStorage.has("github")).isFalse();
        assertThat(authStorage.has("anthropic")).isTrue();
        assertThat(stdout.toString())
            .contains("Select provider to logout:")
            .contains("Removed credentials for github");
    }

    private static ModelRegistry registryWithAnthropic() {
        var registry = new ModelRegistry();
        registry.register(new Model(
            "claude-sonnet",
            "Claude Sonnet",
            "anthropic-messages",
            "anthropic",
            "https://example.com",
            true,
            List.of("text"),
            new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
            200_000,
            8_192,
            null,
            null
        ));
        return registry;
    }
}
