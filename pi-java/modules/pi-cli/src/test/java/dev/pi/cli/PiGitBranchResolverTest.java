package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiGitBranchResolverTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void resolvesBranchFromGitHeadDirectory() throws IOException {
        var project = tempDir.resolve("project");
        var gitDir = project.resolve(".git");
        Files.createDirectories(gitDir);
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/feature/footer\n", StandardCharsets.UTF_8);

        assertThat(PiGitBranchResolver.resolve(project.toString())).isEqualTo("feature/footer");
    }

    @Test
    void resolvesDetachedHead() throws IOException {
        var project = tempDir.resolve("project");
        var gitDir = project.resolve(".git");
        Files.createDirectories(gitDir);
        Files.writeString(gitDir.resolve("HEAD"), "9fceb02d0ae598e95dc970b74767f19372d61af8\n", StandardCharsets.UTF_8);

        assertThat(PiGitBranchResolver.resolve(project.toString())).isEqualTo("detached");
    }

    @Test
    void resolvesBranchFromWorktreeGitFile() throws IOException {
        var project = tempDir.resolve("project");
        var worktree = tempDir.resolve("worktree");
        Files.createDirectories(project);
        Files.createDirectories(worktree);
        Files.writeString(project.resolve(".git"), "gitdir: ../worktree\n", StandardCharsets.UTF_8);
        Files.writeString(worktree.resolve("HEAD"), "ref: refs/heads/feature/worktree\n", StandardCharsets.UTF_8);

        assertThat(PiGitBranchResolver.resolve(project.toString())).isEqualTo("feature/worktree");
    }
}
