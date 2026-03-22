package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.ai.PiAiClient;
import dev.pi.ai.auth.CredentialResolver;
import dev.pi.ai.registry.ApiProviderRegistry;
import dev.pi.ai.registry.ModelRegistry;
import dev.pi.session.PackageSource;
import dev.pi.session.PackageSourceManager;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PiCliModulePackageCommandTest {
    @Test
    void packageCommandsDoNotCreateInteractiveSessions() {
        var stdout = new StringBuilder();
        var sessionCreated = new AtomicBoolean(false);
        var manager = new FakeManager();
        manager.globalPackages.add(new PackageSource("npm:@acme/theme-pack"));
        manager.installedPaths.put("global:npm:@acme/theme-pack", Path.of("/global/theme-pack"));
        var module = new PiCliModule(
            Path.of("."),
            new StringReader(""),
            stdout,
            new StringBuilder(),
            new PiCliParser(),
            new PiAiClient(new ApiProviderRegistry(), new ModelRegistry(), CredentialResolver.defaultResolver()),
            args -> {
                sessionCreated.set(true);
                throw new AssertionError("session should not be created");
            },
            () -> new dev.pi.tui.VirtualTerminal(80, 24),
            PiCliKeybindingsLoader.createDefault(),
            dev.pi.session.AuthStorage.inMemory(),
            () -> new PiPackageCommand(Path.of("."), stdout, new StringBuilder(), path -> manager)
        );

        module.run("list").toCompletableFuture().join();

        assertThat(sessionCreated.get()).isFalse();
        assertThat(stdout.toString()).contains("User packages:").contains("npm:@acme/theme-pack");
    }

    private static final class FakeManager implements PiPackageCommand.Manager {
        private final List<PackageSource> globalPackages = new ArrayList<>();
        private final List<PackageSource> projectPackages = new ArrayList<>();
        private final java.util.Map<String, Path> installedPaths = new java.util.LinkedHashMap<>();

        @Override
        public void install(String source, PackageSourceManager.Scope scope) {
        }

        @Override
        public void remove(String source, PackageSourceManager.Scope scope) {
        }

        @Override
        public void update(String source) {
        }

        @Override
        public boolean addSourceToSettings(String source, PackageSourceManager.Scope scope) {
            return true;
        }

        @Override
        public boolean removeSourceFromSettings(String source, PackageSourceManager.Scope scope) {
            return true;
        }

        @Override
        public Path getInstalledPath(String source, PackageSourceManager.Scope scope) {
            return installedPaths.get((scope == PackageSourceManager.Scope.PROJECT ? "project:" : "global:") + source);
        }

        @Override
        public List<PackageSource> globalPackages() {
            return List.copyOf(globalPackages);
        }

        @Override
        public List<PackageSource> projectPackages() {
            return List.copyOf(projectPackages);
        }
    }
}
