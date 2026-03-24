package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.ai.PiAiClient;
import dev.pi.ai.auth.CredentialResolver;
import dev.pi.ai.registry.ApiProviderRegistry;
import dev.pi.ai.registry.ModelRegistry;
import dev.pi.session.AuthStorage;
import dev.pi.session.SettingsManager;
import dev.pi.tui.VirtualTerminal;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiCliModuleConfigCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void configCommandTogglesPackageResourcesWithoutCreatingInteractiveSession() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var packageRoot = tempDir.resolve("theme-pack");
        Files.createDirectories(cwd);
        Files.createDirectories(packageRoot.resolve("themes"));
        Files.writeString(packageRoot.resolve("themes").resolve("dark.json"), "{}");
        Files.writeString(packageRoot.resolve("themes").resolve("light.json"), "{}");
        SettingsManager.create(cwd).setProjectPackages(java.util.List.of(new dev.pi.session.PackageSource(packageRoot.toString())));

        var stdout = new StringBuilder();
        var sessionCreated = new AtomicBoolean(false);
        var terminal = new VirtualTerminal(90, 16);
        var module = new PiCliModule(
            cwd,
            new StringReader(""),
            stdout,
            new StringBuilder(),
            new PiCliParser(),
            emptyClient(),
            args -> {
                sessionCreated.set(true);
                throw new AssertionError("interactive session should not be created");
            },
            () -> terminal,
            PiCliKeybindingsLoader.createDefault(),
            AuthStorage.inMemory()
        );

        var finished = new CompletableFuture<Void>();
        Thread.ofVirtual().start(() -> {
            module.run("config").toCompletableFuture().join();
            finished.complete(null);
        });

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Resource Configuration")));
        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("themes/light.json")));
        terminal.sendInput("light");
        terminal.sendInput(" ");
        terminal.sendInput("\u001b");

        finished.get(2, TimeUnit.SECONDS);

        var saved = SettingsManager.create(cwd).getProjectPackages();
        assertThat(sessionCreated.get()).isFalse();
        assertThat(stdout).isEmpty();
        assertThat(saved).singleElement().satisfies(pkg -> assertThat(pkg.themes()).containsExactly("-themes/light.json"));
    }

    @Test
    void configHelpPrintsUsageWithoutCreatingInteractiveSession() {
        var cwd = tempDir.resolve("workspace");
        var stdout = new StringBuilder();
        var sessionCreated = new AtomicBoolean(false);
        var module = new PiCliModule(
            cwd,
            new StringReader(""),
            stdout,
            new StringBuilder(),
            new PiCliParser(),
            emptyClient(),
            args -> {
                sessionCreated.set(true);
                throw new AssertionError("interactive session should not be created");
            },
            () -> new VirtualTerminal(90, 16),
            PiCliKeybindingsLoader.createDefault(),
            AuthStorage.inMemory()
        );

        module.run("config", "--help").toCompletableFuture().join();

        assertThat(sessionCreated.get()).isFalse();
        assertThat(stdout.toString()).contains("Usage: pi config").contains("package-provided");
    }

    private static PiAiClient emptyClient() {
        return new PiAiClient(new ApiProviderRegistry(), new ModelRegistry(), CredentialResolver.defaultResolver());
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
        throw new AssertionError("condition not met");
    }
}
