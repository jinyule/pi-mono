package dev.pi.cli;

import dev.pi.agent.runtime.AgentState;
import dev.pi.ai.stream.Subscription;
import dev.pi.tui.Input;
import dev.pi.tui.ProcessTerminal;
import dev.pi.tui.Terminal;
import dev.pi.tui.Text;
import dev.pi.tui.Tui;
import java.util.ArrayList;
import java.util.Objects;

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
        if ("/copy".equals(value.trim())) {
            input.setValue("");
            handleCopyCommand();
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

    private static String rootMessage(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
