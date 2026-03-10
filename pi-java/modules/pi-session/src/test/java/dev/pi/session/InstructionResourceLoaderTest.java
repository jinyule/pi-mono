package dev.pi.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstructionResourceLoaderTest {
    @Test
    void startsEmptyBeforeReload(@TempDir Path tempDir) {
        var loader = new InstructionResourceLoader(tempDir.resolve("project"), tempDir.resolve("agent"));

        assertThat(loader.resources()).isEqualTo(InstructionResources.empty());
    }

    @Test
    void loadsGlobalAndAncestorContextFilesInStableOrder(@TempDir Path tempDir) throws IOException {
        var agentDir = tempDir.resolve("agent");
        var projectDir = tempDir.resolve("workspace").resolve("project").resolve("nested");
        Files.createDirectories(agentDir);
        Files.createDirectories(projectDir);

        Files.writeString(agentDir.resolve("AGENTS.md"), "global", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("workspace").resolve("AGENTS.md"), "workspace", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("workspace").resolve("project").resolve("CLAUDE.md"), "project-claude", StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve("AGENTS.md"), "nested", StandardCharsets.UTF_8);

        var loader = new InstructionResourceLoader(projectDir, agentDir);
        loader.reload();

        assertThat(loader.resources().contextFiles().stream().map(file -> file.path().getFileName().toString() + ":" + file.content()).toList())
            .containsExactly(
                "AGENTS.md:global",
                "AGENTS.md:workspace",
                "CLAUDE.md:project-claude",
                "AGENTS.md:nested"
            );
    }

    @Test
    void prefersAgentsOverClaudeWithinSameDirectory(@TempDir Path tempDir) throws IOException {
        var agentDir = tempDir.resolve("agent");
        var projectDir = tempDir.resolve("project");
        Files.createDirectories(agentDir);
        Files.createDirectories(projectDir);

        Files.writeString(projectDir.resolve("CLAUDE.md"), "claude", StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve("AGENTS.md"), "agents", StandardCharsets.UTF_8);

        var loader = new InstructionResourceLoader(projectDir, agentDir);
        loader.reload();

        assertThat(loader.resources().contextFiles()).singleElement().satisfies(file -> {
            assertThat(file.path().getFileName().toString()).isEqualTo("AGENTS.md");
            assertThat(file.content()).isEqualTo("agents");
        });
    }

    @Test
    void projectSystemPromptOverridesGlobal(@TempDir Path tempDir) throws IOException {
        var agentDir = tempDir.resolve("agent");
        var projectDir = tempDir.resolve("project");
        Files.createDirectories(agentDir);
        Files.createDirectories(projectDir.resolve(".pi"));

        Files.writeString(agentDir.resolve("SYSTEM.md"), "global system", StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve(".pi").resolve("SYSTEM.md"), "project system", StandardCharsets.UTF_8);

        var loader = new InstructionResourceLoader(projectDir, agentDir);
        loader.reload();

        assertThat(loader.resources().systemPrompt()).isEqualTo("project system");
    }

    @Test
    void projectAppendSystemPromptOverridesGlobal(@TempDir Path tempDir) throws IOException {
        var agentDir = tempDir.resolve("agent");
        var projectDir = tempDir.resolve("project");
        Files.createDirectories(agentDir);
        Files.createDirectories(projectDir.resolve(".pi"));

        Files.writeString(agentDir.resolve("APPEND_SYSTEM.md"), "global append", StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve(".pi").resolve("APPEND_SYSTEM.md"), "project append", StandardCharsets.UTF_8);

        var loader = new InstructionResourceLoader(projectDir, agentDir);
        loader.reload();

        assertThat(loader.resources().appendSystemPrompts()).containsExactly("project append");
    }

    @Test
    void fallsBackToGlobalSystemFilesWhenProjectFilesMissing(@TempDir Path tempDir) throws IOException {
        var agentDir = tempDir.resolve("agent");
        var projectDir = tempDir.resolve("project");
        Files.createDirectories(agentDir);
        Files.createDirectories(projectDir);

        Files.writeString(agentDir.resolve("SYSTEM.md"), "global system", StandardCharsets.UTF_8);
        Files.writeString(agentDir.resolve("APPEND_SYSTEM.md"), "global append", StandardCharsets.UTF_8);

        var loader = new InstructionResourceLoader(projectDir, agentDir);
        loader.reload();

        assertThat(loader.resources().systemPrompt()).isEqualTo("global system");
        assertThat(loader.resources().appendSystemPrompts()).containsExactly("global append");
    }
}
