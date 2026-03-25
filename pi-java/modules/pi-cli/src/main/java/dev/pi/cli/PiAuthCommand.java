package dev.pi.cli;

import dev.pi.ai.registry.ModelRegistry;
import dev.pi.session.AuthStorage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class PiAuthCommand {
    private final BufferedReader input;
    private final Appendable stdout;
    private final Appendable stderr;
    private final AuthStorage authStorage;
    private final ModelRegistry modelRegistry;
    private final PiHostCliAuth hostCliAuth;

    PiAuthCommand(
        Reader input,
        Appendable stdout,
        Appendable stderr,
        AuthStorage authStorage,
        ModelRegistry modelRegistry
    ) {
        this(input, stdout, stderr, authStorage, modelRegistry, new PiHostCliAuth());
    }

    PiAuthCommand(
        Reader input,
        Appendable stdout,
        Appendable stderr,
        AuthStorage authStorage,
        ModelRegistry modelRegistry,
        PiHostCliAuth hostCliAuth
    ) {
        this.input = input instanceof BufferedReader bufferedReader ? bufferedReader : new BufferedReader(Objects.requireNonNull(input, "input"));
        this.stdout = Objects.requireNonNull(stdout, "stdout");
        this.stderr = Objects.requireNonNull(stderr, "stderr");
        this.authStorage = Objects.requireNonNull(authStorage, "authStorage");
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry");
        this.hostCliAuth = Objects.requireNonNull(hostCliAuth, "hostCliAuth");
    }

    CompletionStage<Boolean> runIfMatched(String... argv) {
        Objects.requireNonNull(argv, "argv");
        if (argv.length == 0) {
            return CompletableFuture.completedFuture(false);
        }

        var command = argv[0];
        try {
            return switch (command) {
                case "login" -> handleLogin(argv);
                case "logout" -> handleLogout(argv);
                case "auth" -> handleAuth(argv);
                default -> CompletableFuture.completedFuture(false);
            };
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private CompletionStage<Boolean> handleLogin(String... argv) throws IOException {
        if (argv.length > 1 && isHelpFlag(argv[1])) {
            appendLine(stdout, loginHelpText());
            return CompletableFuture.completedFuture(true);
        }

        String provider = argv.length > 1 ? normalizeProviderId(argv[1]) : null;
        if (provider == null) {
            provider = promptForProvider("Select provider to login:", availableProviders());
        }

        var secret = argv.length > 2 ? argv[2] : null;
        var imported = secret == null || secret.isBlank() ? hostCliAuth.importFor(provider) : null;
        if (imported != null) {
            authStorage.setApiKey(provider, imported.secret());
            appendLine(stdout, "Imported credentials for " + provider + " from " + imported.sourceDisplay());
            return CompletableFuture.completedFuture(true);
        }
        if (secret == null || secret.isBlank()) {
            secret = promptForSecret(provider);
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Credentials are required");
        }

        authStorage.setApiKey(provider, secret.trim());
        appendLine(stdout, "Saved credentials for " + provider);
        return CompletableFuture.completedFuture(true);
    }

    private CompletionStage<Boolean> handleLogout(String... argv) throws IOException {
        if (argv.length > 1 && isHelpFlag(argv[1])) {
            appendLine(stdout, logoutHelpText());
            return CompletableFuture.completedFuture(true);
        }

        String provider = argv.length > 1 ? normalizeProviderId(argv[1]) : null;
        if (provider == null) {
            var loggedInProviders = loggedInProviders();
            if (loggedInProviders.isEmpty()) {
                appendLine(stdout, "No saved credentials. Use pi login first.");
                return CompletableFuture.completedFuture(true);
            }
            if (loggedInProviders.size() == 1) {
                provider = loggedInProviders.getFirst().providerId();
            } else {
                provider = promptForProvider("Select provider to logout:", loggedInProviders);
            }
        }

        if (!authStorage.has(provider)) {
            throw new IllegalStateException("No saved credentials for " + provider);
        }
        authStorage.remove(provider);
        appendLine(stdout, "Removed credentials for " + provider);
        return CompletableFuture.completedFuture(true);
    }

    private CompletionStage<Boolean> handleAuth(String... argv) throws IOException {
        if (argv.length == 1 || isHelpFlag(argv[1])) {
            appendLine(stdout, authHelpText());
            return CompletableFuture.completedFuture(true);
        }
        if (!"list".equals(argv[1])) {
            throw new IllegalArgumentException("Unknown auth command: " + argv[1]);
        }
        if (argv.length > 2 && !isHelpFlag(argv[2])) {
            throw new IllegalArgumentException("Unknown option " + argv[2] + " for \"auth list\".");
        }
        if (argv.length > 2 && isHelpFlag(argv[2])) {
            appendLine(stdout, authListHelpText());
            return CompletableFuture.completedFuture(true);
        }
        renderSavedCredentials();
        return CompletableFuture.completedFuture(true);
    }

    private void renderSavedCredentials() throws IOException {
        var loggedInProviders = loggedInProviders();
        if (loggedInProviders.isEmpty()) {
            appendLine(stdout, "No saved credentials.");
            return;
        }
        appendLine(stdout, "Saved credentials:");
        for (var provider : loggedInProviders) {
            appendLine(stdout, "  " + provider.providerId() + " (" + provider.displayName() + ")");
        }
    }

    private List<PiInteractiveSession.AuthProvider> availableProviders() {
        return PiAuthProviders.mergeProviders(modelRegistry.getProviders(), authStorage);
    }

    private List<PiInteractiveSession.AuthProvider> loggedInProviders() {
        return availableProviders().stream()
            .filter(PiInteractiveSession.AuthProvider::loggedIn)
            .toList();
    }

    private String promptForProvider(String title, List<PiInteractiveSession.AuthProvider> providers) throws IOException {
        if (providers.isEmpty()) {
            throw new IllegalStateException("No providers available");
        }
        if (providers.size() == 1) {
            return providers.getFirst().providerId();
        }

        appendLine(stdout, title);
        for (var index = 0; index < providers.size(); index += 1) {
            var provider = providers.get(index);
            appendLine(stdout, "  " + (index + 1) + ". " + provider.displayName() + " (" + provider.providerId() + ")");
        }
        var selection = readLine("Enter number or provider id: ");
        if (selection == null || selection.isBlank()) {
            throw new IllegalArgumentException("Provider selection is required");
        }

        var trimmed = selection.trim();
        try {
            var numericIndex = Integer.parseInt(trimmed);
            if (numericIndex >= 1 && numericIndex <= providers.size()) {
                return providers.get(numericIndex - 1).providerId();
            }
            throw new IllegalArgumentException("Invalid provider selection: " + trimmed);
        } catch (NumberFormatException ignored) {
        }

        return normalizeProviderId(trimmed);
    }

    private String promptForSecret(String provider) throws IOException {
        var secret = readLine("Enter credentials for " + provider + ": ");
        if (secret == null) {
            throw new IllegalArgumentException("Credentials are required");
        }
        return secret;
    }

    private String readLine(String prompt) throws IOException {
        stdout.append(prompt);
        return input.readLine();
    }

    private static boolean isHelpFlag(String value) {
        return "-h".equals(value) || "--help".equals(value);
    }

    private static String normalizeProviderId(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider is required");
        }
        return provider.trim();
    }

    private static String loginHelpText() {
        return """
            Usage:
              pi login [provider] [token]

            Save credentials for a provider.
            If provider or token is omitted, pi will prompt for it.
            For github and gitlab, omitting the token first tries gh/glab login.
            """;
    }

    private static String logoutHelpText() {
        return """
            Usage:
              pi logout [provider]

            Remove saved credentials for a provider.
            If provider is omitted, pi will prompt when needed.
            """;
    }

    private static String authHelpText() {
        return """
            Usage:
              pi auth list

            Show providers that currently have saved credentials.
            """;
    }

    private static String authListHelpText() {
        return """
            Usage:
              pi auth list

            Show providers that currently have saved credentials.
            """;
    }

    private static void appendLine(Appendable appendable, String line) throws IOException {
        appendable.append(line);
        appendable.append(System.lineSeparator());
    }
}
