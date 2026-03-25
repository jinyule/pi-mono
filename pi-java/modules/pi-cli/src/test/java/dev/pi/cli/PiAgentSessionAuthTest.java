package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.pi.agent.runtime.AgentLoopConfig;
import dev.pi.ai.model.AssistantMessageEvent;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.AssistantMessageEventStream;
import dev.pi.session.AuthStorage;
import dev.pi.session.InstructionResources;
import dev.pi.session.SessionManager;
import dev.pi.session.SettingsManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiAgentSessionAuthTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void loginPersistsCredentialsAndUsesThemForPrompts() {
        var observedApiKey = new AtomicReference<String>();
        var authStorage = AuthStorage.inMemory();
        var session = PiAgentSession.builder(
            testModel(),
            SessionManager.inMemory(tempDir.toString()),
            SettingsManager.inMemory(),
            InstructionResources.empty()
        )
            .authStorage(authStorage)
            .streamFunction((model, context, options) -> {
                observedApiKey.set(options.apiKey());
                return fakeAssistant("Ack").stream(model, context, options);
            })
            .modelSelectorModels(List.of(testModel()))
            .build();

        session.login("anthropic", "oauth-access-token");
        session.prompt("hello").toCompletableFuture().join();

        assertThat(observedApiKey.get()).isEqualTo("oauth-access-token");
        assertThat(authStorage.getApiKey("anthropic")).isEqualTo("oauth-access-token");
        assertThat(session.authSelection().loggedInProviders())
            .extracting(PiInteractiveSession.AuthProvider::providerId)
            .containsExactly("anthropic");
    }

    @Test
    void logoutRemovesSavedCredentials() {
        var authStorage = AuthStorage.inMemory();
        authStorage.setApiKey("anthropic", "oauth-access-token");
        var session = PiAgentSession.builder(
            testModel(),
            SessionManager.inMemory(tempDir.toString()),
            SettingsManager.inMemory(),
            InstructionResources.empty()
        )
            .authStorage(authStorage)
            .streamFunction(fakeAssistant("Ack"))
            .modelSelectorModels(List.of(testModel()))
            .build();

        session.logout("anthropic");

        assertThat(authStorage.has("anthropic")).isFalse();
        assertThat(session.authSelection().loggedInProviders()).isEmpty();
        assertThatThrownBy(() -> session.logout("anthropic"))
            .hasMessage("No saved credentials for anthropic");
    }

    @Test
    void authSelectionIncludesPackageProviders() {
        var session = PiAgentSession.builder(
            testModel(),
            SessionManager.inMemory(tempDir.toString()),
            SettingsManager.inMemory(),
            InstructionResources.empty()
        )
            .authStorage(AuthStorage.inMemory())
            .streamFunction(fakeAssistant("Ack"))
            .modelSelectorModels(List.of(testModel()))
            .build();

        assertThat(session.authSelection().allProviders())
            .extracting(PiInteractiveSession.AuthProvider::providerId)
            .contains("anthropic", "github", "gitlab");
    }

    private static Model testModel() {
        return new Model(
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
        );
    }

    private static AgentLoopConfig.AssistantStreamFunction fakeAssistant(String text) {
        return (model, context, options) -> {
            var stream = new AssistantMessageEventStream();
            Thread.ofVirtual().start(() -> stream.push(new AssistantMessageEvent.Done(
                StopReason.STOP,
                new Message.AssistantMessage(
                    List.of(new TextContent(text, null)),
                    model.api(),
                    model.provider(),
                    model.id(),
                    new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                    StopReason.STOP,
                    null,
                    200L
                )
            )));
            return stream;
        };
    }
}
