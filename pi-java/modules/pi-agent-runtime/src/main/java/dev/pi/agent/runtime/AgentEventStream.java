package dev.pi.agent.runtime;

import dev.pi.ai.stream.EventStream;
import java.util.List;

public final class AgentEventStream extends EventStream<AgentEvent, List<AgentMessage>> {
    public AgentEventStream() {
        super(
            event -> event instanceof AgentEvent.AgentEnd,
            event -> ((AgentEvent.AgentEnd) event).messages()
        );
    }
}
