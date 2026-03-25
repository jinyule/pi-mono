package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentState;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.Subscription;
import dev.pi.tui.Terminal;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PiInteractiveModeAuthTest {
    @Test
    void loginCommandStoresCredentialsDirectly() throws Exception {
        var session = new FakeSession();
        var mode = new PiInteractiveMode(session, new NoOpTerminal());

        submit(mode, "/login anthropic sk-ant-test");

        assertThat(session.loggedInProvider).isEqualTo("anthropic");
        assertThat(session.loggedInSecret).isEqualTo("sk-ant-test");
        assertThat(renderedStatus(mode)).contains("Saved credentials for anthropic");
    }

    @Test
    void loginCommandImportsGithubCredentialsFromHostCli() throws Exception {
        var session = new FakeSession();
        var mode = new PiInteractiveMode(
            session,
            new NoOpTerminal(),
            new PiCopyCommand(session, text -> {
            }),
            () -> null,
            null,
            null,
            new PiShareCommand(session, new NoOpGhCli()),
            new PiHostCliAuth(ignored -> new PiHostCliAuth.CommandResult(0, "ghp-imported-token\n", ""))
        );

        submit(mode, "/login github");

        assertThat(session.loggedInProvider).isEqualTo("github");
        assertThat(session.loggedInSecret).isEqualTo("ghp-imported-token");
        assertThat(renderedStatus(mode)).contains("Imported credentials for github from gh auth token");
    }

    @Test
    void logoutWithoutSavedCredentialsShowsHelpfulStatus() throws Exception {
        var session = new FakeSession();
        var mode = new PiInteractiveMode(session, new NoOpTerminal());

        submit(mode, "/logout");

        assertThat(renderedStatus(mode)).contains("No saved credentials. Use /login first.");
    }

    private static void submit(PiInteractiveMode mode, String text) throws Exception {
        Method submit = PiInteractiveMode.class.getDeclaredMethod("submit", String.class);
        submit.setAccessible(true);
        submit.invoke(mode, text);
    }

    private static String renderedStatus(PiInteractiveMode mode) throws Exception {
        Field field = PiInteractiveMode.class.getDeclaredField("status");
        field.setAccessible(true);
        var status = (dev.pi.tui.Text) field.get(mode);
        return String.join("\n", status.render(80));
    }

    private static Model model() {
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

    private static final class FakeSession implements PiInteractiveSession {
        private final AgentState state = new AgentState(
            "session",
            model(),
            null,
            List.of(),
            List.of(),
            false,
            null,
            Set.of(),
            null
        );
        private String loggedInProvider;
        private String loggedInSecret;

        @Override
        public String sessionId() {
            return "session";
        }

        @Override
        public AgentState state() {
            return state;
        }

        @Override
        public Subscription subscribe(java.util.function.Consumer<AgentEvent> listener) {
            return () -> {
            };
        }

        @Override
        public Subscription subscribeState(java.util.function.Consumer<AgentState> listener) {
            listener.accept(state);
            return () -> {
            };
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> prompt(String text) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> resume() {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> waitForIdle() {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public void abort() {
        }

        @Override
        public AuthSelection authSelection() {
            var allProviders = List.of(
                new AuthProvider("anthropic", "Anthropic", "anthropic".equals(loggedInProvider)),
                new AuthProvider("github", "GitHub", "github".equals(loggedInProvider)),
                new AuthProvider("gitlab", "GitLab", "gitlab".equals(loggedInProvider))
            );
            var loggedIn = allProviders.stream().filter(AuthProvider::loggedIn).toList();
            return new AuthSelection(allProviders, loggedIn);
        }

        @Override
        public void login(String provider, String secret) {
            loggedInProvider = provider;
            loggedInSecret = secret;
        }
    }

    private static final class NoOpGhCli implements PiShareCommand.GhCli {
        @Override
        public void requireAuthenticated() {
        }

        @Override
        public String createSecretGist(java.nio.file.Path file) {
            throw new UnsupportedOperationException("not used");
        }
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
