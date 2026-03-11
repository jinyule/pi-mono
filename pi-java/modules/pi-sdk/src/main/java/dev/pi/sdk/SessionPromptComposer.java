package dev.pi.sdk;

import dev.pi.session.InstructionFile;
import dev.pi.session.InstructionResources;
import java.util.ArrayList;

final class SessionPromptComposer {
    private SessionPromptComposer() {}

    static String compose(
        String explicitSystemPrompt,
        String appendSystemPrompt,
        InstructionResources instructionResources
    ) {
        var resources = instructionResources == null ? InstructionResources.empty() : instructionResources;
        var sections = new ArrayList<String>();
        var baseSystemPrompt = explicitSystemPrompt != null && !explicitSystemPrompt.isBlank()
            ? explicitSystemPrompt
            : resources.systemPrompt();
        if (baseSystemPrompt != null && !baseSystemPrompt.isBlank()) {
            sections.add(baseSystemPrompt.trim());
        }
        for (var contextFile : resources.contextFiles()) {
            sections.add(formatContextFile(contextFile));
        }
        for (var prompt : resources.appendSystemPrompts()) {
            if (prompt != null && !prompt.isBlank()) {
                sections.add(prompt.trim());
            }
        }
        if (appendSystemPrompt != null && !appendSystemPrompt.isBlank()) {
            sections.add(appendSystemPrompt.trim());
        }
        return String.join("\n\n", sections);
    }

    private static String formatContextFile(InstructionFile instructionFile) {
        return "Context from %s:\n%s".formatted(
            instructionFile.path().getFileName(),
            instructionFile.content().trim()
        );
    }
}
