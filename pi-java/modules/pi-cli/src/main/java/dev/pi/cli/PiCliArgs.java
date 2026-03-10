package dev.pi.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record PiCliArgs(
    PiCliMode mode,
    boolean helpRequested,
    boolean versionRequested,
    boolean continueSession,
    boolean resumeRequested,
    boolean noSession,
    boolean noTools,
    boolean noExtensions,
    boolean noSkills,
    boolean noPromptTemplates,
    boolean noThemes,
    boolean listModelsRequested,
    boolean exportRequested,
    boolean offline,
    boolean verbose,
    String listModelsQuery,
    Path exportInputPath,
    String provider,
    String model,
    String apiKey,
    String systemPrompt,
    String appendSystemPrompt,
    PiCliThinkingLevel thinking,
    Path sessionPath,
    Path sessionDirectory,
    List<String> modelPatterns,
    List<String> tools,
    List<Path> extensions,
    List<Path> skills,
    List<Path> promptTemplates,
    List<Path> themes,
    List<Path> fileArgs,
    List<String> messages,
    List<String> unmatchedArguments
) {
    public PiCliArgs {
        mode = Objects.requireNonNull(mode, "mode");
        modelPatterns = List.copyOf(defaultList(modelPatterns));
        tools = List.copyOf(defaultList(tools));
        extensions = List.copyOf(defaultList(extensions));
        skills = List.copyOf(defaultList(skills));
        promptTemplates = List.copyOf(defaultList(promptTemplates));
        themes = List.copyOf(defaultList(themes));
        fileArgs = List.copyOf(defaultList(fileArgs));
        messages = List.copyOf(defaultList(messages));
        unmatchedArguments = List.copyOf(defaultList(unmatchedArguments));
    }

    private static <T> List<T> defaultList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
