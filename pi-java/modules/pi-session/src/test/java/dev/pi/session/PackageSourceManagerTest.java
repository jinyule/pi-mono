package dev.pi.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageSourceManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void installsProjectNpmSourceAndAddsItToProjectSettings() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        var settingsManager = SettingsManager.create(cwd, agentDir);
        var runner = new RecordingCommandRunner(tempDir.resolve("global-node-modules"));
        var manager = new PackageSourceManager(cwd, agentDir, settingsManager, runner, runner.globalNpmRoot());

        manager.install("npm:@acme/theme-pack@1.2.3", PackageSourceManager.Scope.PROJECT);
        var added = manager.addSourceToSettings("npm:@acme/theme-pack@1.2.3", PackageSourceManager.Scope.PROJECT);

        assertThat(added).isTrue();
        assertThat(manager.getInstalledPath("npm:@acme/theme-pack", PackageSourceManager.Scope.PROJECT))
            .isEqualTo(cwd.resolve(".pi").resolve("npm").resolve("node_modules").resolve("@acme").resolve("theme-pack").toAbsolutePath().normalize());
        assertThat(settingsManager.getProjectPackages())
            .extracting(PackageSource::source)
            .containsExactly("npm:@acme/theme-pack@1.2.3");
        assertThat(runner.invocations())
            .extracting(Invocation::command)
            .anySatisfy(command -> assertThat(command).contains("install", "@acme/theme-pack@1.2.3", "--prefix"));
    }

    @Test
    void installsProjectNpmSourceWithAuthBackedRegistryConfig() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        Files.writeString(cwd.resolve(".npmrc"), "@acme:registry=https://npm.pkg.github.com\n");
        var settingsManager = SettingsManager.create(cwd, agentDir);
        var authStorage = AuthStorage.inMemory();
        authStorage.setApiKey("github", "gh-private-token");
        var runner = new RecordingCommandRunner(tempDir.resolve("global-node-modules"));
        var manager = new PackageSourceManager(cwd, agentDir, settingsManager, runner, runner.globalNpmRoot(), authStorage);

        manager.install("npm:@acme/theme-pack", PackageSourceManager.Scope.PROJECT);

        assertThat(runner.invocations())
            .filteredOn(invocation -> invocation.command().size() >= 2 && "install".equals(invocation.command().get(1)))
            .singleElement()
            .satisfies(invocation -> {
                assertThat(invocation.environment()).containsKey("NPM_CONFIG_USERCONFIG");
                assertThat(invocation.userConfig())
                    .contains("@acme:registry=https://npm.pkg.github.com/")
                    .contains("//npm.pkg.github.com/:_authToken=gh-private-token")
                    .contains("always-auth=true");
            });
    }

    @Test
    void installsGlobalNpmSourceWithWorkspaceRegistryConfigAndSavedToken() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        Files.writeString(cwd.resolve(".npmrc"), "@acme:registry=https://npm.pkg.github.com\n");
        var settingsManager = SettingsManager.create(cwd, agentDir);
        var authStorage = AuthStorage.inMemory();
        authStorage.setApiKey("github", "gh-private-token");
        var runner = new RecordingCommandRunner(tempDir.resolve("global-node-modules"));
        var manager = new PackageSourceManager(cwd, agentDir, settingsManager, runner, runner.globalNpmRoot(), authStorage);

        manager.install("npm:@acme/theme-pack", PackageSourceManager.Scope.GLOBAL);

        assertThat(runner.invocations())
            .filteredOn(invocation -> invocation.command().size() >= 2 && "install".equals(invocation.command().get(1)))
            .singleElement()
            .satisfies(invocation -> {
                assertThat(invocation.command()).contains("-g", "@acme/theme-pack");
                assertThat(invocation.environment()).containsKey("NPM_CONFIG_USERCONFIG");
                assertThat(invocation.userConfig())
                    .contains("@acme:registry=https://npm.pkg.github.com/")
                    .contains("//npm.pkg.github.com/:_authToken=gh-private-token");
            });
    }

    @Test
    void installsProjectNpmSourceWithSavedGitlabCredentials() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        Files.writeString(cwd.resolve(".npmrc"), "@acme:registry=https://gitlab.com/api/v4/packages/npm/\n");
        var settingsManager = SettingsManager.create(cwd, agentDir);
        var authStorage = AuthStorage.inMemory();
        authStorage.setApiKey("gitlab", "glpat-private-token");
        var runner = new RecordingCommandRunner(tempDir.resolve("global-node-modules"));
        var manager = new PackageSourceManager(cwd, agentDir, settingsManager, runner, runner.globalNpmRoot(), authStorage);

        manager.install("npm:@acme/theme-pack", PackageSourceManager.Scope.PROJECT);

        assertThat(runner.invocations())
            .filteredOn(invocation -> invocation.command().size() >= 2 && "install".equals(invocation.command().get(1)))
            .singleElement()
            .satisfies(invocation -> assertThat(invocation.userConfig())
                .contains("@acme:registry=https://gitlab.com/api/v4/packages/npm/")
                .contains("//gitlab.com/api/v4/packages/npm/:_authToken=glpat-private-token"));
    }

    @Test
    void updatesAndRemovesManagedGitSource() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        var settingsManager = SettingsManager.create(cwd, agentDir);
        settingsManager.setPackages(List.of(new PackageSource("git:https://github.com/acme/tool-pack.git")));
        var runner = new RecordingCommandRunner(tempDir.resolve("global-node-modules"));
        var manager = new PackageSourceManager(cwd, agentDir, settingsManager, runner, runner.globalNpmRoot());

        manager.install("git:https://github.com/acme/tool-pack.git", PackageSourceManager.Scope.GLOBAL);
        assertThat(manager.getInstalledPath("git:https://github.com/acme/tool-pack.git", PackageSourceManager.Scope.GLOBAL))
            .isEqualTo(agentDir.resolve("git").resolve("github.com").resolve("acme").resolve("tool-pack").toAbsolutePath().normalize());

        manager.update("git:https://github.com/acme/tool-pack.git");

        assertThat(runner.invocations())
            .extracting(Invocation::command)
            .anySatisfy(command -> assertThat(command).containsExactly("git", "fetch", "--prune", "origin"))
            .anySatisfy(command -> assertThat(command).containsExactly("git", "clean", "-fdx"));

        manager.remove("git:https://github.com/acme/tool-pack.git", PackageSourceManager.Scope.GLOBAL);
        var removed = manager.removeSourceFromSettings("git:https://github.com/acme/tool-pack.git", PackageSourceManager.Scope.GLOBAL);

        assertThat(removed).isTrue();
        assertThat(manager.getInstalledPath("git:https://github.com/acme/tool-pack.git", PackageSourceManager.Scope.GLOBAL)).isNull();
        assertThat(settingsManager.getGlobalPackages()).isEmpty();
    }

    @Test
    void normalizesLocalSourcesRelativeToScopeBase() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        var pluginDir = cwd.resolve("packages").resolve("demo-pack");
        Files.createDirectories(pluginDir);
        var settingsManager = SettingsManager.create(cwd, agentDir);
        var runner = new RecordingCommandRunner(tempDir.resolve("global-node-modules"));
        var manager = new PackageSourceManager(cwd, agentDir, settingsManager, runner, runner.globalNpmRoot());

        var added = manager.addSourceToSettings(pluginDir.toString(), PackageSourceManager.Scope.PROJECT);

        assertThat(added).isTrue();
        assertThat(settingsManager.getProjectPackages())
            .extracting(PackageSource::source)
            .containsExactly(".." + java.io.File.separator + "packages" + java.io.File.separator + "demo-pack");
    }

    @Test
    void skipsPinnedSourcesDuringUpdate() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        var settingsManager = SettingsManager.create(cwd, agentDir);
        settingsManager.setPackages(List.of(
            new PackageSource("npm:@acme/theme-pack@1.2.3"),
            new PackageSource("git:https://github.com/acme/tool-pack.git@v1")
        ));
        var runner = new RecordingCommandRunner(tempDir.resolve("global-node-modules"));
        var manager = new PackageSourceManager(cwd, agentDir, settingsManager, runner, runner.globalNpmRoot());

        manager.update();

        assertThat(runner.invocations()).isEmpty();
    }

    @Test
    void installsGitSourceWithSavedGithubCredentials() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        var settingsManager = SettingsManager.create(cwd, agentDir);
        var authStorage = AuthStorage.inMemory();
        authStorage.setApiKey("github", "gh-private-token");
        var runner = new RecordingCommandRunner(tempDir.resolve("global-node-modules"));
        var manager = new PackageSourceManager(cwd, agentDir, settingsManager, runner, runner.globalNpmRoot(), authStorage);

        manager.install("git:git@github.com:acme/private-pack.git", PackageSourceManager.Scope.PROJECT);

        assertThat(runner.invocations())
            .filteredOn(invocation -> invocation.command().size() >= 2 && "clone".equals(invocation.command().get(1)))
            .singleElement()
            .satisfies(invocation -> {
                assertThat(invocation.command()).contains("https://github.com/acme/private-pack.git");
                assertThat(invocation.environment())
                    .containsEntry("GIT_CONFIG_KEY_0", "http.https://github.com/.extraHeader")
                    .containsEntry("GIT_CONFIG_VALUE_0", "Authorization: Bearer gh-private-token");
            });
    }

    @Test
    void updatesGitSourceWithSavedGithubCredentials() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir.resolve("git").resolve("github.com").resolve("acme").resolve("private-pack"));
        var settingsManager = SettingsManager.create(cwd, agentDir);
        settingsManager.setPackages(List.of(new PackageSource("git:git@github.com:acme/private-pack.git")));
        var authStorage = AuthStorage.inMemory();
        authStorage.setApiKey("github", "gh-private-token");
        var runner = new RecordingCommandRunner(tempDir.resolve("global-node-modules"));
        var manager = new PackageSourceManager(cwd, agentDir, settingsManager, runner, runner.globalNpmRoot(), authStorage);

        manager.update("git:git@github.com:acme/private-pack.git");

        assertThat(runner.invocations())
            .anySatisfy(invocation -> {
                assertThat(invocation.command()).containsExactly("git", "remote", "set-url", "origin", "https://github.com/acme/private-pack.git");
                assertThat(invocation.environment())
                    .containsEntry("GIT_CONFIG_VALUE_0", "Authorization: Bearer gh-private-token");
            })
            .anySatisfy(invocation -> {
                assertThat(invocation.command()).containsExactly("git", "fetch", "--prune", "origin");
                assertThat(invocation.environment())
                    .containsEntry("GIT_CONFIG_VALUE_0", "Authorization: Bearer gh-private-token");
            });
    }

    private static final class RecordingCommandRunner implements PackageSourceManager.CommandRunner {
        private final Path globalNpmRoot;
        private final List<Invocation> invocations = new ArrayList<>();

        private RecordingCommandRunner(Path globalNpmRoot) {
            this.globalNpmRoot = globalNpmRoot.toAbsolutePath().normalize();
        }

        Path globalNpmRoot() {
            return globalNpmRoot;
        }

        List<Invocation> invocations() {
            return List.copyOf(invocations);
        }

        @Override
        public void run(List<String> command, Path cwd) {
            run(command, cwd, Map.of());
        }

        @Override
        public void run(List<String> command, Path cwd, Map<String, String> environment) {
            invocations.add(new Invocation(List.copyOf(command), cwd, Map.copyOf(environment), capturedUserConfig(environment)));
            try {
                applySideEffects(command, cwd);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to apply fake command side effects", exception);
            }
        }

        @Override
        public String runCapture(List<String> command, Path cwd) {
            return runCapture(command, cwd, Map.of());
        }

        @Override
        public String runCapture(List<String> command, Path cwd, Map<String, String> environment) {
            invocations.add(new Invocation(List.copyOf(command), cwd, Map.copyOf(environment), capturedUserConfig(environment)));
            if (command.size() >= 3 && "root".equals(command.get(1)) && "-g".equals(command.get(2))) {
                return globalNpmRoot.toString();
            }
            return "";
        }

        private static String capturedUserConfig(Map<String, String> environment) {
            var userConfig = environment.get("NPM_CONFIG_USERCONFIG");
            if (userConfig == null || userConfig.isBlank()) {
                return null;
            }
            try {
                return Files.readString(Path.of(userConfig));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read fake npm user config", exception);
            }
        }

        private void applySideEffects(List<String> command, Path cwd) throws IOException {
            if (command.size() < 2) {
                return;
            }
            var executable = command.getFirst();
            var action = command.get(1);
            if (executable.startsWith("npm") && "install".equals(action)) {
                if (command.contains("-g")) {
                    var spec = command.getLast();
                    Files.createDirectories(globalNpmRoot.resolve(extractPackageName(spec)));
                    return;
                }
                var prefixIndex = command.indexOf("--prefix");
                if (prefixIndex >= 0 && prefixIndex + 1 < command.size()) {
                    var spec = command.get(2);
                    var installRoot = Path.of(command.get(prefixIndex + 1));
                    Files.createDirectories(installRoot.resolve("node_modules").resolve(extractPackageName(spec)));
                }
                return;
            }
            if (executable.startsWith("npm") && "uninstall".equals(action)) {
                if (command.contains("-g")) {
                    deleteTree(globalNpmRoot.resolve(command.getLast()));
                    return;
                }
                var prefixIndex = command.indexOf("--prefix");
                if (prefixIndex >= 0 && prefixIndex + 1 < command.size()) {
                    deleteTree(Path.of(command.get(prefixIndex + 1)).resolve("node_modules").resolve(command.get(2)));
                }
                return;
            }
            if ("git".equals(executable) && "clone".equals(action) && command.size() >= 4) {
                Files.createDirectories(Path.of(command.get(3)));
                return;
            }
            if ("git".equals(executable) && "reset".equals(action) && command.contains("@{upstream}")) {
                return;
            }
            if ("git".equals(executable) && "remote".equals(action)) {
                return;
            }
            if ("git".equals(executable) && "fetch".equals(action)) {
                return;
            }
            if ("git".equals(executable) && "clean".equals(action)) {
                return;
            }
            if (executable.startsWith("npm") && "install".equals(action) && cwd != null) {
                Files.createDirectories(cwd.resolve("node_modules"));
            }
        }

        private static Path extractPackageName(String spec) {
            var trimmed = spec == null ? "" : spec.trim();
            if (trimmed.startsWith("npm:")) {
                trimmed = trimmed.substring(4);
            }
            var atIndex = trimmed.startsWith("@")
                ? trimmed.indexOf('@', trimmed.indexOf('/') + 1)
                : trimmed.indexOf('@');
            var name = atIndex < 0 ? trimmed : trimmed.substring(0, atIndex);
            return Path.of(name.replace('/', java.io.File.separatorChar));
        }

        private static void deleteTree(Path path) throws IOException {
            if (path == null || !Files.exists(path)) {
                return;
            }
            try (var stream = Files.walk(path)) {
                stream.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(candidate -> {
                        try {
                            Files.deleteIfExists(candidate);
                        } catch (IOException ignored) {
                        }
                    });
            }
        }
    }

    private record Invocation(List<String> command, Path cwd, Map<String, String> environment, String userConfig) {
    }
}
