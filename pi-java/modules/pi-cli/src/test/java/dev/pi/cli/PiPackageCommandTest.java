package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.pi.session.PackageSource;
import dev.pi.session.PackageSourceManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PiPackageCommandTest {
    @Test
    void installsProjectPackageAndAddsItToSettings() {
        var stdout = new StringBuilder();
        var manager = new FakeManager();
        var command = new PiPackageCommand(Path.of("."), stdout, new StringBuilder(), path -> manager);

        var handled = command.runIfMatched("install", "--local", "npm:@acme/theme-pack").toCompletableFuture().join();

        assertThat(handled).isTrue();
        assertThat(manager.installs).containsExactly(new ActionCall("npm:@acme/theme-pack", PackageSourceManager.Scope.PROJECT));
        assertThat(manager.addedSources).containsExactly(new ActionCall("npm:@acme/theme-pack", PackageSourceManager.Scope.PROJECT));
        assertThat(stdout.toString()).contains("Installed npm:@acme/theme-pack");
    }

    @Test
    void listsUserAndProjectPackages() {
        var stdout = new StringBuilder();
        var manager = new FakeManager();
        manager.globalPackages.add(new PackageSource("npm:@acme/theme-pack"));
        manager.projectPackages.add(new PackageSource(
            "git:https://github.com/acme/tools.git",
            List.of("dist/ext"),
            List.of(),
            List.of(),
            List.of()
        ));
        manager.installedPaths.put("global:npm:@acme/theme-pack", Path.of("/global/theme-pack"));
        manager.installedPaths.put("project:git:https://github.com/acme/tools.git", Path.of("/project/tools"));
        var command = new PiPackageCommand(Path.of("."), stdout, new StringBuilder(), path -> manager);

        var handled = command.runIfMatched("list").toCompletableFuture().join();

        assertThat(handled).isTrue();
        assertThat(stdout.toString())
            .contains("User packages:")
            .contains("Project packages:")
            .contains("npm:@acme/theme-pack")
            .contains("git:https://github.com/acme/tools.git (filtered)")
            .contains("theme-pack")
            .contains("tools");
    }

    @Test
    void rejectsInvalidLocalUsageForUpdate() {
        var command = new PiPackageCommand(Path.of("."), new StringBuilder(), new StringBuilder(), path -> new FakeManager());

        assertThatThrownBy(() -> command.runIfMatched("update", "--local").toCompletableFuture().join())
            .hasRootCauseMessage("--local is only supported by install and remove.");
    }

    @Test
    void returnsFalseWhenCommandDoesNotMatch() {
        var command = new PiPackageCommand(Path.of("."), new StringBuilder(), new StringBuilder(), path -> new FakeManager());

        var handled = command.runIfMatched("hello").toCompletableFuture().join();

        assertThat(handled).isFalse();
    }

    private static final class FakeManager implements PiPackageCommand.Manager {
        private final List<ActionCall> installs = new ArrayList<>();
        private final List<ActionCall> removes = new ArrayList<>();
        private final List<ActionCall> addedSources = new ArrayList<>();
        private final List<ActionCall> removedSources = new ArrayList<>();
        private final List<String> updates = new ArrayList<>();
        private final List<PackageSource> globalPackages = new ArrayList<>();
        private final List<PackageSource> projectPackages = new ArrayList<>();
        private final java.util.Map<String, Path> installedPaths = new java.util.LinkedHashMap<>();

        @Override
        public void install(String source, PackageSourceManager.Scope scope) {
            installs.add(new ActionCall(source, scope));
        }

        @Override
        public void remove(String source, PackageSourceManager.Scope scope) {
            removes.add(new ActionCall(source, scope));
        }

        @Override
        public void update(String source) {
            updates.add(source);
        }

        @Override
        public boolean addSourceToSettings(String source, PackageSourceManager.Scope scope) {
            addedSources.add(new ActionCall(source, scope));
            return true;
        }

        @Override
        public boolean removeSourceFromSettings(String source, PackageSourceManager.Scope scope) {
            removedSources.add(new ActionCall(source, scope));
            return true;
        }

        @Override
        public Path getInstalledPath(String source, PackageSourceManager.Scope scope) {
            return installedPaths.get(key(scope, source));
        }

        @Override
        public List<PackageSource> globalPackages() {
            return List.copyOf(globalPackages);
        }

        @Override
        public List<PackageSource> projectPackages() {
            return List.copyOf(projectPackages);
        }

        private static String key(PackageSourceManager.Scope scope, String source) {
            return (scope == PackageSourceManager.Scope.PROJECT ? "project:" : "global:") + source;
        }
    }

    private record ActionCall(String source, PackageSourceManager.Scope scope) {
    }
}
