package dev.pi.cli;

import dev.pi.session.PackageSource;
import dev.pi.session.PackageSourceManager;
import dev.pi.session.SettingsManager;
import dev.pi.session.AuthStorage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class PiPackageCommand {
    private final Path cwd;
    private final Appendable stdout;
    private final Appendable stderr;
    private final ManagerFactory managerFactory;

    PiPackageCommand(Path cwd, Appendable stdout, Appendable stderr) {
        this(cwd, stdout, stderr, AuthStorage.create());
    }

    PiPackageCommand(Path cwd, Appendable stdout, Appendable stderr, AuthStorage authStorage) {
        this(cwd, stdout, stderr, path -> {
            var settingsManager = SettingsManager.create(path);
            var manager = new PackageSourceManager(path, settingsManager, authStorage);
            return new PackageManagerAdapter(settingsManager, manager);
        });
    }

    PiPackageCommand(Path cwd, Appendable stdout, Appendable stderr, ManagerFactory managerFactory) {
        this.cwd = Objects.requireNonNull(cwd, "cwd");
        this.stdout = Objects.requireNonNull(stdout, "stdout");
        this.stderr = Objects.requireNonNull(stderr, "stderr");
        this.managerFactory = Objects.requireNonNull(managerFactory, "managerFactory");
    }

    CompletionStage<Boolean> runIfMatched(String... argv) {
        Objects.requireNonNull(argv, "argv");
        if (argv.length == 0) {
            return CompletableFuture.completedFuture(false);
        }

        var parsed = parse(argv);
        if (parsed == null) {
            return CompletableFuture.completedFuture(false);
        }

        try {
            if (parsed.help()) {
                appendLine(stdout, helpText(parsed.action()));
                return CompletableFuture.completedFuture(true);
            }
            if (parsed.invalidOption() != null) {
                throw new IllegalArgumentException("Unknown option " + parsed.invalidOption() + " for \"" + parsed.action().value + "\".");
            }
            if ((parsed.action() == Action.INSTALL || parsed.action() == Action.REMOVE) && (parsed.source() == null || parsed.source().isBlank())) {
                throw new IllegalArgumentException("Missing " + parsed.action().value + " source.");
            }
            if ((parsed.action() == Action.UPDATE || parsed.action() == Action.LIST) && parsed.local()) {
                throw new IllegalArgumentException("--local is only supported by install and remove.");
            }

            var manager = managerFactory.create(cwd);
            switch (parsed.action()) {
                case INSTALL -> {
                    var scope = parsed.local() ? PackageSourceManager.Scope.PROJECT : PackageSourceManager.Scope.GLOBAL;
                    manager.install(parsed.source(), scope);
                    manager.addSourceToSettings(parsed.source(), scope);
                    appendLine(stdout, "Installed " + parsed.source());
                }
                case REMOVE -> {
                    var scope = parsed.local() ? PackageSourceManager.Scope.PROJECT : PackageSourceManager.Scope.GLOBAL;
                    manager.remove(parsed.source(), scope);
                    if (!manager.removeSourceFromSettings(parsed.source(), scope)) {
                        throw new IllegalStateException("No matching package found for " + parsed.source());
                    }
                    appendLine(stdout, "Removed " + parsed.source());
                }
                case UPDATE -> {
                    manager.update(parsed.source());
                    appendLine(stdout, parsed.source() == null || parsed.source().isBlank()
                        ? "Updated packages"
                        : "Updated " + parsed.source());
                }
                case LIST -> renderList(manager);
            }
            return CompletableFuture.completedFuture(true);
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private void renderList(Manager manager) throws IOException {
        var globalPackages = manager.globalPackages();
        var projectPackages = manager.projectPackages();
        if (globalPackages.isEmpty() && projectPackages.isEmpty()) {
            appendLine(stdout, "No packages installed.");
            return;
        }
        if (!globalPackages.isEmpty()) {
            appendLine(stdout, "User packages:");
            renderScopeList(manager, globalPackages, PackageSourceManager.Scope.GLOBAL);
        }
        if (!projectPackages.isEmpty()) {
            appendLine(stdout, "Project packages:");
            renderScopeList(manager, projectPackages, PackageSourceManager.Scope.PROJECT);
        }
    }

    private void renderScopeList(Manager manager, List<PackageSource> packages, PackageSourceManager.Scope scope) throws IOException {
        for (var pkg : packages) {
            var label = pkg.source() + (isFiltered(pkg) ? " (filtered)" : "");
            appendLine(stdout, "  " + label);
            var installedPath = manager.getInstalledPath(pkg.source(), scope);
            if (installedPath != null) {
                appendLine(stdout, "    " + installedPath);
            }
        }
    }

    private static boolean isFiltered(PackageSource pkg) {
        return !pkg.extensions().isEmpty()
            || !pkg.skills().isEmpty()
            || !pkg.prompts().isEmpty()
            || !pkg.themes().isEmpty();
    }

    private static ParsedCommand parse(String... argv) {
        if (argv.length == 0) {
            return null;
        }
        var action = Action.fromValue(argv[0]);
        if (action == null) {
            return null;
        }

        boolean local = false;
        boolean help = false;
        String invalidOption = null;
        String source = null;
        for (var index = 1; index < argv.length; index += 1) {
            var argument = argv[index];
            if ("-h".equals(argument) || "--help".equals(argument)) {
                help = true;
                continue;
            }
            if ("-l".equals(argument) || "--local".equals(argument)) {
                local = true;
                continue;
            }
            if (argument.startsWith("-")) {
                invalidOption = invalidOption == null ? argument : invalidOption;
                continue;
            }
            if (source == null) {
                source = argument;
            }
        }
        return new ParsedCommand(action, source, local, help, invalidOption);
    }

    private static String helpText(Action action) {
        return switch (action) {
            case INSTALL -> """
                Usage:
                  pi install <source> [--local]

                Install a package and add it to settings.
                """;
            case REMOVE -> """
                Usage:
                  pi remove <source> [--local]

                Remove a package and remove it from settings.
                """;
            case UPDATE -> """
                Usage:
                  pi update [source]

                Update installed packages.
                """;
            case LIST -> """
                Usage:
                  pi list

                List installed packages from user and project settings.
                """;
        };
    }

    private static void appendLine(Appendable output, String text) throws IOException {
        output.append(text);
        output.append(System.lineSeparator());
    }

    enum Action {
        INSTALL("install"),
        REMOVE("remove"),
        UPDATE("update"),
        LIST("list");

        private final String value;

        Action(String value) {
            this.value = value;
        }

        private static Action fromValue(String value) {
            for (var action : values()) {
                if (action.value.equals(value)) {
                    return action;
                }
            }
            return null;
        }
    }

    record ParsedCommand(Action action, String source, boolean local, boolean help, String invalidOption) {
    }

    interface ManagerFactory {
        Manager create(Path cwd);
    }

    interface Manager {
        void install(String source, PackageSourceManager.Scope scope);

        void remove(String source, PackageSourceManager.Scope scope);

        void update(String source);

        boolean addSourceToSettings(String source, PackageSourceManager.Scope scope);

        boolean removeSourceFromSettings(String source, PackageSourceManager.Scope scope);

        Path getInstalledPath(String source, PackageSourceManager.Scope scope);

        List<PackageSource> globalPackages();

        List<PackageSource> projectPackages();
    }

    private record PackageManagerAdapter(
        SettingsManager settingsManager,
        PackageSourceManager manager
    ) implements Manager {
        @Override
        public void install(String source, PackageSourceManager.Scope scope) {
            manager.install(source, scope);
        }

        @Override
        public void remove(String source, PackageSourceManager.Scope scope) {
            manager.remove(source, scope);
        }

        @Override
        public void update(String source) {
            if (source == null || source.isBlank()) {
                manager.update();
            } else {
                manager.update(source);
            }
        }

        @Override
        public boolean addSourceToSettings(String source, PackageSourceManager.Scope scope) {
            return manager.addSourceToSettings(source, scope);
        }

        @Override
        public boolean removeSourceFromSettings(String source, PackageSourceManager.Scope scope) {
            return manager.removeSourceFromSettings(source, scope);
        }

        @Override
        public Path getInstalledPath(String source, PackageSourceManager.Scope scope) {
            return manager.getInstalledPath(source, scope);
        }

        @Override
        public List<PackageSource> globalPackages() {
            return settingsManager.getGlobalPackages();
        }

        @Override
        public List<PackageSource> projectPackages() {
            return settingsManager.getProjectPackages();
        }
    }
}
