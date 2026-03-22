package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentMessage;
import dev.pi.agent.runtime.AgentState;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.Subscription;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class PiShareCommandTest {
    @Test
    void exportsSessionAndCreatesShareUrls() throws Exception {
        var session = new ShareSession("<html>shared</html>");
        var ghCli = new FakeGhCli();
        var command = new PiShareCommand(session, ghCli);

        var result = command.shareSession();

        assertThat(result)
            .isEqualTo("Share URL: https://pi.dev/session/#abc123\nGist: https://gist.github.com/test/abc123");
        assertThat(ghCli.exportedHtml()).isEqualTo("<html>shared</html>");
        assertThat(ghCli.sharedFile()).isNotNull();
        assertThat(Files.exists(ghCli.sharedFile())).isFalse();
        assertThat(session.exportPath()).isEqualTo(ghCli.sharedFile().toString());
    }

    @Test
    void failsWhenGhIsNotLoggedIn() {
        var session = new ShareSession("<html>shared</html>");
        var ghCli = new FakeGhCli().withAuthFailure("GitHub CLI is not logged in. Run 'gh auth login' first.");
        var command = new PiShareCommand(session, ghCli);

        assertThatThrownBy(command::shareSession)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("GitHub CLI is not logged in. Run 'gh auth login' first.");
        assertThat(session.exportPath()).isNull();
    }

    @Test
    void failsWhenGistOutputHasNoId() {
        var session = new ShareSession("<html>shared</html>");
        var ghCli = new FakeGhCli().withGistUrl("https://gist.github.com/test/");
        var command = new PiShareCommand(session, ghCli);

        assertThatThrownBy(command::shareSession)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to parse gist ID from gh output");
    }

    private static final class ShareSession implements PiInteractiveSession {
        private final String html;
        private final AgentState state;
        private String exportPath;

        private ShareSession(String html) {
            this.html = html;
            this.state = new AgentState("", new Model(
                "test-model",
                "test-model",
                "openai-responses",
                "openai",
                "https://example.com",
                false,
                List.of("text"),
                new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
                128_000,
                8_192,
                null,
                null
            ), null, List.of(), List.of(), false, null, Set.of(), null);
        }

        @Override
        public String sessionId() {
            return "session";
        }

        @Override
        public AgentState state() {
            return state;
        }

        @Override
        public Subscription subscribe(Consumer<AgentEvent> listener) {
            return () -> {
            };
        }

        @Override
        public Subscription subscribeState(Consumer<AgentState> listener) {
            return () -> {
            };
        }

        @Override
        public CompletionStage<Void> prompt(String text) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> resume() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> waitForIdle() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void abort() {
        }

        @Override
        public String exportToHtml(String outputPath) {
            try {
                exportPath = outputPath;
                Files.writeString(Path.of(outputPath), html, StandardCharsets.UTF_8);
                return outputPath;
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to export session", exception);
            }
        }

        private String exportPath() {
            return exportPath;
        }
    }

    private static final class FakeGhCli implements PiShareCommand.GhCli {
        private String authFailure;
        private String gistUrl = "https://gist.github.com/test/abc123";
        private Path sharedFile;
        private String exportedHtml;

        private FakeGhCli withAuthFailure(String authFailure) {
            this.authFailure = authFailure;
            return this;
        }

        private FakeGhCli withGistUrl(String gistUrl) {
            this.gistUrl = gistUrl;
            return this;
        }

        @Override
        public void requireAuthenticated() {
            if (authFailure != null) {
                throw new IllegalStateException(authFailure);
            }
        }

        @Override
        public String createSecretGist(Path file) {
            try {
                sharedFile = file;
                exportedHtml = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read exported session", exception);
            }
            return gistUrl;
        }

        private Path sharedFile() {
            return sharedFile;
        }

        private String exportedHtml() {
            return exportedHtml;
        }
    }
}
