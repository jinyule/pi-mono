package dev.pi.cli;

import dev.pi.agent.runtime.AgentState;
import dev.pi.ai.stream.Subscription;
import dev.pi.tui.OverlayAnchor;
import dev.pi.tui.OverlayMargin;
import dev.pi.tui.OverlayOptions;
import dev.pi.tui.Input;
import dev.pi.tui.ProcessTerminal;
import dev.pi.tui.Terminal;
import dev.pi.tui.Text;
import dev.pi.tui.Tui;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class PiInteractiveMode implements AutoCloseable {
    private final PiInteractiveSession session;
    private final Terminal terminal;
    private final Tui tui;
    private final PiCopyCommand copyCommand;
    private final Text header = new Text("", 1, 1, null);
    private final Text transcript = new Text("", 1, 0, null);
    private final Text status = new Text("", 1, 0, null);
    private final Input input = new Input();

    private Subscription stateSubscription;
    private boolean started;
    private String manualStatus;

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
            )
        );
    }

    PiInteractiveMode(PiInteractiveSession session, Terminal terminal, PiCopyCommand copyCommand) {
        this.session = Objects.requireNonNull(session, "session");
        this.terminal = Objects.requireNonNull(terminal, "terminal");
        this.copyCommand = Objects.requireNonNull(copyCommand, "copyCommand");
        this.tui = new Tui(terminal, true);
        this.tui.addChild(header);
        this.tui.addChild(transcript);
        this.tui.addChild(status);
        this.tui.addChild(input);
        this.tui.setFocus(input);
        this.input.setOnSubmit(this::submit);
        this.input.setOnEscape(session::abort);
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        terminal.setTitle("pi-java interactive");
        stateSubscription = session.subscribeState(this::renderState);
        tui.start();
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
        tui.stop();
    }

    @Override
    public void close() {
        stop();
    }

    private void submit(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        var trimmed = value.trim();
        if ("/copy".equals(trimmed)) {
            input.setValue("");
            handleCopyCommand();
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
        manualStatus = null;
        input.setValue("");
        tui.requestRender();
        try {
            session.prompt(value);
        } catch (RuntimeException exception) {
            renderState(session.state().withError(exception.getMessage()));
        }
    }

    private void renderState(AgentState state) {
        header.setText(renderHeader(state));
        transcript.setText(renderTranscript(state));
        status.setText(renderStatus(state));
        tui.requestRender();
    }

    private String renderHeader(AgentState state) {
        return """
            pi-java interactive
            model: %s/%s
            session: %s
            """.formatted(
            state.model().provider(),
            state.model().id(),
            session.sessionId()
        ).trim();
    }

    private String renderTranscript(AgentState state) {
        var lines = new ArrayList<String>();
        for (var message : state.messages()) {
            lines.add(PiMessageRenderer.renderMessage(message));
        }
        if (state.isStreaming() && state.streamMessage() != null) {
            lines.add(PiMessageRenderer.renderStreamingMessage(state.streamMessage()));
        }
        return String.join("\n\n", lines);
    }

    private String renderStatus(AgentState state) {
        if (manualStatus != null && !manualStatus.isBlank()) {
            return manualStatus;
        }
        if (state.error() != null && !state.error().isBlank()) {
            return "Error: " + state.error();
        }
        if (!state.pendingToolCalls().isEmpty()) {
            return "Running tools: " + String.join(", ", state.pendingToolCalls());
        }
        if (state.isStreaming()) {
            return "Streaming response...";
        }
        return "Ready";
    }

    private void handleCopyCommand() {
        try {
            copyCommand.copyLastAssistantMessage();
            manualStatus = "Copied last agent message to clipboard";
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private void handleTreeCommand() {
        var tree = session.tree();
        if (tree.isEmpty()) {
            manualStatus = "No session history to navigate yet.";
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

    private void selectTreeEntry(String targetId, dev.pi.tui.OverlayHandle overlay) {
        try {
            var result = session.navigateTree(targetId);
            input.setValue(result.editorText() == null ? "" : result.editorText());
            manualStatus = "Moved to selected tree entry";
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
            manualStatus = "Forked to new session";
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        if (overlay != null) {
            overlay.hide();
        }
        renderState(session.state());
    }

    private void handleCompactCommand(String customInstructions) {
        try {
            session.compact(customInstructions);
            manualStatus = "Compacted context";
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private void handleReloadCommand() {
        try {
            var result = session.reload();
            var warningCount = result.settingsErrors().size() + result.resourceErrors().size() + result.extensionWarnings().size();
            manualStatus = warningCount == 0
                ? "Reloaded settings, instruction resources, and extensions"
                : "Reloaded with %d warning%s".formatted(warningCount, warningCount == 1 ? "" : "s");
        } catch (RuntimeException exception) {
            manualStatus = "Error: " + rootMessage(exception);
        }
        renderState(session.state());
    }

    private static String rootMessage(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
