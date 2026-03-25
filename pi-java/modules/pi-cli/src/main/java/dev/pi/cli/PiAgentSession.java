package dev.pi.cli;

import dev.pi.agent.runtime.Agent;
import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentLoopConfig;
import dev.pi.agent.runtime.AgentMessage;
import dev.pi.agent.runtime.AgentMessages;
import dev.pi.agent.runtime.AgentState;
import dev.pi.agent.runtime.AgentTool;
import dev.pi.ai.model.CacheRetention;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.ThinkingBudgets;
import dev.pi.ai.model.ThinkingLevel;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.ToolCall;
import dev.pi.ai.model.Transport;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.Subscription;
import dev.pi.sdk.CreateAgentSessionOptions;
import dev.pi.sdk.PiSdkSession;
import dev.pi.session.AuthStorage;
import dev.pi.session.InstructionResourceLoader;
import dev.pi.session.InstructionResources;
import dev.pi.session.SessionEntry;
import dev.pi.session.SessionManager;
import dev.pi.session.SessionTreeNode;
import dev.pi.session.SettingsManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public final class PiAgentSession implements PiInteractiveSession {
    private static final List<String> AVAILABLE_THINKING_LEVELS = List.of("off", "minimal", "low", "medium", "high", "xhigh");
    private static final List<String> PACKAGE_AUTH_PROVIDERS = List.of("github", "gitlab");

    private final PiSdkSession sdkSession;
    private final SettingsManager settingsManager;
    private final AuthStorage authStorage;
    private final InstructionResourceLoader instructionResourceLoader;
    private volatile InstructionResources instructionResources;
    private final String systemPrompt;
    private final String appendSystemPrompt;
    private final ReloadAction reloadAction;
    private final ThemeReloadAction themeReloadAction;
    private final StartupResourcePathsReloadAction startupResourcePathsReloadAction;
    private final CloseAction closeAction;
    private volatile List<CycleModel> cycleModels;
    private volatile boolean scopedCycleModels;
    private final int availableProviderCount;
    private final List<Model> modelSelectorModels;
    private final List<String> extensionPaths;
    private volatile List<String> skillPaths;
    private volatile List<String> promptPaths;
    private volatile List<String> customThemes;
    private volatile List<CycleModel> modelSelectionTargets = List.of();

    private PiAgentSession(
        PiSdkSession sdkSession,
        SettingsManager settingsManager,
        AuthStorage authStorage,
        InstructionResourceLoader instructionResourceLoader,
        InstructionResources instructionResources,
        String systemPrompt,
        String appendSystemPrompt,
        ReloadAction reloadAction,
        ThemeReloadAction themeReloadAction,
        StartupResourcePathsReloadAction startupResourcePathsReloadAction,
        CloseAction closeAction,
        List<CycleModel> cycleModels,
        boolean scopedCycleModels,
        int availableProviderCount,
        List<Model> modelSelectorModels,
        List<String> extensionPaths,
        List<String> skillPaths,
        List<String> promptPaths,
        List<String> customThemes
    ) {
        this.sdkSession = Objects.requireNonNull(sdkSession, "sdkSession");
        this.settingsManager = Objects.requireNonNull(settingsManager, "settingsManager");
        this.authStorage = authStorage == null ? AuthStorage.inMemory() : authStorage;
        this.instructionResourceLoader = instructionResourceLoader;
        this.instructionResources = Objects.requireNonNull(instructionResources, "instructionResources");
        this.systemPrompt = systemPrompt;
        this.appendSystemPrompt = appendSystemPrompt;
        this.reloadAction = reloadAction;
        this.themeReloadAction = themeReloadAction;
        this.startupResourcePathsReloadAction = startupResourcePathsReloadAction;
        this.closeAction = closeAction;
        this.cycleModels = List.copyOf(Objects.requireNonNullElse(cycleModels, List.of()));
        this.scopedCycleModels = scopedCycleModels;
        this.availableProviderCount = Math.max(1, availableProviderCount);
        this.modelSelectorModels = List.copyOf(Objects.requireNonNullElse(modelSelectorModels, List.of()));
        this.extensionPaths = List.copyOf(Objects.requireNonNullElse(extensionPaths, List.of()));
        this.skillPaths = List.copyOf(Objects.requireNonNullElse(skillPaths, List.of()));
        this.promptPaths = List.copyOf(Objects.requireNonNullElse(promptPaths, List.of()));
        this.customThemes = List.copyOf(Objects.requireNonNullElse(customThemes, List.of()));
    }

    public static Builder builder(
        Model model,
        SessionManager sessionManager,
        SettingsManager settingsManager,
        InstructionResources instructionResources
    ) {
        return new Builder(model, sessionManager, settingsManager, instructionResources);
    }

    public Agent agent() {
        return sdkSession.agent();
    }

    public SessionManager sessionManager() {
        return sdkSession.sessionManager();
    }

    public SettingsManager settingsManager() {
        return settingsManager;
    }

    public AuthStorage authStorage() {
        return authStorage;
    }

    public InstructionResources instructionResources() {
        return instructionResources;
    }

    @Override
    public String sessionId() {
        return sdkSession.sessionId();
    }

    @Override
    public AgentState state() {
        return sdkSession.state();
    }

    @Override
    public Subscription subscribe(Consumer<AgentEvent> listener) {
        return sdkSession.subscribe(listener);
    }

    @Override
    public Subscription subscribeState(Consumer<AgentState> listener) {
        return sdkSession.subscribeState(listener);
    }

    @Override
    public CompletionStage<Void> prompt(String text) {
        return sdkSession.prompt(text);
    }

    @Override
    public CompletionStage<Void> prompt(AgentMessage.UserMessage message) {
        return sdkSession.prompt(message);
    }

    public CompletionStage<Void> prompt(AgentMessage message) {
        return sdkSession.prompt(message);
    }

    @Override
    public CompletionStage<Void> steer(String text) {
        Objects.requireNonNull(text, "text");
        sdkSession.agent().steer(new AgentMessage.UserMessage(
            List.of(new TextContent(text, null)),
            System.currentTimeMillis()
        ));
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> resume() {
        return sdkSession.resume();
    }

    @Override
    public CompletionStage<Void> waitForIdle() {
        return sdkSession.waitForIdle();
    }

    @Override
    public void abort() {
        sdkSession.abort();
    }

    @Override
    public boolean autoCompactionEnabled() {
        return settingsManager.effective().getBoolean("/compaction/enabled", true);
    }

    @Override
    public ContextUsage contextUsage() {
        var contextWindow = sdkSession.state().model().contextWindow();
        if (contextWindow <= 0) {
            return null;
        }

        var branchEntries = sessionManager().branch();
        var latestCompactionIndex = latestCompactionIndex(branchEntries);
        if (latestCompactionIndex >= 0 && !hasPostCompactionUsage(branchEntries, latestCompactionIndex)) {
            return new ContextUsage(null, contextWindow, null);
        }

        var latestUsage = latestAssistantUsage();
        if (latestUsage == null || latestUsage.totalTokens() <= 0) {
            return new ContextUsage(0, contextWindow, 0.0);
        }
        return new ContextUsage(
            latestUsage.totalTokens(),
            contextWindow,
            latestUsage.totalTokens() * 100.0 / contextWindow
        );
    }

    @Override
    public int availableProviderCount() {
        return availableProviderCount;
    }

    @Override
    public String cwd() {
        return sessionManager().header().cwd();
    }

    @Override
    public String sessionName() {
        for (var index = sessionManager().entries().size() - 1; index >= 0; index--) {
            var entry = sessionManager().entries().get(index);
            if (entry instanceof SessionEntry.SessionInfoEntry sessionInfoEntry
                && sessionInfoEntry.name() != null
                && !sessionInfoEntry.name().isBlank()) {
                return sessionInfoEntry.name().trim();
            }
        }
        return null;
    }

    @Override
    public String setSessionName(String name) {
        var normalized = name == null ? "" : name.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Session name must not be empty");
        }
        try {
            sessionManager().appendSessionInfo(normalized);
            return normalized;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist session name", exception);
        }
    }

    @Override
    public SessionStats sessionStats() {
        var messages = sdkSession.state().messages();
        var userMessages = 0;
        var assistantMessages = 0;
        var toolCalls = 0;
        var toolResults = 0;
        var totalInput = 0;
        var totalOutput = 0;
        var totalCacheRead = 0;
        var totalCacheWrite = 0;
        var totalCost = 0.0;

        for (var message : messages) {
            if (message instanceof AgentMessage.UserMessage) {
                userMessages += 1;
                continue;
            }
            if (message instanceof AgentMessage.ToolResultMessage) {
                toolResults += 1;
                continue;
            }
            if (message instanceof AgentMessage.AssistantMessage assistantMessage) {
                assistantMessages += 1;
                toolCalls += (int) assistantMessage.content().stream()
                    .filter(ToolCall.class::isInstance)
                    .count();
                totalInput += assistantMessage.usage().input();
                totalOutput += assistantMessage.usage().output();
                totalCacheRead += assistantMessage.usage().cacheRead();
                totalCacheWrite += assistantMessage.usage().cacheWrite();
                totalCost += assistantMessage.usage().cost().total();
            }
        }

        return new SessionStats(
            sessionManager().sessionFile() == null ? null : sessionManager().sessionFile().toString(),
            sessionId(),
            userMessages,
            assistantMessages,
            toolCalls,
            toolResults,
            messages.size(),
            new TokenStats(
                totalInput,
                totalOutput,
                totalCacheRead,
                totalCacheWrite,
                totalInput + totalOutput + totalCacheRead + totalCacheWrite
            ),
            totalCost
        );
    }

    @Override
    public StartupResources startupResources() {
        var contextFiles = instructionResources.contextFiles().stream()
            .map(instructionFile -> instructionFile.path().toString())
            .toList();
        return new StartupResources(contextFiles, extensionPaths, skillPaths, promptPaths, customThemes);
    }

    @Override
    public String exportToHtml(String outputPath) {
        var sessionFile = sessionManager().sessionFile();
        if (sessionFile == null) {
            throw new IllegalStateException("Export is only available for persisted sessions");
        }
        try {
            var resolvedOutput = outputPath == null || outputPath.isBlank() ? null : Path.of(outputPath);
            return new PiExportCommand().export(sessionFile, resolvedOutput).toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export session", exception);
        }
    }

    @Override
    public ModelCycleResult cycleModelForward() {
        return cycleModel(1);
    }

    @Override
    public ModelCycleResult cycleModelBackward() {
        return cycleModel(-1);
    }

    @Override
    public String cycleThinkingLevel() {
        var model = sdkSession.state().model();
        if (!model.reasoning()) {
            throw new UnsupportedOperationException("Thinking level is not available for current model");
        }
        var next = nextThinkingLevel(sdkSession.state().thinkingLevel());
        sdkSession.agent().setThinkingLevel(next);
        try {
            sessionManager().appendThinkingLevelChange(next == null ? "off" : next.value());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist thinking level", exception);
        }
        return next == null ? "off" : next.value();
    }

    @Override
    public List<SelectableModel> selectableModels() {
        var availableModels = availableCycleModels();
        var selectedIndex = selectedModelIndex(availableModels);
        var currentThinkingLevel = sdkSession.state().thinkingLevel();
        var currentModel = sdkSession.state().model();
        var models = new ArrayList<SelectableModel>(availableModels.size());
        for (var index = 0; index < availableModels.size(); index += 1) {
            models.add(toSelectableModel(index, availableModels.get(index), currentModel, currentThinkingLevel));
        }
        if (selectedIndex >= 0 && selectedIndex < models.size()) {
            var selected = models.get(selectedIndex);
            models.set(selectedIndex, new SelectableModel(
                selected.index(),
                selected.provider(),
                selected.modelId(),
                selected.modelName(),
                selected.thinkingLevel(),
                true,
                selected.reasoning(),
                selected.contextWindow()
            ));
        }
        return List.copyOf(models);
    }

    @Override
    public ModelSelection modelSelection() {
        var currentThinkingLevel = sdkSession.state().thinkingLevel();
        var currentModel = sdkSession.state().model();
        var allTargets = uniqueSelectorModels();
        var scopedTargets = scopedCycleModels ? cycleModels : List.<CycleModel>of();
        var targets = new ArrayList<CycleModel>(allTargets.size() + scopedTargets.size());
        var allModels = new ArrayList<SelectableModel>(allTargets.size());
        for (var target : allTargets) {
            var targetIndex = targets.size();
            targets.add(target);
            allModels.add(toSelectableModel(targetIndex, target, currentModel, currentThinkingLevel));
        }
        var scopedModels = new ArrayList<SelectableModel>(scopedTargets.size());
        for (var target : scopedTargets) {
            var targetIndex = targets.size();
            targets.add(target);
            scopedModels.add(toSelectableModel(targetIndex, target, currentModel, currentThinkingLevel));
        }
        modelSelectionTargets = List.copyOf(targets);
        return new ModelSelection(allModels, scopedModels);
    }

    @Override
    public ScopedModelsSelection scopedModelsSelection() {
        var selection = modelSelection();
        var enabledIds = scopedCycleModels
            ? cycleModels.stream().map(PiAgentSession::fullModelId).toList()
            : List.<String>of();
        return new ScopedModelsSelection(selection.allModels(), enabledIds, scopedCycleModels);
    }

    @Override
    public void updateScopedModels(List<String> enabledModelIds) {
        applyScopedModels(enabledModelIds, false);
    }

    @Override
    public void saveScopedModels(List<String> enabledModelIds) {
        applyScopedModels(enabledModelIds, true);
    }

    @Override
    public ModelCycleResult selectModel(int index) {
        if (sdkSession.state().isStreaming()) {
            throw new IllegalStateException("Wait for the current response to finish before switching models");
        }
        var next = resolveModelSelectionTarget(index);
        var currentThinkingLevel = sdkSession.state().thinkingLevel();
        var effectiveThinkingLevel = effectiveThinkingLevel(next, currentThinkingLevel);
        if (sameModel(next.model(), sdkSession.state().model()) && Objects.equals(currentThinkingLevel, effectiveThinkingLevel)) {
            return new ModelCycleResult(
                next.model().provider(),
                next.model().id(),
                next.model().name(),
                effectiveThinkingLevel == null ? "off" : effectiveThinkingLevel.value(),
                scopedCycleModels
            );
        }

        var nextThinkingLevel = applyCycleModel(next);
        return new ModelCycleResult(
            next.model().provider(),
            next.model().id(),
            next.model().name(),
            nextThinkingLevel == null ? "off" : nextThinkingLevel.value(),
            scopedCycleModels
        );
    }

    @Override
    public SettingsSelection settingsSelection() {
        var thinkingLevel = sdkSession.state().thinkingLevel() == null ? "off" : sdkSession.state().thinkingLevel().value();
        return new SettingsSelection(
            settingsManager.effective().getBoolean("/compaction/enabled", true),
            queueModeValue(sdkSession.agent().steeringMode()),
            queueModeValue(sdkSession.agent().followUpMode()),
            transportValue(sdkSession.agent().transport()),
            settingsManager.effective().getBoolean("/hideThinkingBlock", false),
            settingsManager.effective().getBoolean("/quietStartup", false),
            doubleEscapeAction(),
            currentTheme(),
            availableThemes(),
            settingsManager.effective().getInt("/editorPaddingX", 0),
            sdkSession.state().model().reasoning(),
            thinkingLevel,
            sdkSession.state().model().reasoning() ? AVAILABLE_THINKING_LEVELS : List.of(),
            settingsManager.effective().getBoolean("/showHardwareCursor", true),
            settingsManager.effective().getBoolean("/clearOnShrink", true)
        );
    }

    @Override
    public void updateSetting(String settingId, String value) {
        Objects.requireNonNull(settingId, "settingId");
        Objects.requireNonNull(value, "value");
        switch (settingId) {
            case "autocompact" -> updateAutoCompaction(value);
            case "steering-mode" -> updateQueueModeSetting(value, true);
            case "follow-up-mode" -> updateQueueModeSetting(value, false);
            case "transport" -> updateTransportSetting(value);
            case "hide-thinking" -> updateHideThinkingSetting(value);
            case "quiet-startup" -> updateQuietStartupSetting(value);
            case "double-escape-action" -> updateDoubleEscapeActionSetting(value);
            case "theme" -> updateThemeSetting(value);
            case "show-hardware-cursor" -> updateShowHardwareCursorSetting(value);
            case "editor-padding" -> updateEditorPaddingSetting(value);
            case "clear-on-shrink" -> updateClearOnShrinkSetting(value);
            case "thinking" -> updateThinkingSetting(value);
            default -> throw new IllegalArgumentException("Unknown setting: " + settingId);
        }
    }

    @Override
    public String newSession() {
        if (sdkSession.state().isStreaming()) {
            throw new IllegalStateException("Wait for the current response to finish before starting a new session");
        }
        try {
            sessionManager().createBranchedSession(null);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create new session", exception);
        }
        sdkSession.agent().setSessionId(sessionManager().sessionId());
        seedSessionMetadataIfNeeded(sdkSession.state().thinkingLevel());
        restoreAgentMessages();
        return sessionManager().sessionId();
    }

    @Override
    public CompletionStage<Void> followUp(String text) {
        Objects.requireNonNull(text, "text");
        sdkSession.agent().followUp(new AgentMessage.UserMessage(
            List.of(new dev.pi.ai.model.TextContent(text, null)),
            System.currentTimeMillis()
        ));
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    public List<String> queuedSteeringMessages() {
        return sdkSession.agent().steeringMessages().stream()
            .map(PiAgentSession::renderQueuedMessage)
            .filter(text -> !text.isBlank())
            .toList();
    }

    @Override
    public List<String> queuedFollowUps() {
        return sdkSession.agent().followUpMessages().stream()
            .map(PiAgentSession::renderQueuedMessage)
            .filter(text -> !text.isBlank())
            .toList();
    }

    @Override
    public DequeueResult dequeue() {
        var messages = new ArrayList<AgentMessage>();
        messages.addAll(sdkSession.agent().drainSteeringQueue());
        messages.addAll(sdkSession.agent().drainFollowUpQueue());
        if (messages.isEmpty()) {
            return new DequeueResult("", 0);
        }
        var editorText = messages.stream()
            .map(PiAgentSession::renderQueuedMessage)
            .filter(text -> !text.isBlank())
            .reduce((left, right) -> left + "\n\n" + right)
            .orElse("");
        return new DequeueResult(editorText, messages.size());
    }

    @Override
    public String leafId() {
        return sessionManager().leafId();
    }

    @Override
    public List<SessionTreeNode> tree() {
        return sessionManager().tree();
    }

    @Override
    public TreeNavigationResult navigateTree(String targetId) {
        Objects.requireNonNull(targetId, "targetId");
        if (sdkSession.state().isStreaming()) {
            throw new IllegalStateException("Cannot navigate tree while agent is processing");
        }

        var entry = sessionManager().entry(targetId);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown session entry: " + targetId);
        }

        var editorText = switch (entry) {
            case SessionEntry.MessageEntry messageEntry when "user".equals(messageEntry.message().path("role").asText()) -> {
                sessionManager().navigate(messageEntry.parentId());
                yield extractUserEditorText(messageEntry);
            }
            default -> {
                sessionManager().navigate(targetId);
                yield null;
            }
        };

        restoreAgentMessages();
        return new TreeNavigationResult(sessionManager().leafId(), editorText);
    }

    @Override
    public List<ForkMessage> forkMessages() {
        var messages = new ArrayList<ForkMessage>();
        for (var entry : sessionManager().entries()) {
            if (!(entry instanceof SessionEntry.MessageEntry messageEntry)) {
                continue;
            }
            if (!"user".equals(messageEntry.message().path("role").asText())) {
                continue;
            }
            var text = extractUserEditorText(messageEntry);
            if (!text.isBlank()) {
                messages.add(new ForkMessage(messageEntry.id(), text));
            }
        }
        return List.copyOf(messages);
    }

    @Override
    public ForkResult fork(String entryId) {
        Objects.requireNonNull(entryId, "entryId");
        if (sdkSession.state().isStreaming()) {
            throw new IllegalStateException("Cannot fork while agent is processing");
        }

        var entry = sessionManager().entry(entryId);
        if (!(entry instanceof SessionEntry.MessageEntry messageEntry) || !"user".equals(messageEntry.message().path("role").asText())) {
            throw new IllegalArgumentException("Invalid entry ID for forking");
        }

        var selectedText = extractUserEditorText(messageEntry);
        try {
            sessionManager().createBranchedSession(messageEntry.parentId());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to fork session", exception);
        }
        sdkSession.agent().setSessionId(sessionManager().sessionId());
        seedSessionMetadataIfNeeded(sdkSession.state().thinkingLevel());
        restoreAgentMessages();
        return new ForkResult(selectedText, sessionManager().sessionId());
    }

    @Override
    public CompactionResult compact(String customInstructions) {
        if (sdkSession.state().isStreaming()) {
            throw new IllegalStateException("Wait for the current response to finish before compacting");
        }
        try {
            var result = PiCompactor.compact(sessionManager(), customInstructions);
            restoreAgentMessages();
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to compact session", exception);
        }
    }

    @Override
    public ReloadResult reload() {
        if (sdkSession.state().isStreaming()) {
            throw new IllegalStateException("Wait for the current response to finish before reloading");
        }

        settingsManager.drainErrors();
        settingsManager.reload();
        var settingsErrors = settingsManager.drainErrors();

        var resourceErrors = List.<InstructionResourceLoader.ResourceLoadError>of();
        if (instructionResourceLoader != null) {
            instructionResourceLoader.drainErrors();
            instructionResourceLoader.reload();
            instructionResources = instructionResourceLoader.resources();
            resourceErrors = instructionResourceLoader.drainErrors();
        }

        var extensionWarnings = List.<String>of();
        if (reloadAction != null) {
            try {
                extensionWarnings = List.copyOf(reloadAction.reload());
            } catch (Exception exception) {
                extensionWarnings = List.of(rootMessage(exception));
            }
        }

        var themeWarnings = List.<String>of();
        if (themeReloadAction != null) {
            try {
                var result = themeReloadAction.reload();
                customThemes = result.availableThemes();
                themeWarnings = result.warnings();
            } catch (Exception exception) {
                themeWarnings = List.of(rootMessage(exception));
            }
        }

        if (startupResourcePathsReloadAction != null) {
            try {
                var result = startupResourcePathsReloadAction.reload();
                skillPaths = result.skillPaths();
                promptPaths = result.promptPaths();
            } catch (Exception exception) {
                var updatedWarnings = new ArrayList<String>(extensionWarnings);
                updatedWarnings.add(rootMessage(exception));
                extensionWarnings = List.copyOf(updatedWarnings);
            }
        }

        sdkSession.updateSystemPrompt(systemPrompt, appendSystemPrompt, instructionResources);
        return new ReloadResult(settingsErrors, resourceErrors, themeWarnings, extensionWarnings);
    }

    @Override
    public AuthSelection authSelection() {
        var providers = new LinkedHashMap<String, AuthProvider>();
        for (var provider : knownProviders()) {
            providers.put(provider, new AuthProvider(provider, displayNameForProvider(provider), authStorage.has(provider)));
        }
        for (var provider : authStorage.providers()) {
            providers.put(provider, new AuthProvider(provider, displayNameForProvider(provider), true));
        }
        var allProviders = List.copyOf(providers.values());
        var loggedInProviders = allProviders.stream().filter(AuthProvider::loggedIn).toList();
        return new AuthSelection(allProviders, loggedInProviders);
    }

    @Override
    public void login(String provider, String secret) {
        var normalizedProvider = normalizeProvider(provider);
        authStorage.setApiKey(normalizedProvider, secret);
    }

    @Override
    public void logout(String provider) {
        var normalizedProvider = normalizeProvider(provider);
        if (!authStorage.has(normalizedProvider)) {
            throw new IllegalStateException("No saved credentials for " + normalizedProvider);
        }
        authStorage.remove(normalizedProvider);
    }

    @Override
    public void close() throws Exception {
        if (closeAction != null) {
            closeAction.close();
        }
    }

    public List<SessionPersistenceError> drainPersistenceErrors() {
        return sdkSession.drainPersistenceErrors().stream()
            .map(error -> new SessionPersistenceError(error.operation(), error.error()))
            .toList();
    }

    private static String renderQueuedMessage(AgentMessage message) {
        return switch (message) {
            case AgentMessage.UserMessage userMessage -> PiMessageRenderer.renderUserContent(userMessage.content());
            default -> PiMessageRenderer.renderMessage(message);
        };
    }

    private static String extractUserEditorText(SessionEntry.MessageEntry entry) {
        var content = entry.message().path("content");
        if (content.isTextual()) {
            return content.asText("");
        }
        if (!content.isArray()) {
            return "";
        }

        var lines = new ArrayList<String>();
        for (var item : content) {
            if (!"text".equals(item.path("type").asText())) {
                continue;
            }
            var text = item.path("text").asText("");
            if (!text.isBlank()) {
                lines.add(text);
            }
        }
        return String.join("\n", lines);
    }

    private void restoreAgentMessages() {
        var restoredMessages = sessionManager().buildSessionContext().messages().stream()
            .map(AgentMessages::fromLlmMessage)
            .toList();
        sdkSession.agent().replaceMessages(restoredMessages);
    }

    private void seedSessionMetadataIfNeeded(ThinkingLevel thinkingLevel) {
        if (!sessionManager().entries().isEmpty()) {
            return;
        }
        try {
            sessionManager().appendModelChange(sdkSession.state().model().provider(), sdkSession.state().model().id());
            if (thinkingLevel != null) {
                sessionManager().appendThinkingLevelChange(thinkingLevel.value());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to seed session metadata", exception);
        }
    }

    public record SessionPersistenceError(
        String operation,
        IOException error
    ) {
        public SessionPersistenceError {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(error, "error");
        }
    }

    @FunctionalInterface
    public interface ReloadAction {
        List<String> reload() throws Exception;
    }

    @FunctionalInterface
    public interface ThemeReloadAction {
        ThemeReloadResult reload() throws Exception;
    }

    @FunctionalInterface
    public interface StartupResourcePathsReloadAction {
        StartupResourcePaths reload() throws Exception;
    }

    @FunctionalInterface
    public interface CloseAction {
        void close() throws Exception;
    }

    public record ThemeReloadResult(
        List<String> availableThemes,
        List<String> warnings
    ) {
        public ThemeReloadResult {
            availableThemes = List.copyOf(Objects.requireNonNull(availableThemes, "availableThemes"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        }
    }

    public record StartupResourcePaths(
        List<String> skillPaths,
        List<String> promptPaths
    ) {
        public StartupResourcePaths {
            skillPaths = List.copyOf(Objects.requireNonNull(skillPaths, "skillPaths"));
            promptPaths = List.copyOf(Objects.requireNonNull(promptPaths, "promptPaths"));
        }
    }

    public record CycleModel(
        Model model,
        ThinkingLevel thinkingLevel
    ) {
        public CycleModel {
            Objects.requireNonNull(model, "model");
        }
    }

    public static final class Builder {
        private final Model model;
        private final SessionManager sessionManager;
        private final SettingsManager settingsManager;
        private final InstructionResources instructionResources;
        private AuthStorage authStorage;
        private InstructionResourceLoader instructionResourceLoader;
        private AgentLoopConfig.AssistantStreamFunction streamFunction;
        private String systemPrompt;
        private String appendSystemPrompt;
        private ReloadAction reloadAction;
        private ThemeReloadAction themeReloadAction;
        private StartupResourcePathsReloadAction startupResourcePathsReloadAction;
        private CloseAction closeAction;
        private ThinkingLevel thinkingLevel;
        private List<AgentTool<?>> tools = List.of();
        private AgentLoopConfig.MessageConverter convertToLlm;
        private AgentLoopConfig.ContextTransformer transformContext;
        private AgentLoopConfig.ApiKeyProvider apiKeyProvider;
        private String apiKey;
        private Transport transport;
        private CacheRetention cacheRetention;
        private String sessionId;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Long maxRetryDelayMs;
        private ThinkingBudgets thinkingBudgets;
        private List<CycleModel> cycleModels = List.of();
        private boolean scopedCycleModels;
        private int availableProviderCount = 1;
        private List<Model> modelSelectorModels = List.of();
        private List<String> extensionPaths = List.of();
        private List<String> skillPaths = List.of();
        private List<String> promptPaths = List.of();
        private List<String> customThemes = List.of();

        private Builder(
            Model model,
            SessionManager sessionManager,
            SettingsManager settingsManager,
            InstructionResources instructionResources
        ) {
            this.model = Objects.requireNonNull(model, "model");
            this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
            this.settingsManager = Objects.requireNonNull(settingsManager, "settingsManager");
            this.instructionResources = Objects.requireNonNull(instructionResources, "instructionResources");
            this.authStorage = AuthStorage.inMemory();
        }

        public Builder streamFunction(AgentLoopConfig.AssistantStreamFunction streamFunction) {
            this.streamFunction = streamFunction;
            return this;
        }

        public Builder instructionResourceLoader(InstructionResourceLoader instructionResourceLoader) {
            this.instructionResourceLoader = instructionResourceLoader;
            return this;
        }

        public Builder authStorage(AuthStorage authStorage) {
            this.authStorage = authStorage == null ? AuthStorage.inMemory() : authStorage;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder appendSystemPrompt(String appendSystemPrompt) {
            this.appendSystemPrompt = appendSystemPrompt;
            return this;
        }

        public Builder reloadAction(ReloadAction reloadAction) {
            this.reloadAction = reloadAction;
            return this;
        }

        public Builder themeReloadAction(ThemeReloadAction themeReloadAction) {
            this.themeReloadAction = themeReloadAction;
            return this;
        }

        public Builder startupResourcePathsReloadAction(StartupResourcePathsReloadAction startupResourcePathsReloadAction) {
            this.startupResourcePathsReloadAction = startupResourcePathsReloadAction;
            return this;
        }

        public Builder closeAction(CloseAction closeAction) {
            this.closeAction = closeAction;
            return this;
        }

        public Builder thinkingLevel(ThinkingLevel thinkingLevel) {
            this.thinkingLevel = thinkingLevel;
            return this;
        }

        public Builder tools(List<AgentTool<?>> tools) {
            this.tools = tools == null ? List.of() : List.copyOf(tools);
            return this;
        }

        public Builder convertToLlm(AgentLoopConfig.MessageConverter convertToLlm) {
            this.convertToLlm = convertToLlm;
            return this;
        }

        public Builder transformContext(AgentLoopConfig.ContextTransformer transformContext) {
            this.transformContext = transformContext;
            return this;
        }

        public Builder apiKeyProvider(AgentLoopConfig.ApiKeyProvider apiKeyProvider) {
            this.apiKeyProvider = apiKeyProvider;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder transport(Transport transport) {
            this.transport = transport;
            return this;
        }

        public Builder cacheRetention(CacheRetention cacheRetention) {
            this.cacheRetention = cacheRetention;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers.clear();
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }

        public Builder header(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        public Builder maxRetryDelayMs(Long maxRetryDelayMs) {
            this.maxRetryDelayMs = maxRetryDelayMs;
            return this;
        }

        public Builder thinkingBudgets(ThinkingBudgets thinkingBudgets) {
            this.thinkingBudgets = thinkingBudgets;
            return this;
        }

        public Builder cycleModels(List<CycleModel> cycleModels, boolean scopedCycleModels) {
            this.cycleModels = cycleModels == null ? List.of() : List.copyOf(cycleModels);
            this.scopedCycleModels = scopedCycleModels;
            return this;
        }

        public Builder availableProviderCount(int availableProviderCount) {
            this.availableProviderCount = Math.max(1, availableProviderCount);
            return this;
        }

        public Builder modelSelectorModels(List<Model> modelSelectorModels) {
            this.modelSelectorModels = modelSelectorModels == null ? List.of() : List.copyOf(modelSelectorModels);
            return this;
        }

        public Builder extensionPaths(List<String> extensionPaths) {
            this.extensionPaths = extensionPaths == null ? List.of() : List.copyOf(extensionPaths);
            return this;
        }

        public Builder skillPaths(List<String> skillPaths) {
            this.skillPaths = skillPaths == null ? List.of() : List.copyOf(skillPaths);
            return this;
        }

        public Builder promptPaths(List<String> promptPaths) {
            this.promptPaths = promptPaths == null ? List.of() : List.copyOf(promptPaths);
            return this;
        }

        public Builder customThemes(List<String> customThemes) {
            this.customThemes = customThemes == null ? List.of() : List.copyOf(customThemes);
            return this;
        }

        public PiAgentSession build() {
            if (streamFunction == null) {
                throw new IllegalStateException("PiAgentSession streamFunction must be configured");
            }

            var effectiveInstructionResources = instructionResourceLoader == null
                ? instructionResources
                : instructionResourceLoader.resources();
            var effectiveApiKeyProvider = apiKeyProvider;
            if ((apiKey == null || apiKey.isBlank()) && effectiveApiKeyProvider == null && authStorage != null) {
                effectiveApiKeyProvider = provider -> java.util.concurrent.CompletableFuture.completedFuture(authStorage.getApiKey(provider));
            }
            var options = CreateAgentSessionOptions.builder(model, streamFunction, sessionManager)
                .settingsManager(settingsManager)
                .instructionResources(effectiveInstructionResources)
                .systemPrompt(systemPrompt)
                .appendSystemPrompt(appendSystemPrompt)
                .thinkingLevel(thinkingLevel)
                .tools(tools)
                .convertToLlm(convertToLlm)
                .transformContext(transformContext)
                .apiKeyProvider(effectiveApiKeyProvider)
                .apiKey(apiKey)
                .transport(transport)
                .cacheRetention(cacheRetention)
                .sessionId(sessionId)
                .headers(headers)
                .maxRetryDelayMs(maxRetryDelayMs)
                .thinkingBudgets(thinkingBudgets)
                .build();

            return new PiAgentSession(
                PiSdkSession.create(options),
                settingsManager,
                authStorage,
                instructionResourceLoader,
                effectiveInstructionResources,
                systemPrompt,
                appendSystemPrompt,
                reloadAction,
                themeReloadAction,
                startupResourcePathsReloadAction,
                closeAction,
                cycleModels,
                scopedCycleModels,
                availableProviderCount,
                modelSelectorModels,
                extensionPaths,
                skillPaths,
                promptPaths,
                customThemes
            );
        }

    }

    private int indexOfModel(Model model) {
        for (var index = 0; index < cycleModels.size(); index++) {
            if (sameModel(cycleModels.get(index).model(), model)) {
                return index;
            }
        }
        return -1;
    }

    private ModelCycleResult cycleModel(int step) {
        if (sdkSession.state().isStreaming()) {
            throw new IllegalStateException("Wait for the current response to finish before switching models");
        }
        var availableModels = availableCycleModels();
        if (availableModels.size() <= 1) {
            return null;
        }

        var currentIndex = selectedModelIndex(availableModels);
        var nextIndex = currentIndex < 0
            ? (step < 0 ? availableModels.size() - 1 : 0)
            : Math.floorMod(currentIndex + step, availableModels.size());
        var next = availableModels.get(nextIndex);
        var nextThinkingLevel = applyCycleModel(next);
        return new ModelCycleResult(
            next.model().provider(),
            next.model().id(),
            next.model().name(),
            nextThinkingLevel == null ? "off" : nextThinkingLevel.value(),
            scopedCycleModels
        );
    }

    private List<CycleModel> availableCycleModels() {
        if (!cycleModels.isEmpty()) {
            return cycleModels;
        }
        return List.of(new CycleModel(sdkSession.state().model(), sdkSession.state().thinkingLevel()));
    }

    private int selectedModelIndex(List<CycleModel> availableModels) {
        var currentModel = sdkSession.state().model();
        var currentThinkingLevel = sdkSession.state().thinkingLevel();
        for (var index = 0; index < availableModels.size(); index += 1) {
            var candidate = availableModels.get(index);
            if (!sameModel(candidate.model(), currentModel)) {
                continue;
            }
            if (Objects.equals(candidate.thinkingLevel(), currentThinkingLevel)) {
                return index;
            }
        }
        for (var index = 0; index < availableModels.size(); index += 1) {
            if (sameModel(availableModels.get(index).model(), currentModel)) {
                return index;
            }
        }
        return -1;
    }

    private ThinkingLevel applyCycleModel(CycleModel next) {
        var currentThinkingLevel = sdkSession.state().thinkingLevel();
        var nextThinkingLevel = effectiveThinkingLevel(next, currentThinkingLevel);

        sdkSession.agent().setModel(next.model());
        try {
            sessionManager().appendModelChange(next.model().provider(), next.model().id());
            if (!Objects.equals(currentThinkingLevel, nextThinkingLevel)) {
                sdkSession.agent().setThinkingLevel(nextThinkingLevel);
                sessionManager().appendThinkingLevelChange(nextThinkingLevel == null ? "off" : nextThinkingLevel.value());
            }
            settingsManager.updateGlobal(settings -> settings.withMutations(root -> {
                root.put("defaultProvider", next.model().provider());
                root.put("defaultModel", next.model().id());
            }));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist model change", exception);
        }
        return nextThinkingLevel;
    }

    private static ThinkingLevel effectiveThinkingLevel(CycleModel next, ThinkingLevel currentThinkingLevel) {
        return next.model().reasoning()
            ? (next.thinkingLevel() == null ? currentThinkingLevel : next.thinkingLevel())
            : null;
    }

    private static String displayThinkingLevel(CycleModel cycleModel, Model currentModel, ThinkingLevel currentThinkingLevel) {
        var effective = effectiveThinkingLevel(cycleModel, currentThinkingLevel);
        if (cycleModel.thinkingLevel() != null && effective != null) {
            return effective.value();
        }
        if (!cycleModel.model().reasoning()) {
            return "off";
        }
        if (sameModel(cycleModel.model(), currentModel)) {
            return effective == null ? "off" : effective.value();
        }
        return "current";
    }

    private static boolean sameModel(Model left, Model right) {
        return Objects.equals(left.provider(), right.provider()) && Objects.equals(left.id(), right.id());
    }

    private void applyScopedModels(List<String> enabledModelIds, boolean persist) {
        var allTargets = uniqueSelectorModels();
        if (allTargets.isEmpty()) {
            cycleModels = List.of();
            scopedCycleModels = false;
            if (persist) {
                settingsManager.setEnabledModels(List.of());
            }
            return;
        }

        var normalizedIds = normalizeScopedModelIds(enabledModelIds, allTargets);
        if (normalizedIds.isEmpty() || normalizedIds.size() >= allTargets.size()) {
            cycleModels = allTargets;
            scopedCycleModels = false;
            if (persist) {
                settingsManager.setEnabledModels(List.of());
            }
            return;
        }

        var currentThinkingLevel = sdkSession.state().thinkingLevel();
        var byId = new LinkedHashMap<String, CycleModel>();
        for (var target : allTargets) {
            byId.put(fullModelId(target), target);
        }

        var nextScopedModels = new ArrayList<CycleModel>();
        for (var id : normalizedIds) {
            var target = byId.get(id);
            if (target == null) {
                continue;
            }
            nextScopedModels.add(new CycleModel(
                target.model(),
                resolveScopedThinkingLevel(id, currentThinkingLevel)
            ));
        }

        if (nextScopedModels.isEmpty() || nextScopedModels.size() >= allTargets.size()) {
            cycleModels = allTargets;
            scopedCycleModels = false;
            if (persist) {
                settingsManager.setEnabledModels(List.of());
            }
            return;
        }

        cycleModels = List.copyOf(nextScopedModels);
        scopedCycleModels = true;
        if (persist) {
            settingsManager.setEnabledModels(normalizedIds);
        }
    }

    private List<String> normalizeScopedModelIds(List<String> enabledModelIds, List<CycleModel> allTargets) {
        if (enabledModelIds == null || enabledModelIds.isEmpty()) {
            return List.of();
        }
        var availableIds = allTargets.stream().map(PiAgentSession::fullModelId).toList();
        var availableSet = new LinkedHashSet<>(availableIds);
        var normalized = new ArrayList<String>();
        for (var id : enabledModelIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            var trimmed = id.trim();
            if (!availableSet.contains(trimmed) || normalized.contains(trimmed)) {
                continue;
            }
            normalized.add(trimmed);
        }
        return List.copyOf(normalized);
    }

    private ThinkingLevel resolveScopedThinkingLevel(String id, ThinkingLevel currentThinkingLevel) {
        for (var cycleModel : cycleModels) {
            if (fullModelId(cycleModel).equals(id) && cycleModel.thinkingLevel() != null) {
                return cycleModel.thinkingLevel();
            }
        }
        var model = uniqueSelectorModels().stream()
            .map(CycleModel::model)
            .filter(candidate -> fullModelId(candidate).equals(id))
            .findFirst()
            .orElse(null);
        if (model == null || !model.reasoning()) {
            return null;
        }
        return currentThinkingLevel;
    }

    private List<String> knownProviders() {
        var providers = new LinkedHashSet<String>();
        providers.addAll(PACKAGE_AUTH_PROVIDERS);
        for (var model : modelSelectorModels) {
            providers.add(model.provider());
        }
        for (var cycleModel : cycleModels) {
            providers.add(cycleModel.model().provider());
        }
        providers.add(sdkSession.state().model().provider());
        return List.copyOf(providers);
    }

    private static String fullModelId(CycleModel cycleModel) {
        return fullModelId(cycleModel.model());
    }

    private static String fullModelId(Model model) {
        return model.provider() + "/" + model.id();
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider is required");
        }
        return provider.trim();
    }

    private static String displayNameForProvider(String provider) {
        return switch (provider) {
            case "anthropic" -> "Anthropic";
            case "github" -> "GitHub";
            case "gitlab" -> "GitLab";
            case "openai" -> "OpenAI";
            case "google" -> "Google";
            case "bedrock" -> "AWS Bedrock";
            default -> provider;
        };
    }

    private CycleModel resolveModelSelectionTarget(int index) {
        if (index >= 0 && index < modelSelectionTargets.size()) {
            return modelSelectionTargets.get(index);
        }
        var availableModels = availableCycleModels();
        if (index >= 0 && index < availableModels.size()) {
            return availableModels.get(index);
        }
        throw new IllegalArgumentException("Unknown model selection: " + index);
    }

    private List<CycleModel> uniqueSelectorModels() {
        var uniqueModels = new LinkedHashSet<Model>();
        if (!modelSelectorModels.isEmpty()) {
            uniqueModels.addAll(modelSelectorModels);
        } else if (!cycleModels.isEmpty()) {
            for (var cycleModel : cycleModels) {
                uniqueModels.add(cycleModel.model());
            }
        }
        if (uniqueModels.isEmpty()) {
            uniqueModels.add(sdkSession.state().model());
        }
        return uniqueModels.stream()
            .map(model -> new CycleModel(model, null))
            .toList();
    }

    private SelectableModel toSelectableModel(
        int index,
        CycleModel cycleModel,
        Model currentModel,
        ThinkingLevel currentThinkingLevel
    ) {
        return new SelectableModel(
            index,
            cycleModel.model().provider(),
            cycleModel.model().id(),
            cycleModel.model().name(),
            displayThinkingLevel(cycleModel, currentModel, currentThinkingLevel),
            sameModel(cycleModel.model(), currentModel) && Objects.equals(effectiveThinkingLevel(cycleModel, currentThinkingLevel), currentThinkingLevel),
            cycleModel.model().reasoning(),
            cycleModel.model().contextWindow()
        );
    }

    private Usage latestAssistantUsage() {
        var messages = sdkSession.state().messages();
        for (var index = messages.size() - 1; index >= 0; index--) {
            var message = messages.get(index);
            if (message instanceof AgentMessage.AssistantMessage assistantMessage
                && assistantMessage.stopReason() != StopReason.ERROR
                && assistantMessage.stopReason() != StopReason.ABORTED) {
                return assistantMessage.usage();
            }
        }
        return null;
    }

    private static int latestCompactionIndex(List<SessionEntry> branchEntries) {
        for (var index = branchEntries.size() - 1; index >= 0; index--) {
            if (branchEntries.get(index) instanceof SessionEntry.CompactionEntry) {
                return index;
            }
        }
        return -1;
    }

    private static boolean hasPostCompactionUsage(List<SessionEntry> branchEntries, int compactionIndex) {
        for (var index = branchEntries.size() - 1; index > compactionIndex; index--) {
            var entry = branchEntries.get(index);
            if (!(entry instanceof SessionEntry.MessageEntry messageEntry)) {
                continue;
            }
            if (!"assistant".equals(messageEntry.message().path("role").asText())) {
                continue;
            }

            var stopReason = messageEntry.message().path("stopReason").asText("stop");
            if ("error".equals(stopReason) || "aborted".equals(stopReason)) {
                break;
            }
            return messageEntry.message().path("usage").path("totalTokens").asInt(0) > 0;
        }
        return false;
    }

    private static String rootMessage(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static ThinkingLevel nextThinkingLevel(ThinkingLevel current) {
        return switch (current) {
            case null -> ThinkingLevel.MINIMAL;
            case MINIMAL -> ThinkingLevel.LOW;
            case LOW -> ThinkingLevel.MEDIUM;
            case MEDIUM -> ThinkingLevel.HIGH;
            case HIGH, XHIGH -> null;
        };
    }

    private void updateAutoCompaction(String value) {
        var enabled = parseBooleanSetting("autocompact", value);
        settingsManager.updateGlobal(settings -> settings.withMutations(root -> root.with("compaction").put("enabled", enabled)));
    }

    private void updateQueueModeSetting(String value, boolean steering) {
        var queueMode = parseQueueMode(value);
        if (steering) {
            sdkSession.agent().setSteeringMode(queueMode);
            settingsManager.updateGlobal(settings -> settings.withMutations(root -> root.put("steeringMode", value)));
            return;
        }
        sdkSession.agent().setFollowUpMode(queueMode);
        settingsManager.updateGlobal(settings -> settings.withMutations(root -> root.put("followUpMode", value)));
    }

    private void updateThinkingSetting(String value) {
        if (!sdkSession.state().model().reasoning()) {
            throw new IllegalStateException("Thinking level is not available for current model");
        }

        var normalized = normalizeThinkingLevel(value);
        settingsManager.updateGlobal(settings -> settings.withMutations(root -> root.put("defaultThinkingLevel", normalized)));

        var currentValue = sdkSession.state().thinkingLevel() == null ? "off" : sdkSession.state().thinkingLevel().value();
        if (currentValue.equals(normalized)) {
            return;
        }

        var nextThinkingLevel = "off".equals(normalized) ? null : ThinkingLevel.fromValue(normalized);
        sdkSession.agent().setThinkingLevel(nextThinkingLevel);
        try {
            sessionManager().appendThinkingLevelChange(normalized);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist thinking level", exception);
        }
    }

    private void updateHideThinkingSetting(String value) {
        var hidden = parseBooleanSetting("hide-thinking", value);
        settingsManager.updateGlobal(settings -> settings.withMutations(root -> root.put("hideThinkingBlock", hidden)));
    }

    private void updateQuietStartupSetting(String value) {
        var quiet = parseBooleanSetting("quiet-startup", value);
        settingsManager.updateGlobal(settings -> settings.withMutations(root -> root.put("quietStartup", quiet)));
    }

    private void updateThemeSetting(String value) {
        var normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Invalid value for theme: " + value);
        }
        settingsManager.updateGlobal(settings -> settings.withMutations(root -> root.put("theme", normalized)));
    }

    private void updateDoubleEscapeActionSetting(String value) {
        var normalized = value == null ? "" : value.trim();
        if (!"tree".equals(normalized) && !"fork".equals(normalized) && !"none".equals(normalized)) {
            throw new IllegalArgumentException("Invalid value for double-escape-action: " + value);
        }
        settingsManager.updateGlobal(settings -> settings.withMutations(root -> root.put("doubleEscapeAction", normalized)));
    }

    private void updateEditorPaddingSetting(String value) {
        int padding;
        try {
            padding = Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid value for editor-padding: " + value, exception);
        }
        if (padding < 0 || padding > 3) {
            throw new IllegalArgumentException("Invalid value for editor-padding: " + value);
        }
        var normalized = padding;
        settingsManager.updateGlobal(settings -> settings.withMutations(root -> root.put("editorPaddingX", normalized)));
    }

    private void updateShowHardwareCursorSetting(String value) {
        var enabled = parseBooleanSetting("show-hardware-cursor", value);
        settingsManager.updateGlobal(settings -> settings.withMutations(root -> root.put("showHardwareCursor", enabled)));
    }

    private void updateClearOnShrinkSetting(String value) {
        var enabled = parseBooleanSetting("clear-on-shrink", value);
        settingsManager.updateGlobal(settings -> settings.withMutations(root -> root.put("clearOnShrink", enabled)));
    }

    private String doubleEscapeAction() {
        var action = settingsManager.effective().getString("/doubleEscapeAction");
        return action == null || action.isBlank() ? "tree" : action.trim();
    }

    private String currentTheme() {
        var configuredTheme = settingsManager.effective().getString("/theme");
        return configuredTheme == null || configuredTheme.isBlank() ? "dark" : configuredTheme.trim();
    }

    private List<String> availableThemes() {
        var themes = new ArrayList<String>();
        var currentTheme = currentTheme();
        themes.add(currentTheme);
        for (var themeName : PiCliAnsi.availableThemeNames()) {
            if (!themeName.equals(currentTheme)) {
                themes.add(themeName);
            }
        }
        return List.copyOf(themes);
    }

    private static boolean parseBooleanSetting(String settingId, String value) {
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("Invalid value for " + settingId + ": " + value);
        };
    }

    private static Agent.QueueMode parseQueueMode(String value) {
        return switch (value) {
            case "all" -> Agent.QueueMode.ALL;
            case "one-at-a-time" -> Agent.QueueMode.ONE_AT_A_TIME;
            default -> throw new IllegalArgumentException("Unknown queue mode: " + value);
        };
    }

    private static String queueModeValue(Agent.QueueMode queueMode) {
        return queueMode == Agent.QueueMode.ALL ? "all" : "one-at-a-time";
    }

    private static String transportValue(Transport transport) {
        return transport == null ? "auto" : transport.value();
    }

    private static String normalizeThinkingLevel(String value) {
        if ("off".equals(value)) {
            return value;
        }
        return ThinkingLevel.fromValue(value).value();
    }

    private void updateTransportSetting(String value) {
        var transport = "auto".equals(value) ? null : Transport.fromValue(value);
        sdkSession.agent().setTransport(transport);
        settingsManager.updateGlobal(settings -> settings.withMutations(root -> root.put("transport", value)));
    }
}
