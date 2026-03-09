package dev.pi.ai.provider;

import dev.pi.ai.model.Context;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.SimpleStreamOptions;
import dev.pi.ai.model.StreamOptions;
import dev.pi.ai.stream.AssistantMessageEventStream;
import java.util.Objects;

public interface ApiProvider {
    String api();

    AssistantMessageEventStream stream(
        Model model,
        Context context,
        StreamOptions options
    );

    default AssistantMessageEventStream streamSimple(
        Model model,
        Context context,
        SimpleStreamOptions options
    ) {
        return stream(
            Objects.requireNonNull(model, "model"),
            Objects.requireNonNull(context, "context"),
            Objects.requireNonNull(options, "options")
        );
    }
}
