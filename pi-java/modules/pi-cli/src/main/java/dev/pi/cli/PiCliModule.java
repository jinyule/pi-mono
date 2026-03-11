package dev.pi.cli;

import dev.pi.agent.runtime.AgentLoopConfig;
import dev.pi.ai.PiAiClient;
import dev.pi.ai.model.Model;
import dev.pi.ai.registry.ModelRegistry;
import dev.pi.extension.spi.ExtensionRuntime;
import dev.pi.session.InstructionResourceLoader;
import dev.pi.session.SettingsManager;
import dev.pi.tui.EditorKeybindings;
import dev.pi.tui.ProcessTerminal;
import dev.pi.tui.Terminal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class PiCliModule {
    private static final String FALLBACK_VERSION = "0.1.0-SNAPSHOT";

    private final Path cwd;
    private final Reader input;
    private final Appendable stdout;
    private final Appendable stderr;
    private final PiCliParser parser;
    private final PiAiClient aiClient;
    private final PiCliApplication.SessionFactory sessionFactory;
    private final Supplier<Terminal> terminalFactory;
    private final PiCliKeybindingsLoader keybindingsLoader;
    private final PiCliApplication application;

    public PiCliModule() {
        this(
            Path.of("").toAbsolutePath().normalize(),
            new InputStreamReader(System.in, StandardCharsets.UTF_8),
            System.out,
            System.err,
            new PiCliParser(),
            PiAiClient.createDefault(),
            null,
            ProcessTerminal::new,
            PiCliKeybindingsLoader.createDefault()
        );
    }

    PiCliModule(
        Path cwd,
        Reader input,
        Appendable stdout,
        Appendable stderr,
        PiCliParser parser,
        PiAiClient aiClient,
        PiCliApplication.SessionFactory sessionFactory,
        Supplier<Terminal> terminalFactory
    ) {
        this(cwd, input, stdout, stderr, parser, aiClient, sessionFactory, terminalFactory, PiCliKeybindingsLoader.createDefault());
    }

    PiCliModule(
        Path cwd,
        Reader input,
        Appendable stdout,
        Appendable stderr,
        PiCliParser parser,
        PiAiClient aiClient,
        PiCliApplication.SessionFactory sessionFactory,
        Supplier<Terminal> terminalFactory,
        PiCliKeybindingsLoader keybindingsLoader
    ) {
        this.cwd = Objects.requireNonNull(cwd, "cwd").toAbsolutePath().normalize();
        this.input = Objects.requireNonNull(input, "input");
        this.stdout = Objects.requireNonNull(stdout, "stdout");
        this.stderr = Objects.requireNonNull(stderr, "stderr");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.aiClient = Objects.requireNonNull(aiClient, "aiClient");
        this.terminalFactory = Objects.requireNonNull(terminalFactory, "terminalFactory");
        this.keybindingsLoader = Objects.requireNonNull(keybindingsLoader, "keybindingsLoader");
        this.sessionFactory = sessionFactory == null ? this::createDefaultSession : sessionFactory;
        this.application = PiCliApplication.builder(this.sessionFactory)
            .parser(this.parser)
            .helpHandler(this::runHelp)
            .versionHandler(this::runVersion)
            .listModelsHandler(this::runListModels)
            .exportHandler(this::runExport)
            .interactiveHandler(this::runInteractive)
            .printHandler(this::runPrint)
            .jsonHandler(this::runJson)
            .rpcHandler(this::runRpc)
            .build();
    }

    public String id() {
        return "pi-cli";
    }

    public String description() {
        return "CLI entrypoint and runtime orchestration across interactive and non-interactive modes.";
    }

    public PiCliParser parser() {
        return parser;
    }

    public PiCliApplication application() {
        return application;
    }

    public CompletionStage<Void> run(String... argv) {
        EditorKeybindings.setGlobal(keybindingsLoader.load());
        return application.run(argv);
    }

    private CompletionStage<Void> runListModels(PiCliArgs args) {
        new PiListModelsCommand(aiClient.modelRegistry(), stdout).run(args.listModelsQuery());
        return CompletableFuture.completedFuture(null);
    }

    private CompletionStage<Void> runHelp(PiCliArgs args) {
        return appendLine(stdout, parser.helpText());
    }

    private CompletionStage<Void> runVersion(PiCliArgs args) {
        return appendLine(stdout, "pi-java " + resolveVersion());
    }

    private CompletionStage<Void> runExport(PiCliArgs args) {
        try {
            var outputPath = args.messages().isEmpty() ? null : Path.of(args.messages().getFirst());
            var exportedPath = new PiExportCommand().export(args.exportInputPath(), outputPath);
            stdout.append("Exported to: ").append(exportedPath.toString()).append(System.lineSeparator());
            return CompletableFuture.completedFuture(null);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private CompletionStage<Void> runInteractive(PiCliArgs args, PiInteractiveSession session) {
        try {
            var initialPrompt = PiCliPromptFactory.createInitialPrompt(args, cwd);
            var mode = new PiInteractiveMode(session, terminalFactory.get());
            var done = new CompletableFuture<Void>();
            var shutdownHook = new Thread(mode::close);
            mode.setOnStop(() -> {
                removeShutdownHook(shutdownHook);
                done.complete(null);
            });
            mode.start();
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            var startup = initialPrompt == null ? CompletableFuture.<Void>completedFuture(null) : session.prompt(initialPrompt);
            startup = startup.whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    mode.close();
                }
            });
            return startup.thenCompose(ignored -> done);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private CompletionStage<Void> runPrint(PiCliArgs args, PiInteractiveSession session) {
        try {
            return new PiPrintMode(session, stdout, stderr)
                .run(PiCliPromptFactory.createInitialPrompt(args, cwd))
                .thenAccept(ignored -> {
                });
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private CompletionStage<Void> runJson(PiCliArgs args, PiInteractiveSession session) {
        try {
            return new PiJsonMode(session, stdout).run(PiCliPromptFactory.createInitialPrompt(args, cwd));
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private CompletionStage<Void> runRpc(PiCliArgs args, PiInteractiveSession session) {
        if (!args.fileArgs().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("@file arguments are not supported in RPC mode"));
        }
        var rpcMode = new PiRpcMode(session, stdout);
        try {
            var reader = input instanceof BufferedReader bufferedReader ? bufferedReader : new BufferedReader(input);
            rpcMode.start();
            String line;
            while ((line = reader.readLine()) != null) {
                rpcMode.handleCommand(line).toCompletableFuture().join();
            }
            rpcMode.close();
            return CompletableFuture.completedFuture(null);
        } catch (IOException exception) {
            rpcMode.close();
            return CompletableFuture.failedFuture(exception);
        }
    }

    private PiInteractiveSession createDefaultSession(PiCliArgs args) throws Exception {
        var sessionManager = new PiCliSessionResolver(cwd).resolve(args);
        var settingsManager = SettingsManager.create(cwd);
        var instructionLoader = new InstructionResourceLoader(cwd);
        instructionLoader.reload();
        var extensionRuntime = args.noExtensions() || args.extensions().isEmpty()
            ? null
            : new ExtensionRuntime(args.extensions());
        var model = resolveModel(args, aiClient.modelRegistry());

        var builder = PiAgentSession.builder(
            model,
            sessionManager,
            settingsManager,
            instructionLoader.resources()
        )
            .instructionResourceLoader(instructionLoader)
            .streamFunction(AgentLoopConfig.AssistantStreamFunction.fromClient(aiClient))
            .systemPrompt(args.systemPrompt())
            .appendSystemPrompt(args.appendSystemPrompt())
            .thinkingLevel(args.thinking() == null ? null : args.thinking().toReasoningLevel())
            .apiKey(args.apiKey());
        if (extensionRuntime != null) {
            builder.reloadAction(() -> extensionRuntime.reload().failures().stream().map(PiCliModule::formatExtensionFailure).toList());
        }
        return builder.build();
    }

    private static Model resolveModel(PiCliArgs args, ModelRegistry registry) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(registry, "registry");

        if (args.provider() != null && args.model() != null) {
            return registry.require(args.provider(), args.model());
        }

        if (args.provider() != null) {
            var providerModels = registry.getModels(args.provider());
            if (providerModels.size() == 1) {
                return providerModels.getFirst();
            }
            if (providerModels.isEmpty()) {
                throw new IllegalStateException("No models registered for provider: " + args.provider());
            }
            throw new IllegalStateException("Provider %s has multiple models; pass --model explicitly".formatted(args.provider()));
        }

        if (args.model() != null) {
            var matches = allModels(registry).stream()
                .filter(model -> model.id().equals(args.model()))
                .toList();
            if (matches.size() == 1) {
                return matches.getFirst();
            }
            if (matches.isEmpty()) {
                throw new IllegalStateException("No model registered with id: " + args.model());
            }
            throw new IllegalStateException("Model id %s is ambiguous; pass --provider as well".formatted(args.model()));
        }

        var models = allModels(registry);
        if (models.size() == 1) {
            return models.getFirst();
        }
        if (models.isEmpty()) {
            throw new IllegalStateException("No models registered. Configure the ModelRegistry before running the CLI.");
        }
        throw new IllegalStateException("Multiple models are registered; pass --provider and --model.");
    }

    private static List<Model> allModels(ModelRegistry registry) {
        var models = new ArrayList<Model>();
        for (var provider : registry.getProviders()) {
            models.addAll(registry.getModels(provider));
        }
        return List.copyOf(models);
    }

    private static String formatExtensionFailure(dev.pi.extension.spi.ExtensionLoadFailure failure) {
        return "%s: %s".formatted(failure.source().getFileName(), failure.message());
    }

    private static CompletionStage<Void> appendLine(Appendable output, String text) {
        try {
            output.append(text);
            output.append(System.lineSeparator());
            return CompletableFuture.completedFuture(null);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static String resolveVersion() {
        var implementationVersion = PiCliModule.class.getPackage().getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank() ? FALLBACK_VERSION : implementationVersion;
    }

    private static void removeShutdownHook(Thread shutdownHook) {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException | IllegalArgumentException ignored) {
        }
    }
}
