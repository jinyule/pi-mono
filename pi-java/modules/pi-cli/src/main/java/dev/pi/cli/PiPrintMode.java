package dev.pi.cli;

import dev.pi.agent.runtime.AgentMessage;
import dev.pi.ai.model.StopReason;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class PiPrintMode {
    private final PiInteractiveSession session;
    private final Appendable stdout;
    private final Appendable stderr;

    public PiPrintMode(PiInteractiveSession session, Appendable stdout) {
        this(session, stdout, new StringBuilder());
    }

    public PiPrintMode(PiInteractiveSession session, Appendable stdout, Appendable stderr) {
        this.session = Objects.requireNonNull(session, "session");
        this.stdout = Objects.requireNonNull(stdout, "stdout");
        this.stderr = Objects.requireNonNull(stderr, "stderr");
    }

    public CompletionStage<RunResult> run(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Print mode requires a non-blank prompt");
        }
        return session.prompt(prompt)
            .thenCompose(ignored -> session.waitForIdle())
            .thenApply(ignored -> emitLastAssistantMessage());
    }

    private RunResult emitLastAssistantMessage() {
        var assistant = findLastAssistant();
        if (assistant == null) {
            throw new IllegalStateException("Print mode did not receive an assistant response");
        }

        var stopReason = assistant.stopReason();
        if (stopReason == StopReason.ERROR || stopReason == StopReason.ABORTED) {
            var errorText = assistant.errorMessage() == null || assistant.errorMessage().isBlank()
                ? stopReason.value()
                : assistant.errorMessage();
            appendLine(stderr, errorText);
            return new RunResult(false, null, errorText);
        }

        var outputText = PiMessageRenderer.renderAssistantContent(assistant.content());
        appendLine(stdout, outputText);
        return new RunResult(true, outputText, null);
    }

    private AgentMessage.AssistantMessage findLastAssistant() {
        var messages = session.state().messages();
        for (int index = messages.size() - 1; index >= 0; index -= 1) {
            if (messages.get(index) instanceof AgentMessage.AssistantMessage assistantMessage) {
                return assistantMessage;
            }
        }
        return null;
    }

    private static void appendLine(Appendable appendable, String text) {
        try {
            appendable.append(text == null ? "" : text);
            appendable.append(System.lineSeparator());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write print-mode output", exception);
        }
    }

    public record RunResult(
        boolean success,
        String output,
        String error
    ) {
    }
}
