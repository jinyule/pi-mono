package dev.pi.ai.stream;

import dev.pi.ai.model.AssistantMessageEvent;
import dev.pi.ai.model.Message;

public final class AssistantMessageEventStream extends EventStream<AssistantMessageEvent, Message.AssistantMessage> {
    public AssistantMessageEventStream() {
        super(
            event -> event instanceof AssistantMessageEvent.Done || event instanceof AssistantMessageEvent.Error,
            event -> switch (event) {
                case AssistantMessageEvent.Done done -> done.message();
                case AssistantMessageEvent.Error error -> error.error();
                default -> throw new IllegalStateException("Unexpected terminal event: " + event.type());
            }
        );
    }
}

