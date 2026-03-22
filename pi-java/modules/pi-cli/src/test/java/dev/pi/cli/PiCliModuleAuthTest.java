package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.ai.PiAiClient;
import dev.pi.ai.auth.CredentialResolver;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.Usage;
import dev.pi.ai.registry.ApiProviderRegistry;
import dev.pi.ai.registry.ModelRegistry;
import dev.pi.session.AuthStorage;
import dev.pi.tui.Terminal;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiCliModuleAuthTest {
    @TempDir
    Path tempDir;

    @Test
    void createDefaultSessionLoadsSavedCredentialsFromAuthStorage() throws Exception {
        var authStorage = AuthStorage.create(tempDir.resolve("auth.json"));
        authStorage.setApiKey("anthropic", "stored-auth-token");

        var module = new PiCliModule(
            tempDir,
            new StringReader(""),
            new StringBuilder(),
            new StringBuilder(),
            new PiCliParser(),
            testClient(),
            null,
            NoOpTerminal::new,
            PiCliKeybindingsLoader.createDefault(),
            authStorage
        );

        var session = createSession(module, List.of("--print"));

        assertThat(session.authSelection().loggedInProviders())
            .extracting(PiInteractiveSession.AuthProvider::providerId)
            .containsExactly("anthropic");
    }

    private static PiInteractiveSession createSession(PiCliModule module, List<String> argv) throws Exception {
        Method method = PiCliModule.class.getDeclaredMethod("createDefaultSession", PiCliArgs.class);
        method.setAccessible(true);
        return (PiInteractiveSession) method.invoke(module, new PiCliParser().parse(argv));
    }

    private static PiAiClient testClient() {
        var models = new ModelRegistry();
        models.register(new Model(
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
        return new PiAiClient(new ApiProviderRegistry(), models, CredentialResolver.defaultResolver());
    }

    private static final class NoOpTerminal implements Terminal {
        @Override
        public void start(dev.pi.tui.InputHandler onInput, Runnable onResize) {
        }

        @Override
        public void stop() {
        }

        @Override
        public void write(String data) {
        }

        @Override
        public int columns() {
            return 80;
        }

        @Override
        public int rows() {
            return 24;
        }
    }
}
