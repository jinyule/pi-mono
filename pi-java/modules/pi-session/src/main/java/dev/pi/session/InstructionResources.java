package dev.pi.session;

import java.util.List;
import java.util.Objects;

public record InstructionResources(
    List<InstructionFile> contextFiles,
    String systemPrompt,
    List<String> appendSystemPrompts
) {
    public InstructionResources {
        contextFiles = List.copyOf(Objects.requireNonNull(contextFiles, "contextFiles"));
        appendSystemPrompts = List.copyOf(Objects.requireNonNull(appendSystemPrompts, "appendSystemPrompts"));
    }

    public static InstructionResources empty() {
        return new InstructionResources(List.of(), null, List.of());
    }
}
