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

    default CompletionStage<Void> steer(String text) {
        throw new UnsupportedOperationException("Steering queueing is not available");
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

    default List<SelectableModel> selectableModels() {
        return List.of();
    }

    default ModelSelection modelSelection() {
        return new ModelSelection(selectableModels(), List.of());
    }

    default ModelCycleResult selectModel(int index) {
        throw new UnsupportedOperationException("Model selector is not available");
    }

    default SettingsSelection settingsSelection() {
        return new SettingsSelection(
            false,
            "one-at-a-time",
            "one-at-a-time",
            "auto",
            false,
            false,
            "tree",
            "dark",
            List.of("dark", "light"),
            0,
            false,
            "off",
            List.of()
        );
    }

    default void updateSetting(String settingId, String value) {
        throw new UnsupportedOperationException("Settings updates are not available");
    }

    default String newSession() {
        throw new UnsupportedOperationException("Starting a new session is not available");
    }

    default CompletionStage<Void> followUp(String text) {
        throw new UnsupportedOperationException("Follow-up queueing is not available");
    }

    default List<String> queuedSteeringMessages() {
        return List.of();
    }

    default List<String> queuedFollowUps() {
        return List.of();
    }

    default DequeueResult dequeue() {
        throw new UnsupportedOperationException("Queued message restore is not available");
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

    record SelectableModel(
        int index,
        String provider,
        String modelId,
        String modelName,
        String thinkingLevel,
        boolean current,
        boolean reasoning,
        int contextWindow
    ) {
        public SelectableModel(int index, String provider, String modelId, String thinkingLevel, boolean current) {
            this(index, provider, modelId, modelId, thinkingLevel, current, false, 0);
        }
    }

    record SettingsSelection(
        boolean autoCompact,
        String steeringMode,
        String followUpMode,
        String transport,
        boolean hideThinkingBlock,
        boolean quietStartup,
        String doubleEscapeAction,
        String theme,
        List<String> availableThemes,
        int editorPaddingX,
        boolean reasoningAvailable,
        String thinkingLevel,
        List<String> availableThinkingLevels
    ) {
        public SettingsSelection {
            availableThemes = List.copyOf(availableThemes);
            availableThinkingLevels = List.copyOf(availableThinkingLevels);
        }
    }

    record ModelSelection(
        List<SelectableModel> allModels,
        List<SelectableModel> scopedModels
    ) {
        public ModelSelection {
            allModels = List.copyOf(allModels);
            scopedModels = List.copyOf(scopedModels);
        }
    }

    record DequeueResult(
        String editorText,
        int restoredCount
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
