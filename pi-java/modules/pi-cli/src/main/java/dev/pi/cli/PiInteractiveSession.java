package dev.pi.cli;

import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentState;
import dev.pi.ai.stream.Subscription;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public interface PiInteractiveSession {
    String sessionId();

    AgentState state();

    Subscription subscribe(Consumer<AgentEvent> listener);

    Subscription subscribeState(Consumer<AgentState> listener);

    CompletionStage<Void> prompt(String text);

    CompletionStage<Void> resume();

    CompletionStage<Void> waitForIdle();

    void abort();
}
