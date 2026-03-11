package dev.pi.cli;

import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentState;
import dev.pi.session.InstructionResourceLoader;
import dev.pi.session.SettingsManager;
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

    default List<ForkMessage> forkMessages() {
        return List.of();
    }

    default ForkResult fork(String entryId) {
        throw new UnsupportedOperationException("Fork is not available");
    }

    default CompactionResult compact(String customInstructions) {
        throw new UnsupportedOperationException("Compaction is not available");
    }

    default void abortCompaction() {
    }

    default ReloadResult reload() {
        throw new UnsupportedOperationException("Reload is not available");
    }

    record TreeNavigationResult(
        String leafId,
        String editorText
    ) {
    }

    record ForkMessage(
        String entryId,
        String text
    ) {
    }

    record ForkResult(
        String selectedText,
        String sessionId
    ) {
    }

    record CompactionResult(
        String summary,
        String firstKeptEntryId,
        int tokensBefore
    ) {
    }

    record ReloadResult(
        List<SettingsManager.SettingsError> settingsErrors,
        List<InstructionResourceLoader.ResourceLoadError> resourceErrors,
        List<String> extensionWarnings
    ) {
        public ReloadResult {
            settingsErrors = List.copyOf(settingsErrors);
            resourceErrors = List.copyOf(resourceErrors);
            extensionWarnings = List.copyOf(extensionWarnings);
        }
    }
}
