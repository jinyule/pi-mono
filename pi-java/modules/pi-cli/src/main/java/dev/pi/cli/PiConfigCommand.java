package dev.pi.cli;

import dev.pi.session.PackageSourceManager;
import dev.pi.session.SettingsManager;
import dev.pi.session.AuthStorage;
import dev.pi.tui.Terminal;
import dev.pi.tui.Tui;
import java.io.IOException;
import java.lang.Appendable;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

final class PiConfigCommand {
    private final Path cwd;
    private final Appendable stdout;
    private final Supplier<Terminal> terminalFactory;
    private final AuthStorage authStorage;

    PiConfigCommand(Path cwd, Appendable stdout, Supplier<Terminal> terminalFactory) {
        this(cwd, stdout, terminalFactory, AuthStorage.create());
    }

    PiConfigCommand(Path cwd, Appendable stdout, Supplier<Terminal> terminalFactory, AuthStorage authStorage) {
        this.cwd = Objects.requireNonNull(cwd, "cwd").toAbsolutePath().normalize();
        this.stdout = Objects.requireNonNull(stdout, "stdout");
        this.terminalFactory = Objects.requireNonNull(terminalFactory, "terminalFactory");
        this.authStorage = Objects.requireNonNull(authStorage, "authStorage");
    }

    CompletionStage<Boolean> runIfMatched(String... argv) {
        if (argv == null || argv.length == 0 || !"config".equals(argv[0])) {
            return CompletableFuture.completedFuture(false);
        }
        if (argv.length > 1 && ("-h".equals(argv[1]) || "--help".equals(argv[1]))) {
            appendLine(stdout, helpText());
            return CompletableFuture.completedFuture(true);
        }
        if (argv.length > 1) {
            appendLine(stdout, "Unknown option: " + argv[1]);
            appendLine(stdout, helpText());
            return CompletableFuture.completedFuture(true);
        }

        var settingsManager = SettingsManager.create(cwd);
        var packageSourceManager = new PackageSourceManager(cwd, settingsManager, authStorage);
        var resolver = new PiConfigResolver(settingsManager, packageSourceManager);
        runSelector(resolver);
        return CompletableFuture.completedFuture(true);
    }

    private void runSelector(PiConfigResolver resolver) {
        var done = new CompletableFuture<Void>();
        var tui = new Tui(terminalFactory.get(), true);
        var selector = new PiConfigSelector(
            resolver.resolve(),
            (item, enabled) -> resolver.toggle(item, enabled),
            () -> {
                if (done.complete(null)) {
                    tui.stop();
                }
            },
            tui::requestRender
        );
        tui.addChild(selector);
        tui.setFocus(selector);
        tui.start();
        done.join();
    }

    private static String helpText() {
        return """
            Usage: pi config

            Open package resource configuration.
            Toggle package-provided extensions, skills, prompts, and themes.
            """;
    }

    private static void appendLine(Appendable appendable, String line) {
        try {
            appendable.append(line).append(System.lineSeparator());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write command output", exception);
        }
    }
}
