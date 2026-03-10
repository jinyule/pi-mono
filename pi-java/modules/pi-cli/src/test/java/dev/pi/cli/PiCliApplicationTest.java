package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentState;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.Subscription;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class PiCliApplicationTest {
    @Test
    void dispatchesInteractiveMode() {
        var session = new StubSession();
        var invoked = new AtomicReference<String>();
        var app = PiCliApplication.builder(args -> session)
            .interactiveHandler((args, currentSession) -> {
                invoked.set(args.mode().value());
                return CompletableFuture.completedFuture(null);
            })
            .build();

        app.run("hello").toCompletableFuture().join();

        assertThat(invoked.get()).isEqualTo("interactive");
    }

    @Test
    void dispatchesPrintJsonAndRpcModes() {
        var session = new StubSession();
        var invoked = new java.util.ArrayList<String>();
        var app = PiCliApplication.builder(args -> session)
            .printHandler((args, currentSession) -> {
                invoked.add("print:" + String.join("|", args.messages()));
                return CompletableFuture.completedFuture(null);
            })
            .jsonHandler((args, currentSession) -> {
                invoked.add("json");
                return CompletableFuture.completedFuture(null);
            })
            .rpcHandler((args, currentSession) -> {
                invoked.add("rpc");
                return CompletableFuture.completedFuture(null);
            })
            .build();

        app.run("--print", "hello", "world").toCompletableFuture().join();
        app.run("--mode", "json", "hello").toCompletableFuture().join();
        app.run("--mode", "rpc").toCompletableFuture().join();

        assertThat(invoked).containsExactly("print:hello|world", "json", "rpc");
    }

    @Test
    void dispatchesListModelsWithoutCreatingSession() {
        var sessionCreated = new AtomicBoolean(false);
        var listModelsQuery = new AtomicReference<String>();
        var app = PiCliApplication.builder(args -> {
            sessionCreated.set(true);
            return new StubSession();
        })
            .listModelsHandler(args -> {
                listModelsQuery.set(args.listModelsQuery());
                return CompletableFuture.completedFuture(null);
            })
            .build();

        app.run("--list-models", "sonnet").toCompletableFuture().join();

        assertThat(sessionCreated).isFalse();
        assertThat(listModelsQuery.get()).isEqualTo("sonnet");
    }

    @Test
    void dispatchesExportWithoutCreatingSession() {
        var sessionCreated = new AtomicBoolean(false);
        var exportPath = new AtomicReference<String>();
        var app = PiCliApplication.builder(args -> {
            sessionCreated.set(true);
            return new StubSession();
        })
            .exportHandler(args -> {
                exportPath.set(args.exportInputPath().toString());
                return CompletableFuture.completedFuture(null);
            })
            .build();

        app.run("--export", "session.jsonl").toCompletableFuture().join();

        assertThat(sessionCreated).isFalse();
        assertThat(exportPath.get()).isEqualTo("session.jsonl");
    }

    @Test
    void surfacesSessionFactoryFailures() {
        var app = PiCliApplication.builder(args -> {
            throw new IOException("session init failed");
        }).build();

        assertThatThrownBy(() -> app.run("hello").toCompletableFuture().join())
            .hasRootCauseMessage("session init failed");
    }

    private static final class StubSession implements PiInteractiveSession {
        private final AgentState state = new AgentState(
            "",
            new Model(
                "test-model",
                "Test Model",
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
            ),
            null,
            List.of(),
            List.of(),
            false,
            null,
            Set.of(),
            null
        );

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
    }
}
