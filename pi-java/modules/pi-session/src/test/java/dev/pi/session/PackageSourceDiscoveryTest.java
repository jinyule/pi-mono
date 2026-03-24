package dev.pi.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageSourceDiscoveryTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesGlobalAndProjectPackageResourcesWithScopeRelativePaths() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        var globalPackage = agentDir.resolve("packages").resolve("theme-pack");
        var projectPackage = cwd.resolve(".pi").resolve("vendor").resolve("project-pack");
        Files.createDirectories(globalPackage.resolve("themes"));
        Files.createDirectories(projectPackage.resolve("dist").resolve("themes"));
        Files.createDirectories(projectPackage.resolve("dist").resolve("extensions"));
        Files.writeString(globalPackage.resolve("themes").resolve("dawn.json"), "{}");
        Files.writeString(projectPackage.resolve("dist").resolve("themes").resolve("ocean.json"), "{}");
        Files.writeString(projectPackage.resolve("dist").resolve("extensions").resolve("demo.jar"), "");

        var discovery = new PackageSourceDiscovery(cwd, agentDir);

        assertThat(discovery.resolve(
            List.of(new PackageSource("packages/theme-pack")),
            List.of(new PackageSource("vendor/project-pack", List.of("dist/extensions/demo.jar"), List.of(), List.of(), List.of("dist/themes"))),
            PackageSourceDiscovery.ResourceType.THEMES
        )).containsExactly(
            projectPackage.resolve("dist").resolve("themes").toAbsolutePath().normalize(),
            globalPackage.resolve("themes").toAbsolutePath().normalize()
        );
        assertThat(discovery.resolve(
            List.of(),
            List.of(new PackageSource("vendor/project-pack", List.of("dist/extensions/demo.jar"), List.of(), List.of(), List.of("dist/themes"))),
            PackageSourceDiscovery.ResourceType.EXTENSIONS
        )).containsExactly(
            projectPackage.resolve("dist").resolve("extensions").resolve("demo.jar").toAbsolutePath().normalize()
        );
    }

    @Test
    void fallsBackToPackageRootForExtensionDirectoriesWithoutConventionFolder() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        var packageRoot = cwd.resolve("plugin-package");
        Files.createDirectories(packageRoot);

        var discovery = new PackageSourceDiscovery(cwd, agentDir);

        assertThat(discovery.resolve(
            List.of(),
            List.of(new PackageSource("../plugin-package")),
            PackageSourceDiscovery.ResourceType.EXTENSIONS
        )).containsExactly(packageRoot.toAbsolutePath().normalize());
    }

    @Test
    void resolvesInstalledProjectNpmPackages() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        var installedPackage = cwd.resolve(".pi").resolve("npm").resolve("node_modules").resolve("@acme").resolve("theme-pack");
        Files.createDirectories(installedPackage.resolve("dist").resolve("themes"));
        Files.writeString(installedPackage.resolve("dist").resolve("themes").resolve("ocean.json"), "{}");
        var discovery = new PackageSourceDiscovery(cwd, agentDir);

        assertThat(discovery.resolve(
            List.of(),
            List.of(new PackageSource("npm:@acme/theme-pack@1.2.3", List.of(), List.of(), List.of(), List.of("dist/themes"))),
            PackageSourceDiscovery.ResourceType.THEMES
        )).containsExactly(installedPackage.resolve("dist").resolve("themes").toAbsolutePath().normalize());
    }

    @Test
    void resolvesInstalledGlobalNpmPackagesWhenRootIsKnown() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        var globalNpmRoot = tempDir.resolve("global-node-modules");
        var installedPackage = globalNpmRoot.resolve("@acme").resolve("theme-pack");
        Files.createDirectories(installedPackage.resolve("themes"));
        Files.writeString(installedPackage.resolve("themes").resolve("midnight.json"), "{}");

        var discovery = new PackageSourceDiscovery(cwd, agentDir, globalNpmRoot);

        assertThat(discovery.resolve(
            List.of(new PackageSource("npm:@acme/theme-pack")),
            List.of(),
            PackageSourceDiscovery.ResourceType.THEMES
        )).containsExactly(installedPackage.resolve("themes").toAbsolutePath().normalize());
    }

    @Test
    void resolvesInstalledGitPackages() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        var installedPackage = agentDir.resolve("git").resolve("github.com").resolve("acme").resolve("theme-pack");
        Files.createDirectories(installedPackage.resolve("dist").resolve("themes"));
        Files.writeString(installedPackage.resolve("dist").resolve("themes").resolve("git-theme.json"), "{}");

        var discovery = new PackageSourceDiscovery(cwd, agentDir);

        assertThat(discovery.resolve(
            List.of(new PackageSource("git:https://github.com/acme/theme-pack.git@v1", List.of(), List.of(), List.of(), List.of("dist/themes"))),
            List.of(),
            PackageSourceDiscovery.ResourceType.THEMES
        )).containsExactly(installedPackage.resolve("dist").resolve("themes").toAbsolutePath().normalize());
    }

    @Test
    void installsMissingProjectPackagesWhenEnabled() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd.resolve(".pi"));
        var installedPackage = cwd.resolve(".pi").resolve("npm").resolve("node_modules").resolve("@acme").resolve("theme-pack");
        var commandRunner = new FakeCommandRunner(installedPackage);
        var manager = new PackageSourceManager(cwd, agentDir, SettingsManager.inMemory(), commandRunner, null);
        var discovery = new PackageSourceDiscovery(manager, true);

        var resolved = discovery.resolve(
            List.of(),
            List.of(new PackageSource("npm:@acme/theme-pack")),
            PackageSourceDiscovery.ResourceType.THEMES
        );

        assertThat(resolved).containsExactly(
            installedPackage.resolve("themes").toAbsolutePath().normalize()
        );
        assertThat(commandRunner.commands).anyMatch(command -> command.contains("npm.cmd install @acme/theme-pack"));
    }

    @Test
    void prefersProjectPackagesOverGlobalPackagesWithSameIdentity() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        var globalNpmRoot = tempDir.resolve("global-node-modules");
        var globalPackage = globalNpmRoot.resolve("@acme").resolve("theme-pack");
        var projectPackage = cwd.resolve(".pi").resolve("npm").resolve("node_modules").resolve("@acme").resolve("theme-pack");
        Files.createDirectories(globalPackage.resolve("themes"));
        Files.createDirectories(projectPackage.resolve("themes"));
        Files.writeString(globalPackage.resolve("themes").resolve("global.json"), "{}");
        Files.writeString(projectPackage.resolve("themes").resolve("project.json"), "{}");

        var discovery = new PackageSourceDiscovery(cwd, agentDir, globalNpmRoot);

        assertThat(discovery.resolve(
            List.of(new PackageSource("npm:@acme/theme-pack")),
            List.of(new PackageSource("npm:@acme/theme-pack")),
            PackageSourceDiscovery.ResourceType.THEMES
        )).containsExactly(projectPackage.resolve("themes").toAbsolutePath().normalize());
    }

    private static final class FakeCommandRunner implements PackageSourceManager.CommandRunner {
        private final Path installedPackage;
        private final List<String> commands = new ArrayList<>();

        private FakeCommandRunner(Path installedPackage) {
            this.installedPackage = installedPackage;
        }

        @Override
        public void run(List<String> command, Path cwd) {
            var joined = String.join(" ", command);
            commands.add(joined);
            if (joined.contains("install") && joined.contains("@acme/theme-pack")) {
                try {
                    Files.createDirectories(installedPackage.resolve("themes"));
                    Files.writeString(installedPackage.resolve("themes").resolve("installed.json"), "{}");
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }
        }

        @Override
        public String runCapture(List<String> command, Path cwd) {
            commands.add(String.join(" ", command));
            return "";
        }
    }
}
