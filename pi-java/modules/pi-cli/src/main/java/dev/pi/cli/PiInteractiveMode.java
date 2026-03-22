package dev.pi.cli;

import dev.pi.agent.runtime.AgentState;
import dev.pi.agent.runtime.AgentMessage;
import dev.pi.ai.model.ImageContent;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.UserContent;
import dev.pi.ai.stream.Subscription;
import dev.pi.tui.Component;
import dev.pi.tui.Focusable;
import dev.pi.tui.OverlayAnchor;
import dev.pi.tui.OverlayMargin;
import dev.pi.tui.OverlayOptions;
import dev.pi.tui.Input;
import dev.pi.tui.KeyMatcher;
import dev.pi.tui.ProcessTerminal;
import dev.pi.tui.Terminal;
import dev.pi.tui.TerminalText;
import dev.pi.tui.Text;
import dev.pi.tui.Tui;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class PiInteractiveMode implements AutoCloseable {
    private final PiInteractiveSession session;
    private final Terminal terminal;
    private final Tui tui;
    private final PiCopyCommand copyCommand;
    private final PiClipboardImage clipboardImage;
    private final PiExternalEditor externalEditor;
    private final Runnable suspendAction;
    private final Text header = new Text("", 0, 0, null);
    private final Text resources = new Text("", 1, 0, null);
    private final Text transcript = new Text("", 1, 0, null);
    private final Text status = new Text("", 1, 0, null);
    private final Text footer = new Text("", 1, 0, null);
    private final Input input = new Input();
    private final PromptInput promptInput = new PromptInput();

    private Subscription stateSubscription;
    private PiGitBranchWatcher gitBranchWatcher;
    private boolean started;
    private String manualStatus;
    private String previewTheme;
    private String reloadDiagnostics;
    private Runnable onStop;
    private long lastClearTimeMillis;
    private long lastEscapeTimeMillis;
    private boolean expandToolDetails;
    private final List<ImageContent> pendingImages = new ArrayList<>();

    public PiInteractiveMode(PiInteractiveSession session) {
        this(session, new ProcessTerminal());
    }

    public PiInteractiveMode(PiInteractiveSession session, Terminal terminal) {
        this(
            session,
            terminal,
            new PiCopyCommand(
                session,
                PiClipboard.combined(
                    PiClipboard.osc52(terminal),
                    PiClipboard.system()
                )
            ),
            PiClipboardImage.system()
        );
    }

    PiInteractiveMode(PiInteractiveSession session, Terminal terminal, PiCopyCommand copyCommand) {
        this(session, terminal, copyCommand, PiClipboardImage.system());
    }

    PiInteractiveMode(
        PiInteractiveSession session,
        Terminal terminal,
        PiCopyCommand copyCommand,
        PiClipboardImage clipboardImage
    ) {
        this(session, terminal, copyCommand, clipboardImage, null, null);
    }

    PiInteractiveMode(
        PiInteractiveSession session,
        Terminal terminal,
        PiCopyCommand copyCommand,
        PiClipboardImage clipboardImage,
        Runnable suspendAction
    ) {
        this(session, terminal, copyCommand, clipboardImage, null, suspendAction);
    }

    PiInteractiveMode(
        PiInteractiveSession session,
        Terminal terminal,
        PiCopyCommand copyCommand,
        PiClipboardImage clipboardImage,
        PiExternalEditor externalEditor,
        Runnable suspendAction
    ) {
        this.session = Objects.requireNonNull(session, "session");
        this.terminal = Objects.requireNonNull(terminal, "terminal");
        this.copyCommand = Objects.requireNonNull(copyCommand, "copyCommand");
        this.clipboardImage = Objects.requireNonNull(clipboardImage, "clipboardImage");
        this.tui = new Tui(terminal, true);
        this.externalEditor = externalEditor == null ? PiExternalEditor.system(tui) : externalEditor;
        this.suspendAction = suspendAction == null ? createSuspendAction() : suspendAction;
        this.tui.addChild(header);
        this.tui.addChild(resources);
        this.tui.addChild(transcript);
        this.tui.addChild(status);
        this.tui.addChild(footer);
        this.tui.addChild(promptInput);
        this.tui.setFocus(promptInput);
        this.input.setOnSubmit(this::submit);
        this.input.setOnExit(this::requestExit);
    }

    public void setOnStop(Runnable onStop) {
        this.onStop = onStop;
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        manualStatus = startupStatus();
        updateTerminalTitle();
        stateSubscription = session.subscribeState(this::renderState);
        gitBranchWatcher = PiGitBranchWatcher.start(session.cwd(), () -> renderState(session.state()));
        tui.start();
        renderState(session.state());
    }

    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        if (stateSubscription != null) {
            stateSubscription.unsubscribe();
            stateSubscription = null;
        }
        if (gitBranchWatcher != null) {
            gitBranchWatcher.close();
            gitBranchWatcher = null;
        }
        tui.stop();
        if (onStop != null) {
            onStop.run();
        }
    }

    @Override
    public void close() {
        stop();
    }

    private void submit(String value) {
        var hasText = value != null && !value.isBlank();
        if (!hasText && pendingImages.isEmpty()) {
            return;
        }
        var trimmed = value == null ? "" : value.trim();
        if ("/copy".equals(trimmed)) {
            input.setValue("");
            handleCopyCommand();
            return;
        }
        if ("/name".equals(trimmed) || trimmed.startsWith("/name ")) {
            input.setValue("");
            handleNameCommand(trimmed);
            return;
        }
        if ("/tree".equals(trimmed)) {
            input.setValue("");
            handleTreeCommand();
            return;
        }
        if ("/fork".equals(trimmed)) {
            input.setValue("");
            handleForkCommand();
            return;
        }
        if ("/compact".equals(trimmed) || trimmed.startsWith("/compact ")) {
            input.setValue("");
            var customInstructions = trimmed.length() == 8 ? null : trimmed.substring(9).trim();
            handleCompactCommand(customInstructions == null || customInstructions.isBlank() ? null : customInstructions);
            return;
        }
        if ("/reload".equals(trimmed)) {
            input.setValue("");
            handleReloadCommand();
            return;
        }
        if ("/model".equals(trimmed) || trimmed.startsWith("/model ")) {
            input.setValue("");
            var searchTerm = trimmed.length() == 6 ? null : trimmed.substring(7).trim();
            handleModelCommand(searchTerm == null || searchTerm.isBlank() ? null : searchTerm);
            return;
        }
        if ("/settings".equals(trimmed)) {
            input.setValue("");
            handleSettingsCommand();
            return;
        }
        if ("/new".equals(trimmed)) {
            input.setValue("");
            handleNewSessionCommand();
            return;
        }
        if ("/resume".equals(trimmed)) {
            input.setValue("");
            handleResumeCommand();
            return;
        }
        if ("/debug".equals(trimmed)) {
            input.setValue("");
            handleDebugCommand();
            return;
        }
        if ("/exit".equals(trimmed) || "/quit".equals(trimmed)) {
            input.setValue("");
            requestExit();
            return;
        }
        if (session.state().isStreaming()) {
            if (!pendingImages.isEmpty()) {
                manualStatus = "Error: Image attachments are not supported while streaming";
                renderState(session.state());
                return;
            }
            handleSteerCommand(value);
            return;
        }
        manualStatus = null;
        input.setValue("");
        tui.requestRender();
        try {
            session.prompt(buildPrompt(value));
            pendingImages.clear();
            renderState(session.state());
        } catch (RuntimeException exception) {
            renderState(session.state().withError(exception.getMessage()));
        }
    }

    private void renderState(AgentState state) {
        var settings = session.settingsSelection();
        PiCliAnsi.setTheme(previewTheme == null || previewTheme.isBlank() ? settings.theme() : previewTheme);
        tui.setShowHardwareCursor(settings.showHardwareCursor());
        tui.setClearOnShrink(settings.clearOnShrink());
        input.setPaddingX(settings.editorPaddingX());
        header.setText(renderHeader(state));
        resources.setText(renderResources());
        transcript.setText(renderTranscript(state));
        status.setText(renderStatus(state));
        footer.setText(renderFooter(state));
        tui.requestRender();
    }

    private String renderHeader(AgentState state) {
        if (session.settingsSelection().quietStartup()) {
            return "";
        }
        var lines = new ArrayList<String>();
        lines.add("pi-java interactive");
        lines.add("model: %s/%s".formatted(state.model().provider(), state.model().id()));
        lines.add("session: %s".formatted(session.sessionId()));
        if (terminal.rows() >= 14) {
            lines.add(renderHeaderHintLine(
                terminal.columns(),
                PiCliKeyHints.rawHint(appKeyDisplay(PiAppAction.INTERRUPT, "interrupt"), "interrupt"),
                PiCliKeyHints.rawHint(appKeyDisplay(PiAppAction.CLEAR, "clear"), "clear"),
                PiCliKeyHints.rawHint(appKeyDisplay(PiAppAction.CLEAR, "clear"), "twice exit"),
                PiCliKeyHints.rawHint(appKeyDisplay(PiAppAction.EXIT, "exit"), "empty exit")
            ));
        }
        if (terminal.rows() >= 16) {
            lines.add(renderHeaderHintLine(
                terminal.columns(),
                PiCliKeyHints.rawHint(appKeyDisplay(PiAppAction.SELECT_MODEL, "select-model"), "model"),
                PiCliKeyHints.rawHint(appKeyDisplay(PiAppAction.EXPAND_TOOLS, "expand-tools"), "tools"),
                PiCliKeyHints.rawHint(appKeyDisplay(PiAppAction.TOGGLE_THINKING, "toggle-thinking"), "thinking"),
                PiCliKeyHints.rawHint(appKeyDisplay(PiAppAction.PASTE_IMAGE, "paste-image"), "paste image")
            ));
        }
        return String.join("\n", lines);
    }

    private String renderTranscript(AgentState state) {
        var lines = new ArrayList<String>();
        var hideThinking = session.settingsSelection().hideThinkingBlock();
        for (var message : state.messages()) {
            lines.add(PiMessageRenderer.renderMessage(message, hideThinking, expandToolDetails));
        }
        if (state.isStreaming() && state.streamMessage() != null) {
            lines.add(PiMessageRenderer.renderStreamingMessage(state.streamMessage(), hideThinking, expandToolDetails));
        }
        return String.join("\n\n", lines);
    }

    private String renderStatus(AgentState state) {
        var lines = new ArrayList<String>();
        if (manualStatus != null && !manualStatus.isBlank()) {
            lines.add(manualStatus);
        } else if (state.error() != null && !state.error().isBlank()) {
            lines.add("Error: " + state.error());
        } else if (!state.pendingToolCalls().isEmpty()) {
            lines.add("Running tools: " + String.join(", ", state.pendingToolCalls()));
        } else if (state.isStreaming()) {
            lines.add("Streaming response...");
        } else {
            lines.add("Ready");
        }
        if (!pendingImages.isEmpty()) {
            lines.add("Attached images: " + pendingImages.size());
        }

        var queuedSteering = session.queuedSteeringMessages();
        var queuedFollowUps = session.queuedFollowUps();
        if (!queuedSteering.isEmpty() || !queuedFollowUps.isEmpty()) {
            for (var message : queuedSteering) {
                lines.add(truncateStatusLine(PiCliAnsi.muted("Steering: " + message)));
            }
            for (var message : queuedFollowUps) {
                lines.add(truncateStatusLine(PiCliAnsi.muted("Follow-up: " + message)));
            }
            lines.add(truncateStatusLine(PiCliAnsi.muted("↳ ") + PiCliKeyHints.appHint(PiAppAction.DEQUEUE, "to edit all queued messages")));
        }
        return String.join("\n", lines);
    }

    private String renderResources() {
        var sections = new ArrayList<String>();
        var settings = session.settingsSelection();
        if (!settings.quietStartup()) {
            var startupResources = session.startupResources();
            appendResourceSection(sections, "Context", startupResources.contextFiles(), true);
            appendResourceSection(sections, "Extensions", startupResources.extensionPaths(), true);
            appendResourceSection(sections, "Themes", startupResources.customThemes(), false);
        }
        if (reloadDiagnostics != null && !reloadDiagnostics.isBlank()) {
            sections.add(reloadDiagnostics);
        }
        return String.join("\n\n", sections);
    }

    private String renderFooter(AgentState state) {
        if (terminal.rows() < 14) {
            return "";
        }
        var width = Math.max(1, terminal.columns());
        var statsLine = renderFooterStatsLine(
            state,
            width,
            session.contextUsage(),
            session.autoCompactionEnabled(),
            session.availableProviderCount()
        );
        if (terminal.rows() < 15) {
            return statsLine;
        }
        var metaLine = renderFooterMetaLine(width, session.cwd(), session.sessionName());
        if (metaLine.isBlank()) {
            return statsLine;
        }
        return statsLine + "\n" + metaLine;
    }

    private static String renderFooterStatsLine(
        AgentState state,
        int width,
        PiInteractiveSession.ContextUsage contextUsage,
        boolean autoCompactionEnabled,
        int availableProviderCount
    ) {
        var footerStats = footerStats(state, contextUsage, autoCompactionEnabled);
        var plainLeft = footerStats.plain();
        if (plainLeft.isBlank()) {
            return renderFooterModelSummary(state, width, availableProviderCount);
        }

        var fullRight = footerModelSummary(state, Math.max(1, width - TerminalText.visibleWidth(plainLeft) - 2), availableProviderCount);
        var plainRight = fullRight.plain();
        var leftWidth = TerminalText.visibleWidth(plainLeft);
        var rightWidth = TerminalText.visibleWidth(plainRight);
        if (leftWidth + 2 + rightWidth <= width) {
            return footerStats.styled()
                + "  "
                + fullRight.styled();
        }
        var narrowRight = footerModelSummary(
            state,
            Math.min(Math.max(8, width / 3), Math.max(1, width - 4)),
            availableProviderCount
        );
        var reservedRightWidth = TerminalText.visibleWidth(narrowRight.plain());
        var availableLeftWidth = width - reservedRightWidth - 2;
        if (availableLeftWidth >= 4) {
            return PiCliAnsi.muted(TerminalText.truncateToWidth(plainLeft, availableLeftWidth, "..."))
                + "  "
                + narrowRight.plain();
        }
        return renderFooterModelSummary(state, width, availableProviderCount);
    }

    private static FooterStats footerStats(
        AgentState state,
        PiInteractiveSession.ContextUsage contextUsage,
        boolean autoCompactionEnabled
    ) {
        var usageSummary = footerUsageSummary(state);
        var contextSummary = footerContextSummary(contextUsage, autoCompactionEnabled);
        if (usageSummary.isBlank()) {
            return contextSummary == null
                ? new FooterStats("", "")
                : new FooterStats(contextSummary.plain(), contextSummary.styled());
        }
        if (contextSummary == null) {
            return new FooterStats(usageSummary, PiCliAnsi.muted(usageSummary));
        }
        return new FooterStats(
            usageSummary + " " + contextSummary.plain(),
            PiCliAnsi.muted(usageSummary) + " " + contextSummary.styled()
        );
    }

    private static String footerUsageSummary(AgentState state) {
        var totalInput = 0;
        var totalOutput = 0;
        var totalCacheRead = 0;
        var totalCacheWrite = 0;
        var totalCost = 0.0;
        for (var message : state.messages()) {
            if (!(message instanceof AgentMessage.AssistantMessage assistantMessage)) {
                continue;
            }
            totalInput += assistantMessage.usage().input();
            totalOutput += assistantMessage.usage().output();
            totalCacheRead += assistantMessage.usage().cacheRead();
            totalCacheWrite += assistantMessage.usage().cacheWrite();
            totalCost += assistantMessage.usage().cost().total();
        }

        var parts = new ArrayList<String>();
        if (totalInput > 0) {
            parts.add("↑" + formatTokens(totalInput));
        }
        if (totalOutput > 0) {
            parts.add("↓" + formatTokens(totalOutput));
        }
        if (totalCacheRead > 0) {
            parts.add("R" + formatTokens(totalCacheRead));
        }
        if (totalCacheWrite > 0) {
            parts.add("W" + formatTokens(totalCacheWrite));
        }
        if (!parts.isEmpty() || totalCost > 0.0) {
            parts.add("$" + String.format(Locale.ROOT, "%.3f", totalCost));
        }
        return String.join(" ", parts);
    }

    private static FooterSegment footerContextSummary(
        PiInteractiveSession.ContextUsage contextUsage,
        boolean autoCompactionEnabled
    ) {
        if (contextUsage == null || contextUsage.contextWindow() <= 0) {
            return null;
        }

        var plain = contextUsage.percent() == null
            ? "?/" + formatTokens(contextUsage.contextWindow())
            : String.format(
                Locale.ROOT,
                "%.1f%%/%s",
                contextUsage.percent(),
                formatTokens(contextUsage.contextWindow())
            );
        if (autoCompactionEnabled) {
            plain += " (auto)";
        }
        if (contextUsage.percent() != null && contextUsage.percent() > 90.0) {
            return new FooterSegment(plain, PiCliAnsi.error(plain));
        }
        if (contextUsage.percent() != null && contextUsage.percent() > 70.0) {
            return new FooterSegment(plain, PiCliAnsi.warning(plain));
        }
        return new FooterSegment(plain, PiCliAnsi.muted(plain));
    }

    private static String renderFooterMetaLine(int width, String cwd, String sessionName) {
        var parts = new ArrayList<String>();
        var normalizedCwd = shortenHomePath(cwd);
        if (normalizedCwd != null && !normalizedCwd.isBlank()) {
            var branch = PiGitBranchResolver.resolve(cwd);
            parts.add(branch == null || branch.isBlank() ? normalizedCwd : normalizedCwd + " (" + branch + ")");
        }
        if (sessionName != null && !sessionName.isBlank()) {
            parts.add(sessionName.trim());
        }
        if (parts.isEmpty()) {
            return "";
        }
        return PiCliAnsi.muted(TerminalText.truncateMiddleToWidth(String.join(" • ", parts), width, "..."));
    }

    private static String footerModelSummary(AgentState state) {
        var model = state.model();
        if (model.reasoning()) {
            var thinkingLevel = state.thinkingLevel() == null ? "thinking off" : state.thinkingLevel().value();
            return model.id() + " • " + thinkingLevel;
        }
        return model.id();
    }

    private static String renderFooterModelSummary(AgentState state, int width, int availableProviderCount) {
        return footerModelSummary(state, width, availableProviderCount).styled();
    }

    private static FooterModelSummary footerModelSummary(AgentState state, int width, int availableProviderCount) {
        if (width <= 0) {
            return new FooterModelSummary("", "");
        }
        var providerPrefix = availableProviderCount > 1
            && state.model().provider() != null
            && !state.model().provider().isBlank()
            ? "(" + state.model().provider() + ") "
            : "";
        var modelLabel = state.model().id();
        var providerWidth = TerminalText.visibleWidth(providerPrefix);
        var modelWidth = TerminalText.visibleWidth(modelLabel);
        var includeProvider = !providerPrefix.isBlank() && providerWidth + modelWidth <= width;
        if (!includeProvider) {
            providerPrefix = "";
            providerWidth = 0;
        }

        if (!state.model().reasoning()) {
            if (providerWidth + modelWidth <= width) {
                return new FooterModelSummary(
                    providerPrefix + modelLabel,
                    PiCliAnsi.muted(providerPrefix) + PiCliAnsi.bold(modelLabel)
                );
            }
            var truncated = TerminalText.truncateToWidth(modelLabel, width, "...");
            return new FooterModelSummary(truncated, PiCliAnsi.bold(truncated));
        }

        var suffix = " • " + (state.thinkingLevel() == null ? "thinking off" : state.thinkingLevel().value());
        var suffixWidth = TerminalText.visibleWidth(suffix);
        if (providerWidth + modelWidth + suffixWidth <= width) {
            return new FooterModelSummary(
                providerPrefix + modelLabel + suffix,
                PiCliAnsi.muted(providerPrefix) + PiCliAnsi.bold(modelLabel) + PiCliAnsi.muted(suffix)
            );
        }
        if (providerWidth + modelWidth >= width) {
            var truncated = TerminalText.truncateToWidth(modelLabel, width, "...");
            return new FooterModelSummary(truncated, PiCliAnsi.bold(truncated));
        }
        var truncatedSuffix = TerminalText.truncateToWidth(suffix, width - providerWidth - modelWidth, "...");
        return new FooterModelSummary(
            providerPrefix + modelLabel + truncatedSuffix,
            PiCliAnsi.muted(providerPrefix) + PiCliAnsi.bold(modelLabel) + PiCliAnsi.muted(truncatedSuffix)
        );
    }

    private static String formatTokens(int count) {
        if (count < 1_000) {
            return Integer.toString(count);
        }
        if (count < 10_000) {
            return String.format(Locale.ROOT, "%.1fk", count / 1_000.0);
        }
        if (count < 1_000_000) {
            return Math.round(count / 1_000.0) + "k";
        }
        if (count < 10_000_000) {
            return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
        }
        return Math.round(count / 1_000_000.0) + "M";
    }

    private static String shortenHomePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        var home = System.getProperty("user.home");
        if (home != null && !home.isBlank() && path.startsWith(home)) {
            return "~" + path.substring(home.length());
        }
        return path;
    }

    private record FooterStats(String plain, String styled) {}

    private record FooterSegment(String plain, String styled) {}

    private record FooterModelSummary(String plain, String styled) {}

    private void handleCopyCommand() {
        try {
            copyCommand.copyLastAssistantMessage();
            manualStatus = "Copied last agent message to clipboard";
        } catch (RuntimeException exception) {
            var message = rootMessage(exception);
            manualStatus = "No agent messages to copy yet.".equals(message)
                ? message
                : "Error: " + message;
        }
        renderState(session.state());
    }

    private void handleTreeCommand() {
        var tree = session.tree();
        if (tree.isEmpty()) {
            manualStatus = "No entries in session";
            renderState(session.state());
            return;
        }

        var overlayRef = new AtomicReference<dev.pi.tui.OverlayHandle>();
        var selector = new PiTreeSelector(
            tree,
            session.leafId(),
            targetId -> selectTreeEntry(targetId, overlayRef.get()),
            () -> {
                var overlay = overlayRef.get();
                if (overlay != null) {
                    overlay.hide();
                }
            },
            tui::requestRender
        );
        var width = Math.max(40, Math.min(100, terminal.columns() - 2));
        var maxHeight = Math.max(8, Math.floorDiv(terminal.rows(), 2));
        overlayRef.set(tui.showOverlay(
            selector,
            new OverlayOptions(
                width,
                40,
                maxHeight,
                OverlayAnchor.CENTER,
                0,
                0,
                null,
                null,
                OverlayMargin.uniform(1)
            )
        ));
    }

    private void handleForkCommand() {
        var forkMessages = session.forkMessages();
        if (forkMessages.isEmpty()) {
            manualStatus = "No messages to fork from";
            renderState(session.state());
            return;
        }

        var overlayRef = new AtomicReference<dev.pi.tui.OverlayHandle>();
        var selector = new PiForkSelector(
            forkMessages,
            entryId -> selectForkEntry(entryId, overlayRef.get()),
            () -> {
                var overlay = overlayRef.get();
                if (overlay != null) {
                    overlay.hide();
                }
            },
            tui::requestRender
        );
        var width = Math.max(40, Math.min(100, terminal.columns() - 2));
        var maxHeight = Math.max(8, Math.floorDiv(terminal.rows(), 2));
        overlayRef.set(tui.showOverlay(
            selector,
            new OverlayOptions(
                width,
                40,
                maxHeight,
                OverlayAnchor.CENTER,
                0,
                0,
                null,
                null,
                OverlayMargin.uniform(1)
            )
        ));
    }

    private void handleSelectModelCommand() {
        handleSelectModelCommand(null);
    }

    private void handleModelCommand(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            handleSelectModelCommand(null);
            return;
        }
        var exactMatch = findExactModelMatch(session.modelSelection(), searchTerm);
        if (exactMatch != null) {
            selectModelEntry(exactMatch.index(), null);
            return;
        }
        handleSelectModelCommand(searchTerm);
    }

    private void handleSelectModelCommand(String initialSearchInput) {
        var selection = session.modelSelection();
        if (selection.allModels().isEmpty() && selection.scopedModels().isEmpty()) {
            manualStatus = "No models available";
            renderState(session.state());
            return;
        }
        if (selection.allModels().size() <= 1 && selection.scopedModels().size() <= 1) {
            manualStatus = "Only one model available";
            renderState(session.state());
            return;
        }

        var overlayRef = new AtomicReference<dev.pi.tui.OverlayHandle>();
        var selector = new PiModelSelector(
            selection,
            initialSearchInput,
            index -> selectModelEntry(index, overlayRef.get()),
            () -> {
                var overlay = overlayRef.get();
                if (overlay != null) {
                    overlay.hide();
                }
            },
            tui::requestRender
        );
        var width = Math.max(40, Math.min(100, terminal.columns() - 2));
        var maxHeight = Math.max(8, Math.floorDiv(terminal.rows(), 2));
        overlayRef.set(tui.showOverlay(
            selector,
            new OverlayOptions(
                width,
                40,
                maxHeight,
                OverlayAnchor.CENTER,
                0,
                0,
                null,
                null,
                OverlayMargin.uniform(1)
            )
        ));
    }

    private static PiInteractiveSession.SelectableModel findExactModelMatch(
        PiInteractiveSession.ModelSelection selection,
        String searchTerm
    ) {
        var term = searchTerm == null ? "" : searchTerm.trim().toLowerCase(Locale.ROOT);
        if (term.isBlank()) {
            return null;
        }
        String targetProvider = null;
        var targetModelId = term;
        var slashIndex = term.indexOf('/');
        if (slashIndex >= 0) {
            targetProvider = term.substring(0, slashIndex).trim();
            targetModelId = term.substring(slashIndex + 1).trim();
        }
        if (targetModelId.isBlank()) {
            return null;
        }
        var candidates = selection.scopedModels().isEmpty() ? selection.allModels() : selection.scopedModels();
        PiInteractiveSession.SelectableModel match = null;
        for (var candidate : candidates) {
            var idMatches = candidate.modelId().toLowerCase(Locale.ROOT).equals(targetModelId);
            var providerMatches = targetProvider == null || candidate.provider().toLowerCase(Locale.ROOT).equals(targetProvider);
            if (!idMatches || !providerMatches) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = candidate;
        }
        return match;
    }

    private void handleSettingsCommand() {
        var overlayRef = new AtomicReference<dev.pi.tui.OverlayHandle>();
        var selector = new PiSettingsSelector(
            session.settingsSelection(),
            (settingId, value) -> {
                previewTheme = null;
                session.updateSetting(settingId, value);
                manualStatus = null;
                renderState(session.state());
            },
            () -> {
                previewTheme = null;
                var overlay = overlayRef.get();
                if (overlay != null) {
                    overlay.hide();
                }
                renderState(session.state());
            },
            (settingId, value) -> {
                if (!"theme".equals(settingId)) {
                    return;
                }
                previewTheme = value;
                renderState(session.state());
            }
        );
        var width = Math.max(48, Math.min(96, terminal.columns() - 2));
        var maxHeight = Math.max(10, Math.floorDiv(terminal.rows(), 2));
        overlayRef.set(tui.showOverlay(
            selector,
            new OverlayOptions(
                width,
                48,
                maxHeight,
                OverlayAnchor.CENTER,
                0,
                0,
                null,
                null,
                OverlayMargin.uniform(1)
            )
        ));
    }

    private void selectTreeEntry(String targetId, dev.pi.tui.OverlayHandle overlay) {
        if (Objects.equals(targetId, session.leafId())) {
            manualStatus = "Already at this point";
            if (overlay != null) {
                overlay.hide();
            }
            renderState(session.state());
            return;
        }
        try {
            var result = session.navigateTree(targetId);
            input.setValue(result.editorText() == null ? "" : result.editorText());
            manualStatus = "Navigated to selected point";
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        if (overlay != null) {
            overlay.hide();
        }
        renderState(session.state());
    }

    private void selectForkEntry(String entryId, dev.pi.tui.OverlayHandle overlay) {
        try {
            var result = session.fork(entryId);
            input.setValue(result.selectedText() == null ? "" : result.selectedText());
            manualStatus = "Branched to new session";
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        if (overlay != null) {
            overlay.hide();
        }
        renderState(session.state());
    }

    private void selectModelEntry(int index, dev.pi.tui.OverlayHandle overlay) {
        try {
            var result = session.selectModel(index);
            manualStatus = formatModelSelectionStatus(result);
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        if (overlay != null) {
            overlay.hide();
        }
        renderState(session.state());
    }

    private void handleCompactCommand(String customInstructions) {
        if (session.state().messages().size() < 2) {
            manualStatus = "Nothing to compact (no messages yet)";
            renderState(session.state());
            return;
        }
        try {
            session.compact(customInstructions);
            manualStatus = null;
        } catch (RuntimeException exception) {
            var message = rootMessage(exception);
            manualStatus = "Compaction cancelled".equals(message)
                ? "Compaction cancelled"
                : "Compaction failed: " + message;
        }
        renderState(session.state());
    }

    private void handleReloadCommand() {
        if (session.state().isStreaming()) {
            manualStatus = "Wait for the current response to finish before reloading.";
            renderState(session.state());
            return;
        }
        try {
            var result = session.reload();
            reloadDiagnostics = formatReloadDiagnostics(result);
            manualStatus = "Reloaded extensions, skills, prompts, themes";
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private void handleNameCommand(String text) {
        var name = text == null ? "" : text.replaceFirst("^/name\\s*", "").trim();
        if (name.isBlank()) {
            var currentName = session.sessionName();
            manualStatus = currentName == null || currentName.isBlank()
                ? "Usage: /name <name>"
                : "Session name: " + currentName.trim();
            renderState(session.state());
            return;
        }
        try {
            manualStatus = "Session name set: " + session.setSessionName(name);
            updateTerminalTitle();
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private void handleDebugCommand() {
        try {
            var debugLogPath = debugLogPath();
            var content = debugLogContent();
            Files.createDirectories(debugLogPath.getParent());
            Files.writeString(debugLogPath, content, StandardCharsets.UTF_8);
            manualStatus = "Debug log written: " + debugLogPath;
        } catch (IOException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private void requestExit() {
        close();
    }

    private static String rootMessage(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private void updateTerminalTitle() {
        var cwdName = terminalTitleCwd(session.cwd());
        var sessionName = session.sessionName();
        if (sessionName != null && !sessionName.isBlank()) {
            terminal.setTitle("pi-java - " + sessionName.trim() + " - " + cwdName);
            return;
        }
        terminal.setTitle("pi-java - " + cwdName);
    }

    private Path debugLogPath() {
        return Path.of(System.getProperty("user.home"), ".pi", "agent", "debug.log");
    }

    private String debugLogContent() {
        var width = terminal.columns();
        var height = terminal.rows();
        var allLines = tui.render(width);
        var lines = new ArrayList<String>();
        lines.add("Debug output");
        lines.add("Terminal: " + width + "x" + height);
        lines.add("Total lines: " + allLines.size());
        lines.add("");
        lines.add("=== All rendered lines with visible widths ===");
        for (var index = 0; index < allLines.size(); index += 1) {
            var line = allLines.get(index);
            lines.add("[" + index + "] (w=" + TerminalText.visibleWidth(line) + ") " + line);
        }
        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }

    private String startupStatus() {
        var compactionCount = compactionCount(session.tree());
        if (compactionCount <= 0) {
            return null;
        }
        return "Session compacted " + (compactionCount == 1 ? "1 time" : compactionCount + " times");
    }

    private static int compactionCount(List<dev.pi.session.SessionTreeNode> nodes) {
        var count = 0;
        for (var node : nodes) {
            if (node.entry() instanceof dev.pi.session.SessionEntry.CompactionEntry) {
                count += 1;
            }
            count += compactionCount(node.children());
        }
        return count;
    }

    private final class PromptInput implements Component, Focusable {
        @Override
        public List<String> render(int width) {
            return input.render(width);
        }

        @Override
        public void handleInput(String data) {
            var appKeybindings = PiAppKeybindings.global();
            if (appKeybindings.matches(data, PiAppAction.INTERRUPT)) {
                handleInterruptCommand(data);
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.CLEAR)) {
                handleClearCommand();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.EXIT) && input.getValue().isEmpty()) {
                requestExit();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.SUSPEND)) {
                handleSuspendCommand();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.EXTERNAL_EDITOR)) {
                handleExternalEditorCommand();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.RESUME)) {
                handleResumeCommand();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.CYCLE_MODEL_FORWARD)) {
                handleCycleModelForwardCommand();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.CYCLE_MODEL_BACKWARD)) {
                handleCycleModelBackwardCommand();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.CYCLE_THINKING_LEVEL)) {
                handleCycleThinkingLevelCommand();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.SELECT_MODEL)) {
                handleSelectModelCommand();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.EXPAND_TOOLS)) {
                handleToggleToolDetailsCommand();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.PASTE_IMAGE)) {
                handlePasteImageCommand();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.TOGGLE_THINKING)) {
                handleToggleThinkingCommand();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.FOLLOW_UP)) {
                handleFollowUpCommand();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.DEQUEUE)) {
                handleDequeueCommand();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.NEW_SESSION)) {
                handleNewSessionCommand();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.TREE)) {
                handleTreeCommand();
                return;
            }
            if (appKeybindings.matches(data, PiAppAction.FORK)) {
                handleForkCommand();
                return;
            }
            input.handleInput(data);
        }

        @Override
        public boolean isFocused() {
            return input.isFocused();
        }

        @Override
        public void setFocused(boolean focused) {
            input.setFocused(focused);
        }

        @Override
        public void invalidate() {
            input.invalidate();
        }
    }

    private void handleResumeCommand() {
        try {
            session.resume().toCompletableFuture().join();
            manualStatus = "Resumed session";
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private void handleSuspendCommand() {
        try {
            suspendAction.run();
            manualStatus = null;
        } catch (UnsupportedOperationException exception) {
            manualStatus = exception.getMessage();
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private void handleExternalEditorCommand() {
        try {
            var editedText = externalEditor.edit(input.getValue());
            if (editedText != null) {
                var normalizedText = normalizeExternalEditorText(editedText);
                replaceInputValue(normalizedText);
            } else {
                manualStatus = null;
            }
        } catch (UnsupportedOperationException exception) {
            manualStatus = exception.getMessage();
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private void handleClearCommand() {
        var now = System.currentTimeMillis();
        if (now - lastClearTimeMillis < 500) {
            requestExit();
            return;
        }
        input.setValue("");
        pendingImages.clear();
        lastClearTimeMillis = now;
        tui.requestRender();
    }

    private void handleInterruptCommand(String data) {
        if (session.state().isStreaming()) {
            session.abort();
            return;
        }
        if (!KeyMatcher.matches(data, "escape") || !input.getValue().trim().isEmpty()) {
            session.abort();
            return;
        }

        var action = session.settingsSelection().doubleEscapeAction();
        if ("none".equals(action)) {
            return;
        }

        var now = System.currentTimeMillis();
        if (now - lastEscapeTimeMillis >= 500) {
            lastEscapeTimeMillis = now;
            return;
        }
        lastEscapeTimeMillis = 0;
        if ("tree".equals(action)) {
            handleTreeCommand();
            return;
        }
        handleForkCommand();
    }

    private void handleCycleModelForwardCommand() {
        try {
            var result = session.cycleModelForward();
            manualStatus = result == null ? onlyOneModelMessage() : formatModelCycleStatus(result);
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private void handleCycleModelBackwardCommand() {
        try {
            var result = session.cycleModelBackward();
            manualStatus = result == null ? onlyOneModelMessage() : formatModelCycleStatus(result);
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private void handleCycleThinkingLevelCommand() {
        try {
            manualStatus = "Thinking level: " + session.cycleThinkingLevel();
        } catch (UnsupportedOperationException exception) {
            manualStatus = "Current model does not support thinking";
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private void handleToggleThinkingCommand() {
        var hidden = !session.settingsSelection().hideThinkingBlock();
        try {
            session.updateSetting("hide-thinking", hidden ? "true" : "false");
            manualStatus = "Thinking blocks: " + (hidden ? "hidden" : "visible");
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private void handleToggleToolDetailsCommand() {
        expandToolDetails = !expandToolDetails;
        renderState(session.state());
    }

    private void handleNewSessionCommand() {
        try {
            session.newSession();
            input.setValue("");
            pendingImages.clear();
            updateTerminalTitle();
            manualStatus = "New session started";
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private void handleFollowUpCommand() {
        var text = input.getValue();
        if (!session.state().isStreaming()) {
            submit(text);
            return;
        }
        if (!pendingImages.isEmpty()) {
            manualStatus = "Error: Image attachments are not supported in queued follow-ups";
            renderState(session.state());
            return;
        }
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            session.followUp(text).toCompletableFuture().join();
            input.setValue("");
            manualStatus = null;
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private void handlePasteImageCommand() {
        try {
            var image = clipboardImage.read();
            if (image == null) {
                return;
            }
            pendingImages.add(image);
            manualStatus = null;
        } catch (RuntimeException exception) {
            return;
        }
        renderState(session.state());
    }

    private void handleSteerCommand(String text) {
        try {
            session.steer(text).toCompletableFuture().join();
            input.setValue("");
            manualStatus = null;
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private void handleDequeueCommand() {
        try {
            var result = session.dequeue();
            if (result.restoredCount() == 0 || result.editorText() == null || result.editorText().isBlank()) {
                manualStatus = "No queued messages to restore";
                renderState(session.state());
                return;
            }
            var currentText = input.getValue();
            var combined = result.editorText();
            if (currentText != null && !currentText.isBlank()) {
                combined = combined + "\n\n" + currentText;
            }
            input.setValue(combined);
            manualStatus = result.restoredCount() == 1
                ? "Restored 1 queued message to editor"
                : "Restored %d queued messages to editor".formatted(result.restoredCount());
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private static String renderHeaderHintLine(int width, String... segments) {
        return TerminalText.truncateToWidth(
            String.join(PiCliAnsi.muted(" \u2022 "), java.util.List.of(segments)),
            Math.max(1, width),
            "..."
        );
    }

    private String truncateStatusLine(String text) {
        return TerminalText.truncateToWidth(text, Math.max(1, terminal.columns() - 2), "...");
    }

    private void appendResourceSection(
        List<String> sections,
        String title,
        List<String> items,
        boolean pathLike
    ) {
        var visibleItems = items.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .map(item -> pathLike ? shortenHomePath(item) : item)
            .toList();
        if (visibleItems.isEmpty()) {
            return;
        }
        var lines = new ArrayList<String>();
        lines.add(PiCliAnsi.bold("[" + title + "]"));
        for (var item : visibleItems) {
            lines.add(PiCliAnsi.muted("  " + item));
        }
        sections.add(String.join("\n", lines));
    }

    private String formatReloadDiagnostics(PiInteractiveSession.ReloadResult result) {
        var lines = new ArrayList<String>();
        for (var error : result.settingsErrors()) {
            lines.add("  " + error.scope().name().toLowerCase(Locale.ROOT) + " settings: " + rootMessage(error.error()));
        }
        for (var error : result.resourceErrors()) {
            lines.add("  " + shortenHomePath(error.path().toString()) + ": " + rootMessage(error.error()));
        }
        for (var warning : result.themeWarnings()) {
            lines.add("  theme: " + warning);
        }
        for (var warning : result.extensionWarnings()) {
            lines.add("  extension: " + warning);
        }
        if (lines.isEmpty()) {
            return "";
        }
        lines.addFirst(PiCliAnsi.warning("[Reload warnings]"));
        for (var index = 1; index < lines.size(); index += 1) {
            lines.set(index, PiCliAnsi.warning(lines.get(index)));
        }
        return String.join("\n", lines);
    }

    private void replaceInputValue(String value) {
        input.setValue("");
        if (value != null && !value.isEmpty()) {
            input.handleInput(value);
        }
    }

    private static String appKeyDisplay(PiAppAction action, String fallback) {
        var keys = PiAppKeybindings.global().getKeys(action);
        return keyDisplay(keys, fallback);
    }

    private static String keyDisplay(List<String> keys, String fallback) {
        return keys.isEmpty() ? fallback : String.join("/", keys);
    }

    private static String formatModelCycleStatus(PiInteractiveSession.ModelCycleResult result) {
        var summary = "Switched to " + result.modelName();
        if (!"off".equals(result.thinkingLevel())) {
            return summary + " (thinking: " + result.thinkingLevel() + ")";
        }
        return summary;
    }

    private static String formatModelSelectionStatus(PiInteractiveSession.ModelCycleResult result) {
        return "Model: " + result.modelId();
    }

    private String onlyOneModelMessage() {
        return session.modelSelection().scopedModels().isEmpty()
            ? "Only one model available"
            : "Only one model in scope";
    }

    private Runnable createSuspendAction() {
        return () -> {
            if (isWindows()) {
                throw new UnsupportedOperationException("Suspend is not supported on Windows");
            }
            tui.stop();
            try {
                var process = new ProcessBuilder(
                    "kill",
                    "-TSTP",
                    Long.toString(ProcessHandle.current().pid())
                ).inheritIO().start();
                var exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new IllegalStateException("Failed to suspend process");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Failed to suspend process", exception);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Failed to suspend process", exception);
            } finally {
                tui.start();
            }
        };
    }

    private AgentMessage.UserMessage buildPrompt(String value) {
        var content = new ArrayList<UserContent>();
        if (value != null && !value.isBlank()) {
            content.add(new TextContent(value, null));
        }
        content.addAll(pendingImages);
        return new AgentMessage.UserMessage(List.copyOf(content), System.currentTimeMillis());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("win");
    }

    private static String normalizeExternalEditorText(String text) {
        return stripTrailingSingleLineBreak(text)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\n', ' ');
    }

    private static String stripTrailingSingleLineBreak(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.endsWith("\r\n")) {
            return text.substring(0, text.length() - 2);
        }
        if (text.endsWith("\n") || text.endsWith("\r")) {
            return text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static String terminalTitleCwd(String cwd) {
        var normalized = cwd == null ? "" : cwd.trim().replace('\\', '/');
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            return "interactive";
        }
        var slashIndex = normalized.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == normalized.length() - 1) {
            return normalized;
        }
        return normalized.substring(slashIndex + 1);
    }
}
