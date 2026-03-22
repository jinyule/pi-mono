package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentState;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.Subscription;
import dev.pi.tui.VirtualTerminal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class PiInteractiveModeShareTest {
    @Test
    void handlesShareSlashCommand() {
        var session = new MinimalSession();
        var terminal = new VirtualTerminal(120, 20);
        var shareCommand = new PiShareCommand(session, new PiShareCommand.GhCli() {
            @Override
            public void requireAuthenticated() {
            }

            @Override
            public String createSecretGist(java.nio.file.Path file) {
                return "https://gist.github.com/test/abc123";
            }
        });
        var mode = new PiInteractiveMode(
            session,
            terminal,
            new PiCopyCommand(session, text -> {
            }),
            () -> null,
            currentText -> currentText,
            null,
            shareCommand
        );

        mode.start();
        terminal.sendInput("/share");
        terminal.sendInput("\r");

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Share URL: https://pi.dev/session/#abc123")
            .contains("Gist: https://gist.github.com/test/abc123");

        mode.stop();
    }

    private static final class MinimalSession implements PiInteractiveSession {
        private final AgentState state = new AgentState("", new Model(
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
            listener.accept(state);
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
        public String cwd() {
            return "/workspace";
        }

        @Override
        public String exportToHtml(String outputPath) {
            return outputPath;
        }
    }
}
