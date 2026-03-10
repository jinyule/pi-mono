package dev.pi.cli;

import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentState;
import dev.pi.ai.stream.Subscription;
import dev.pi.session.SessionTreeNode;
import java.util.List;
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

    default String leafId() {
        return null;
    }

    default List<SessionTreeNode> tree() {
        return List.of();
    }

    default TreeNavigationResult navigateTree(String targetId) {
        throw new UnsupportedOperationException("Tree navigation is not available");
    }

    record TreeNavigationResult(
        String leafId,
        String editorText
    ) {
    }
}
