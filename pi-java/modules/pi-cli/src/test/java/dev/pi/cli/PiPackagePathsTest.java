package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiPackagePathsTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesPackagedReadmeDocsAndExamplesFromExplicitOverride() throws Exception {
        var packageDir = tempDir.resolve("package-root");
        Files.createDirectories(packageDir.resolve("docs"));
        Files.createDirectories(packageDir.resolve("examples"));
        var readme = packageDir.resolve("README.md");
        Files.writeString(readme, "# Pi\n", StandardCharsets.UTF_8);

        var environment = Map.of(PiPackagePaths.ENV_PACKAGE_DIR, packageDir.toString());

        assertThat(PiPackagePaths.readmePath(environment, tempDir.resolve("elsewhere"), tempDir.resolve("workspace")))
            .isEqualTo(readme.toAbsolutePath().normalize());
        assertThat(PiPackagePaths.docsPath(environment, tempDir.resolve("elsewhere"), tempDir.resolve("workspace")))
            .isEqualTo(packageDir.resolve("docs").toAbsolutePath().normalize());
        assertThat(PiPackagePaths.examplesPath(environment, tempDir.resolve("elsewhere"), tempDir.resolve("workspace")))
            .isEqualTo(packageDir.resolve("examples").toAbsolutePath().normalize());
    }

    @Test
    void prefersExplicitPackageDirectoryOverride() throws Exception {
        var packageDir = tempDir.resolve("package-root");
        Files.createDirectories(packageDir);
        var changelog = packageDir.resolve("CHANGELOG.md");
        Files.writeString(changelog, "## [0.1.0]\n", StandardCharsets.UTF_8);

        var resolved = PiPackagePaths.changelogPath(
            Map.of(PiPackagePaths.ENV_PACKAGE_DIR, packageDir.toString()),
            tempDir.resolve("elsewhere"),
            tempDir.resolve("workspace")
        );

        assertThat(resolved).isEqualTo(changelog.toAbsolutePath().normalize());
    }

    @Test
    void findsCodingAgentPackageFromCodeSourceTree() throws Exception {
        var repoRoot = tempDir.resolve("repo");
        var codeSourceBase = repoRoot.resolve("pi-java").resolve("modules").resolve("pi-cli").resolve("build").resolve("classes");
        var changelog = repoRoot.resolve("packages").resolve("coding-agent").resolve("CHANGELOG.md");
        Files.createDirectories(codeSourceBase);
        Files.createDirectories(changelog.getParent());
        Files.writeString(changelog, "## [0.2.0]\n", StandardCharsets.UTF_8);

        var resolved = PiPackagePaths.changelogPath(Map.of(), codeSourceBase, tempDir.resolve("workspace"));

        assertThat(resolved).isEqualTo(changelog.toAbsolutePath().normalize());
    }

    @Test
    void prefersWorkspaceChangelogOverCodeSourceTree() throws Exception {
        var workspaceRoot = tempDir.resolve("workspace");
        var workspaceChangelog = workspaceRoot.resolve("packages").resolve("coding-agent").resolve("CHANGELOG.md");
        Files.createDirectories(workspaceChangelog.getParent());
        Files.writeString(workspaceChangelog, "## [0.3.0]\n", StandardCharsets.UTF_8);

        var repoRoot = tempDir.resolve("repo");
        var codeSourceBase = repoRoot.resolve("pi-java").resolve("modules").resolve("pi-cli").resolve("build").resolve("classes");
        var installedChangelog = repoRoot.resolve("packages").resolve("coding-agent").resolve("CHANGELOG.md");
        Files.createDirectories(codeSourceBase);
        Files.createDirectories(installedChangelog.getParent());
        Files.writeString(installedChangelog, "## [0.2.0]\n", StandardCharsets.UTF_8);

        var resolved = PiPackagePaths.changelogPath(Map.of(), codeSourceBase, workspaceRoot);

        assertThat(resolved).isEqualTo(workspaceChangelog.toAbsolutePath().normalize());
    }

    @Test
    void findsPackagedAssetsFromRepoRoot() throws Exception {
        var repoRoot = tempDir.resolve("repo");
        var codingAgentRoot = repoRoot.resolve("packages").resolve("coding-agent");
        var codeSourceBase = repoRoot.resolve("pi-java").resolve("modules").resolve("pi-cli").resolve("build").resolve("classes");
        Files.createDirectories(codeSourceBase);
        Files.createDirectories(codingAgentRoot.resolve("docs"));
        Files.createDirectories(codingAgentRoot.resolve("examples"));
        Files.writeString(codingAgentRoot.resolve("README.md"), "# Coding Agent\n", StandardCharsets.UTF_8);

        assertThat(PiPackagePaths.readmePath(Map.of(), codeSourceBase, tempDir.resolve("workspace")))
            .isEqualTo(codingAgentRoot.resolve("README.md").toAbsolutePath().normalize());
        assertThat(PiPackagePaths.docsPath(Map.of(), codeSourceBase, tempDir.resolve("workspace")))
            .isEqualTo(codingAgentRoot.resolve("docs").toAbsolutePath().normalize());
        assertThat(PiPackagePaths.examplesPath(Map.of(), codeSourceBase, tempDir.resolve("workspace")))
            .isEqualTo(codingAgentRoot.resolve("examples").toAbsolutePath().normalize());
    }
}
