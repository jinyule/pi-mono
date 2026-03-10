package dev.pi.cli;

import dev.pi.agent.runtime.AgentMessage;
import java.util.Objects;

public final class PiCopyCommand {
    private final PiInteractiveSession session;
    private final PiClipboard clipboard;

    public PiCopyCommand(PiInteractiveSession session, PiClipboard clipboard) {
        this.session = Objects.requireNonNull(session, "session");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
    }

    public String copyLastAssistantMessage() {
        var assistant = findLastAssistantMessage();
        if (assistant == null) {
            throw new IllegalStateException("No agent messages to copy yet.");
        }

        var text = PiMessageRenderer.renderAssistantContent(assistant.content());
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Last agent message has no copyable text.");
        }

        clipboard.copy(text);
        return text;
    }

    private AgentMessage.AssistantMessage findLastAssistantMessage() {
        var messages = session.state().messages();
        for (int index = messages.size() - 1; index >= 0; index -= 1) {
            if (messages.get(index) instanceof AgentMessage.AssistantMessage assistantMessage) {
                return assistantMessage;
            }
        }
        return null;
    }
}
