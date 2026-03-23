package dev.pi.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class InstructionResourceLoader {
    private static final List<String> CONTEXT_FILE_CANDIDATES = List.of("AGENTS.md", "CLAUDE.md");

    private final Path cwd;
    private final Path agentDir;
    private InstructionResources resources = InstructionResources.empty();
    private final List<ResourceLoadError> errors = new ArrayList<>();

    public InstructionResourceLoader(Path cwd) {
        this(cwd, PiAgentPaths.agentDir());
    }

    public InstructionResourceLoader(Path cwd, Path agentDir) {
        this.cwd = Objects.requireNonNull(cwd, "cwd").toAbsolutePath().normalize();
        this.agentDir = Objects.requireNonNull(agentDir, "agentDir").toAbsolutePath().normalize();
    }

    public void reload() {
        errors.clear();
        resources = new InstructionResources(
            loadContextFiles(),
            discoverSystemPrompt().orElse(null),
            discoverAppendSystemPrompts()
        );
    }

    public InstructionResources resources() {
        return resources;
    }

    public List<ResourceLoadError> drainErrors() {
        var drained = List.copyOf(errors);
        errors.clear();
        return drained;
    }

    private List<InstructionFile> loadContextFiles() {
        var loaded = new ArrayList<InstructionFile>();
        var seenPaths = new LinkedHashSet<Path>();

        loadContextFileFromDir(agentDir).ifPresent(file -> {
            loaded.add(file);
            seenPaths.add(file.path());
        });

        var ancestorFiles = new ArrayList<InstructionFile>();
        Path current = cwd;
        while (current != null) {
            var contextFile = loadContextFileFromDir(current);
            if (contextFile.isPresent() && seenPaths.add(contextFile.get().path())) {
                ancestorFiles.addFirst(contextFile.get());
            }
            current = current.getParent();
        }

        loaded.addAll(ancestorFiles);
        return List.copyOf(loaded);
    }

    private Optional<String> discoverSystemPrompt() {
        return readPromptFile(projectConfigPath("SYSTEM.md"))
            .or(() -> readPromptFile(agentDir.resolve("SYSTEM.md")));
    }

    private List<String> discoverAppendSystemPrompts() {
        return readPromptFile(projectConfigPath("APPEND_SYSTEM.md"))
            .or(() -> readPromptFile(agentDir.resolve("APPEND_SYSTEM.md")))
            .map(List::of)
            .orElseGet(List::of);
    }

    private Optional<InstructionFile> loadContextFileFromDir(Path dir) {
        for (var candidate : CONTEXT_FILE_CANDIDATES) {
            var path = dir.resolve(candidate);
            if (!Files.exists(path)) {
                continue;
            }
            return readFile(path).map(content -> new InstructionFile(path, content));
        }
        return Optional.empty();
    }

    private Optional<String> readPromptFile(Path path) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        return readFile(path);
    }

    private Optional<String> readFile(Path path) {
        try {
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            errors.add(new ResourceLoadError(path, exception));
            return Optional.empty();
        }
    }

    private Path projectConfigPath(String fileName) {
        return cwd.resolve(".pi").resolve(fileName);
    }

    public record ResourceLoadError(
        Path path,
        IOException error
    ) {
        public ResourceLoadError {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(error, "error");
        }
    }
}
