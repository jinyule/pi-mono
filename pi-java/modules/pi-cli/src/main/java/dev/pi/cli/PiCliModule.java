package dev.pi.cli;

import dev.pi.agent.runtime.AgentLoopConfig;
import dev.pi.ai.PiAiClient;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.ThinkingLevel;
import dev.pi.ai.registry.ModelRegistry;
import dev.pi.extension.spi.ExtensionContext;
import dev.pi.extension.spi.ExtensionEventFailure;
import dev.pi.extension.spi.ExtensionResourceDiscoveryResult;
import dev.pi.extension.spi.ExtensionRuntimeSnapshot;
import dev.pi.extension.spi.ExtensionRuntime;
import dev.pi.extension.spi.ResourcesDiscoverEvent;
import dev.pi.session.AuthStorage;
import dev.pi.session.InstructionResourceLoader;
import dev.pi.session.PackageSourceDiscovery;
import dev.pi.session.SettingsManager;
import dev.pi.session.SessionManager;
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
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
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
    private final AuthStorage authStorage;
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
            PiCliKeybindingsLoader.createDefault(),
            AuthStorage.create()
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
        this(
            cwd,
            input,
            stdout,
            stderr,
            parser,
            aiClient,
            sessionFactory,
            terminalFactory,
            PiCliKeybindingsLoader.createDefault(),
            AuthStorage.create()
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
        Supplier<Terminal> terminalFactory,
        PiCliKeybindingsLoader keybindingsLoader
    ) {
        this(cwd, input, stdout, stderr, parser, aiClient, sessionFactory, terminalFactory, keybindingsLoader, AuthStorage.create());
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
        PiCliKeybindingsLoader keybindingsLoader,
        AuthStorage authStorage
    ) {
        this.cwd = Objects.requireNonNull(cwd, "cwd").toAbsolutePath().normalize();
        this.input = Objects.requireNonNull(input, "input");
        this.stdout = Objects.requireNonNull(stdout, "stdout");
        this.stderr = Objects.requireNonNull(stderr, "stderr");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.aiClient = Objects.requireNonNull(aiClient, "aiClient");
        this.terminalFactory = Objects.requireNonNull(terminalFactory, "terminalFactory");
        this.keybindingsLoader = Objects.requireNonNull(keybindingsLoader, "keybindingsLoader");
        this.authStorage = Objects.requireNonNull(authStorage, "authStorage");
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
        var loadedKeybindings = keybindingsLoader.load();
        EditorKeybindings.setGlobal(loadedKeybindings.editorKeybindings());
        PiAppKeybindings.setGlobal(loadedKeybindings.appKeybindings());
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
        var packageSourceDiscovery = new PackageSourceDiscovery(cwd);
        var extensionSources = args.noExtensions()
            ? List.<Path>of()
            : mergePaths(cwd,
                packageSourceDiscovery.resolve(
                    settingsManager.getGlobalPackages(),
                    settingsManager.getProjectPackages(),
                    PackageSourceDiscovery.ResourceType.EXTENSIONS
                ),
                args.extensions()
            );
        var extensionRuntime = extensionSources.isEmpty()
            ? null
            : new ExtensionRuntime(extensionSources);
        var extensionResourcesRef = new AtomicReference<>(extensionRuntime == null
            ? DiscoveredExtensionResources.empty()
            : discoverExtensionResources(
                extensionRuntime.snapshot(),
                sessionManager,
                settingsManager,
                ResourcesDiscoverEvent.Reason.STARTUP
            ));
        var themeLoader = new PiCliThemeLoader(cwd);
        var loadedThemes = themeLoader.load(
            resolveThemePaths(args, settingsManager, packageSourceDiscovery, extensionResourcesRef.get()),
            args.noThemes()
        );
        PiCliAnsi.setRegisteredThemes(loadedThemes.palettes());
        var instructionLoader = new InstructionResourceLoader(cwd);
        instructionLoader.reload();
        var scopedCycleModels = resolveScopedCycleModels(args.modelPatterns(), aiClient.modelRegistry());
        var cycleModels = resolveCycleModels(args, aiClient.modelRegistry(), scopedCycleModels);
        var model = resolveModel(args, aiClient.modelRegistry(), scopedCycleModels, settingsManager);
        var initialThinkingLevel = resolveInitialThinkingLevel(args, scopedCycleModels);
        var listedResources = resolveStartupResourcePaths(
            args,
            settingsManager,
            packageSourceDiscovery,
            extensionRuntime,
            extensionResourcesRef.get()
        );

        var builder = PiAgentSession.builder(
            model,
            sessionManager,
            settingsManager,
            instructionLoader.resources()
        )
            .authStorage(authStorage)
            .instructionResourceLoader(instructionLoader)
            .streamFunction(AgentLoopConfig.AssistantStreamFunction.fromClient(aiClient))
            .apiKeyProvider(args.apiKey() == null || args.apiKey().isBlank()
                ? provider -> java.util.concurrent.CompletableFuture.completedFuture(authStorage.getApiKey(provider))
                : null)
            .systemPrompt(args.systemPrompt())
            .appendSystemPrompt(args.appendSystemPrompt())
            .thinkingLevel(initialThinkingLevel)
            .apiKey(args.apiKey())
            .availableProviderCount(aiClient.modelRegistry().getProviders().size())
            .cycleModels(cycleModels, !args.modelPatterns().isEmpty() && !scopedCycleModels.isEmpty())
            .modelSelectorModels(allModels(aiClient.modelRegistry()))
            .customThemes(loadedThemes.availableThemes())
            .extensionPaths(listedResources.extensionPaths())
            .skillPaths(listedResources.skillPaths())
            .promptPaths(listedResources.promptPaths())
            .themeReloadAction(() -> {
                var reloadedThemes = themeLoader.load(resolveThemePaths(
                    args,
                    settingsManager,
                    packageSourceDiscovery,
                    extensionResourcesRef.get()
                ), args.noThemes());
                PiCliAnsi.setRegisteredThemes(reloadedThemes.palettes());
                return new PiAgentSession.ThemeReloadResult(
                    reloadedThemes.availableThemes(),
                    reloadedThemes.warnings()
                );
            })
            .startupResourcePathsReloadAction(() -> {
                var refreshed = resolveStartupResourcePaths(
                    args,
                    settingsManager,
                    packageSourceDiscovery,
                    extensionRuntime,
                    extensionResourcesRef.get()
                );
                return new PiAgentSession.StartupResourcePaths(
                    refreshed.skillPaths(),
                    refreshed.promptPaths()
                );
            })
            .closeAction(() -> {
                if (extensionRuntime != null) {
                    extensionRuntime.close();
                }
            });
        if (extensionRuntime != null) {
            builder.reloadAction(() -> {
                var snapshot = extensionRuntime.reload();
                var discovered = discoverExtensionResources(
                    snapshot,
                    sessionManager,
                    settingsManager,
                    ResourcesDiscoverEvent.Reason.RELOAD
                );
                extensionResourcesRef.set(discovered);
                return discovered.warnings();
            });
        }
        return builder.build();
    }

    private List<Path> resolveThemePaths(
        PiCliArgs args,
        SettingsManager settingsManager,
        PackageSourceDiscovery packageSourceDiscovery,
        DiscoveredExtensionResources extensionResources
    ) {
        if (args.noThemes()) {
            return mergePaths(cwd, args.themes());
        }
        return mergePaths(
            cwd,
            packageSourceDiscovery.resolve(
                settingsManager.getGlobalPackages(),
                settingsManager.getProjectPackages(),
                PackageSourceDiscovery.ResourceType.THEMES
            ),
            extensionResources.themePaths(),
            args.themes()
        );
    }

    private StartupResourceListing resolveStartupResourcePaths(
        PiCliArgs args,
        SettingsManager settingsManager,
        PackageSourceDiscovery packageSourceDiscovery,
        ExtensionRuntime extensionRuntime,
        DiscoveredExtensionResources extensionResources
    ) {
        var extensionPaths = extensionRuntime == null
            ? List.<String>of()
            : extensionRuntime.sources().stream().map(Path::toString).toList();
        var skillPaths = args.noSkills()
            ? List.<String>of()
            : mergePaths(
                cwd,
                packageSourceDiscovery.resolve(
                    settingsManager.getGlobalPackages(),
                    settingsManager.getProjectPackages(),
                    PackageSourceDiscovery.ResourceType.SKILLS
                ),
                extensionResources.skillPaths(),
                args.skills()
            ).stream().map(Path::toString).toList();
        var promptPaths = args.noPromptTemplates()
            ? List.<String>of()
            : mergePaths(
                cwd,
                packageSourceDiscovery.resolve(
                    settingsManager.getGlobalPackages(),
                    settingsManager.getProjectPackages(),
                    PackageSourceDiscovery.ResourceType.PROMPTS
                ),
                extensionResources.promptPaths(),
                args.promptTemplates()
            ).stream().map(Path::toString).toList();
        return new StartupResourceListing(extensionPaths, skillPaths, promptPaths);
    }

    private static DiscoveredExtensionResources discoverExtensionResources(
        ExtensionRuntimeSnapshot snapshot,
        SessionManager sessionManager,
        SettingsManager settingsManager,
        ResourcesDiscoverEvent.Reason reason
    ) {
        if (snapshot == null || !snapshot.resourceDiscovery().hasHandlers()) {
            return DiscoveredExtensionResources.fromResult(
                new ExtensionResourceDiscoveryResult(List.of(), List.of(), List.of(), List.of()),
                snapshot == null ? List.of() : snapshot.failures().stream().map(PiCliModule::formatExtensionFailure).toList()
            );
        }
        var result = snapshot.resourceDiscovery().discover(
            new ResourcesDiscoverEvent(Path.of(sessionManager.header().cwd()), reason),
            new PiCliExtensionContext(Path.of(sessionManager.header().cwd()), sessionManager, settingsManager)
        ).toCompletableFuture().join();
        var warnings = new ArrayList<String>();
        for (var failure : snapshot.failures()) {
            warnings.add(formatExtensionFailure(failure));
        }
        for (var failure : result.failures()) {
            warnings.add(formatExtensionEventFailure(failure));
        }
        return DiscoveredExtensionResources.fromResult(result, warnings);
    }

    @SafeVarargs
    private static List<Path> mergePaths(Path cwd, List<Path>... pathGroups) {
        var merged = new java.util.LinkedHashSet<Path>();
        for (var pathGroup : pathGroups) {
            for (var path : pathGroup == null ? List.<Path>of() : pathGroup) {
                if (path == null) {
                    continue;
                }
                merged.add(normalizePath(cwd, path));
            }
        }
        return List.copyOf(merged);
    }

    private static Path normalizePath(Path cwd, Path path) {
        return path.isAbsolute()
            ? path.toAbsolutePath().normalize()
            : cwd.resolve(path).toAbsolutePath().normalize();
    }

    static Model resolveModel(PiCliArgs args, ModelRegistry registry, List<PiAgentSession.CycleModel> scopedCycleModels) {
        return resolveModel(args, registry, scopedCycleModels, null);
    }

    static Model resolveModel(
        PiCliArgs args,
        ModelRegistry registry,
        List<PiAgentSession.CycleModel> scopedCycleModels,
        SettingsManager settingsManager
    ) {
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

        if (!args.modelPatterns().isEmpty() && !scopedCycleModels.isEmpty()) {
            return scopedCycleModels.getFirst().model();
        }

        if (settingsManager != null) {
            var defaultModel = resolveDefaultModel(registry, settingsManager);
            if (defaultModel != null) {
                return defaultModel;
            }
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

    private static Model resolveDefaultModel(ModelRegistry registry, SettingsManager settingsManager) {
        var defaultProvider = settingsManager.effective().getString("/defaultProvider");
        var defaultModel = settingsManager.effective().getString("/defaultModel");
        if (defaultProvider != null && !defaultProvider.isBlank() && defaultModel != null && !defaultModel.isBlank()) {
            try {
                return registry.require(defaultProvider, defaultModel);
            } catch (IllegalArgumentException | IllegalStateException ignored) {
            }
        }
        if (defaultModel != null && !defaultModel.isBlank()) {
            var matches = allModels(registry).stream()
                .filter(model -> model.id().equals(defaultModel))
                .toList();
            if (matches.size() == 1) {
                return matches.getFirst();
            }
        }
        return null;
    }

    private static List<Model> allModels(ModelRegistry registry) {
        var models = new ArrayList<Model>();
        for (var provider : registry.getProviders()) {
            models.addAll(registry.getModels(provider));
        }
        return List.copyOf(models);
    }

    static List<PiAgentSession.CycleModel> resolveCycleModels(
        PiCliArgs args,
        ModelRegistry registry,
        List<PiAgentSession.CycleModel> scopedCycleModels
    ) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(scopedCycleModels, "scopedCycleModels");
        if (!args.modelPatterns().isEmpty() && !scopedCycleModels.isEmpty()) {
            return scopedCycleModels;
        }
        return allModels(registry).stream()
            .map(model -> new PiAgentSession.CycleModel(model, null))
            .toList();
    }

    static List<PiAgentSession.CycleModel> resolveScopedCycleModels(List<String> patterns, ModelRegistry registry) {
        Objects.requireNonNull(patterns, "patterns");
        Objects.requireNonNull(registry, "registry");
        var availableModels = allModels(registry);
        var scopedModels = new ArrayList<PiAgentSession.CycleModel>();
        for (var rawPattern : patterns) {
            if (rawPattern == null || rawPattern.isBlank()) {
                continue;
            }
            var parsedPattern = parseCycleModelPattern(rawPattern);
            var matches = parsedPattern.glob()
                ? availableModels.stream().filter(model -> matchesGlob(parsedPattern.pattern(), model)).toList()
                : matchExactOrContains(parsedPattern.pattern(), availableModels);
            for (var model : matches) {
                if (scopedModels.stream().anyMatch(existing -> sameModel(existing.model(), model))) {
                    continue;
                }
                scopedModels.add(new PiAgentSession.CycleModel(model, parsedPattern.thinkingLevel()));
            }
        }
        return List.copyOf(scopedModels);
    }

    private static List<Model> matchExactOrContains(String pattern, List<Model> availableModels) {
        var exactMatches = availableModels.stream()
            .filter(model -> fullModelId(model).equalsIgnoreCase(pattern) || model.id().equalsIgnoreCase(pattern))
            .toList();
        if (!exactMatches.isEmpty()) {
            return exactMatches;
        }
        var normalized = pattern.toLowerCase();
        return availableModels.stream()
            .filter(model -> fullModelId(model).toLowerCase().contains(normalized) || model.id().toLowerCase().contains(normalized))
            .toList();
    }

    private static ParsedCycleModelPattern parseCycleModelPattern(String rawPattern) {
        var colonIndex = rawPattern.lastIndexOf(':');
        if (colonIndex <= 0 || colonIndex == rawPattern.length() - 1) {
            return new ParsedCycleModelPattern(rawPattern, null, hasGlob(rawPattern));
        }
        var suffix = rawPattern.substring(colonIndex + 1);
        try {
            var thinkingLevel = PiCliThinkingLevel.fromValue(suffix).toReasoningLevel();
            var pattern = rawPattern.substring(0, colonIndex);
            return new ParsedCycleModelPattern(pattern, thinkingLevel, hasGlob(pattern));
        } catch (IllegalArgumentException ignored) {
            return new ParsedCycleModelPattern(rawPattern, null, hasGlob(rawPattern));
        }
    }

    private static boolean hasGlob(String pattern) {
        return pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0 || pattern.indexOf('[') >= 0;
    }

    private static boolean matchesGlob(String pattern, Model model) {
        var regex = globToRegex(pattern);
        return regex.matcher(fullModelId(model)).matches() || regex.matcher(model.id()).matches();
    }

    private static Pattern globToRegex(String pattern) {
        var regex = new StringBuilder("^");
        for (var index = 0; index < pattern.length(); index++) {
            var ch = pattern.charAt(index);
            switch (ch) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '[', ']' -> regex.append(ch);
                case '\\', '.', '(', ')', '+', '{', '}', '^', '$', '|' -> regex.append('\\').append(ch);
                default -> regex.append(ch);
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    private static ThinkingLevel resolveInitialThinkingLevel(PiCliArgs args, List<PiAgentSession.CycleModel> scopedCycleModels) {
        if (args.thinking() != null) {
            return args.thinking().toReasoningLevel();
        }
        if (args.provider() != null || args.model() != null || args.modelPatterns().isEmpty() || scopedCycleModels.isEmpty()) {
            return null;
        }
        return scopedCycleModels.getFirst().thinkingLevel();
    }

    private static String fullModelId(Model model) {
        return model.provider() + "/" + model.id();
    }

    private static boolean sameModel(Model left, Model right) {
        return left.provider().equals(right.provider()) && left.id().equals(right.id());
    }

    private record ParsedCycleModelPattern(
        String pattern,
        ThinkingLevel thinkingLevel,
        boolean glob
    ) {
    }

    private static String formatExtensionFailure(dev.pi.extension.spi.ExtensionLoadFailure failure) {
        return "%s: %s".formatted(failure.source().getFileName(), failure.message());
    }

    private static String formatExtensionEventFailure(ExtensionEventFailure failure) {
        return "%s: %s".formatted(failure.extensionId(), rootMessage(failure.cause()));
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

    private static String rootMessage(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record StartupResourceListing(
        List<String> extensionPaths,
        List<String> skillPaths,
        List<String> promptPaths
    ) {
        private StartupResourceListing {
            extensionPaths = List.copyOf(extensionPaths);
            skillPaths = List.copyOf(skillPaths);
            promptPaths = List.copyOf(promptPaths);
        }
    }

    private record DiscoveredExtensionResources(
        List<Path> skillPaths,
        List<Path> promptPaths,
        List<Path> themePaths,
        List<String> warnings
    ) {
        private DiscoveredExtensionResources {
            skillPaths = List.copyOf(skillPaths);
            promptPaths = List.copyOf(promptPaths);
            themePaths = List.copyOf(themePaths);
            warnings = List.copyOf(warnings);
        }

        private static DiscoveredExtensionResources empty() {
            return new DiscoveredExtensionResources(List.of(), List.of(), List.of(), List.of());
        }

        private static DiscoveredExtensionResources fromResult(
            ExtensionResourceDiscoveryResult result,
            List<String> warnings
        ) {
            return new DiscoveredExtensionResources(
                result.skillPaths().stream().map(dev.pi.extension.spi.ExtensionResourcePath::resolvedPath).toList(),
                result.promptPaths().stream().map(dev.pi.extension.spi.ExtensionResourcePath::resolvedPath).toList(),
                result.themePaths().stream().map(dev.pi.extension.spi.ExtensionResourcePath::resolvedPath).toList(),
                warnings
            );
        }
    }

    private record PiCliExtensionContext(
        Path cwd,
        SessionManager sessionManager,
        SettingsManager settingsManager
    ) implements ExtensionContext {
    }
}
