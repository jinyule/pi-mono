package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.session.PackageSource;
import dev.pi.session.PackageSourceManager;
import dev.pi.session.SettingsManager;
import dev.pi.session.AuthStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiConfigResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void resolveIncludesConventionResourcesAndAppliedFilters() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var packageRoot = tempDir.resolve("resource-pack");
        Files.createDirectories(cwd);
        Files.createDirectories(packageRoot.resolve("themes"));
        Files.createDirectories(packageRoot.resolve("skills").resolve("demo"));
        Files.writeString(packageRoot.resolve("themes").resolve("dark.json"), "{}");
        Files.writeString(packageRoot.resolve("themes").resolve("light.json"), "{}");
        Files.writeString(packageRoot.resolve("skills").resolve("demo").resolve("SKILL.md"), "# demo");

        var settingsManager = SettingsManager.create(cwd);
        settingsManager.setProjectPackages(List.of(new PackageSource(
            packageRoot.toString(),
            List.of(),
            List.of(),
            List.of(),
            List.of("-themes/light.json")
        )));

        var resolver = new PiConfigResolver(settingsManager, new PackageSourceManager(cwd, settingsManager));
        var groups = resolver.resolve();

        assertThat(groups).hasSize(1);
        var themes = findSection(groups, PiConfigResolver.ResourceType.THEMES);
        var themeStates = themes.stream().collect(Collectors.toMap(PiConfigResolver.ResourceItem::displayName, PiConfigResolver.ResourceItem::enabled));
        assertThat(themeStates).containsEntry("themes/dark.json", true).containsEntry("themes/light.json", false);

        var skills = findSection(groups, PiConfigResolver.ResourceType.SKILLS);
        assertThat(skills).singleElement().extracting(PiConfigResolver.ResourceItem::displayName).isEqualTo("skills/demo");
    }

    @Test
    void resolveUsesManifestPatternsWhenManifestOnlyContainsGlobs() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var packageRoot = tempDir.resolve("prompt-pack");
        Files.createDirectories(cwd);
        Files.createDirectories(packageRoot.resolve("prompts"));
        Files.writeString(packageRoot.resolve("prompts").resolve("system.md"), "# system");
        Files.writeString(packageRoot.resolve("prompts").resolve("ignore.md"), "# ignore");
        Files.writeString(packageRoot.resolve("package.json"), """
            {
              "name": "prompt-pack",
              "pi": {
                "prompts": ["prompts/*.md", "!prompts/ignore.md"]
              }
            }
            """);

        var settingsManager = SettingsManager.create(cwd);
        settingsManager.setProjectPackages(List.of(new PackageSource(packageRoot.toString())));

        var resolver = new PiConfigResolver(settingsManager, new PackageSourceManager(cwd, settingsManager));
        var prompts = findSection(resolver.resolve(), PiConfigResolver.ResourceType.PROMPTS);
        var promptStates = prompts.stream().collect(Collectors.toMap(PiConfigResolver.ResourceItem::displayName, PiConfigResolver.ResourceItem::enabled));

        assertThat(promptStates).containsEntry("prompts/system.md", true).containsEntry("prompts/ignore.md", false);
    }

    @Test
    void resolveInstallsMissingRemotePackagesAndPrefersProjectScope() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        var globalNpmRoot = tempDir.resolve("global-node-modules");
        var installedPackage = cwd.resolve(".pi").resolve("npm").resolve("node_modules").resolve("@acme").resolve("theme-pack");
        var commandRunner = new FakeCommandRunner(installedPackage, globalNpmRoot);
        var settingsManager = SettingsManager.create(cwd, agentDir);
        settingsManager.setPackages(List.of(new PackageSource("npm:@acme/theme-pack")));
        settingsManager.setProjectPackages(List.of(new PackageSource("npm:@acme/theme-pack")));
        var manager = new PackageSourceManager(cwd, agentDir, settingsManager, commandRunner, globalNpmRoot);
        var resolver = new PiConfigResolver(settingsManager, manager);

        var groups = resolver.resolve();

        assertThat(groups).singleElement().extracting(PiConfigResolver.ResourceGroup::scope).isEqualTo(PiConfigResolver.Scope.PROJECT);
        assertThat(findSection(groups, PiConfigResolver.ResourceType.THEMES))
            .singleElement()
            .extracting(PiConfigResolver.ResourceItem::displayName)
            .isEqualTo("themes/project.json");
        assertThat(commandRunner.commands).anyMatch(command -> command.contains("npm.cmd install @acme/theme-pack"));
    }

    @Test
    void resolveUsesSavedGithubCredentialsForMissingGitPackages() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        var installedPackage = agentDir.resolve("git").resolve("github.com").resolve("acme").resolve("private-pack");
        var authStorage = AuthStorage.inMemory();
        authStorage.setApiKey("github", "gh-private-token");
        var commandRunner = new FakeCommandRunner(installedPackage, tempDir.resolve("global-node-modules"));
        var settingsManager = SettingsManager.create(cwd, agentDir);
        settingsManager.setPackages(List.of(new PackageSource(
            "git:git@github.com:acme/private-pack.git",
            List.of(),
            List.of(),
            List.of(),
            List.of("themes")
        )));
        var manager = new PackageSourceManager(cwd, agentDir, settingsManager, commandRunner, null, authStorage);
        var resolver = new PiConfigResolver(settingsManager, manager);

        var groups = resolver.resolve();

        assertThat(groups).singleElement();
        assertThat(findSection(groups, PiConfigResolver.ResourceType.THEMES))
            .singleElement()
            .extracting(PiConfigResolver.ResourceItem::displayName)
            .isEqualTo("themes/private.json");
        assertThat(commandRunner.environments)
            .anySatisfy(environment -> assertThat(environment).containsEntry("GIT_CONFIG_VALUE_0", "Authorization: Bearer gh-private-token"));
    }

    private static List<PiConfigResolver.ResourceItem> findSection(
        List<PiConfigResolver.ResourceGroup> groups,
        PiConfigResolver.ResourceType type
    ) {
        for (var group : groups) {
            for (var section : group.sections()) {
                if (section.type() == type) {
                    return section.items();
                }
            }
        }
        throw new AssertionError("Missing section: " + type);
    }

    private static final class FakeCommandRunner implements PackageSourceManager.CommandRunner {
        private final Path installedPackage;
        private final Path globalNpmRoot;
        private final List<String> commands = new ArrayList<>();
        private final List<Map<String, String>> environments = new ArrayList<>();

        private FakeCommandRunner(Path installedPackage, Path globalNpmRoot) {
            this.installedPackage = installedPackage;
            this.globalNpmRoot = globalNpmRoot;
        }

        @Override
        public void run(List<String> command, Path cwd) {
            run(command, cwd, Map.of());
        }

        @Override
        public void run(List<String> command, Path cwd, Map<String, String> environment) {
            var joined = String.join(" ", command);
            commands.add(joined);
            environments.add(Map.copyOf(environment));
            if (joined.contains("npm.cmd install @acme/theme-pack")) {
                try {
                    Files.createDirectories(installedPackage.resolve("themes"));
                    Files.writeString(installedPackage.resolve("themes").resolve("project.json"), "{}");
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }
            if (joined.contains("clone") && joined.contains("private-pack")) {
                try {
                    Files.createDirectories(installedPackage.resolve("themes"));
                    Files.writeString(installedPackage.resolve("themes").resolve("private.json"), "{}");
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }
        }

        @Override
        public String runCapture(List<String> command, Path cwd) {
            return runCapture(command, cwd, Map.of());
        }

        @Override
        public String runCapture(List<String> command, Path cwd, Map<String, String> environment) {
            commands.add(String.join(" ", command));
            environments.add(Map.copyOf(environment));
            return globalNpmRoot.toString();
        }
    }
}
