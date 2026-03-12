package dev.pi.cli;

import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentMessage;
import dev.pi.agent.runtime.AgentState;
import dev.pi.ai.model.ThinkingLevel;
import dev.pi.session.SettingsManager;
import dev.pi.ai.stream.Subscription;
import dev.pi.session.InstructionResourceLoader;
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

    default CompletionStage<Void> prompt(AgentMessage.UserMessage message) {
        return prompt(PiMessageRenderer.renderUserContent(message.content()));
    }

    CompletionStage<Void> resume();

    CompletionStage<Void> waitForIdle();

    void abort();

    default ModelCycleResult cycleModelForward() {
        throw new UnsupportedOperationException("Model cycling is not available");
    }

    default ModelCycleResult cycleModelBackward() {
        throw new UnsupportedOperationException("Model cycling is not available");
    }

    default String cycleThinkingLevel() {
        throw new UnsupportedOperationException("Thinking level cycling is not available");
    }

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

    default boolean autoCompactionEnabled() {
        return false;
    }

    default ContextUsage contextUsage() {
        return null;
    }

    default int availableProviderCount() {
        return 1;
    }

    default String cwd() {
        return "";
    }

    default String sessionName() {
        return null;
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

    record ModelCycleResult(
        String provider,
        String modelId,
        String thinkingLevel,
        boolean scoped
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

    record ContextUsage(
        Integer tokens,
        int contextWindow,
        Double percent
    ) {
    }
}
