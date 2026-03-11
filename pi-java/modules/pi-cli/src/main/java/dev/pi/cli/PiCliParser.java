package dev.pi.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import picocli.CommandLine;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Unmatched;

public final class PiCliParser {
    private static final String LIST_MODELS_PRESENT = "__present__";
    private static final Set<String> FLAG_OPTIONS = Set.of(
        "-h", "--help",
        "-v", "-V", "--version",
        "-p", "--print",
        "-c", "--continue",
        "-r", "--resume",
        "--no-session",
        "--no-tools",
        "--no-extensions",
        "--no-skills",
        "--no-prompt-templates",
        "--no-themes",
        "--offline",
        "--verbose"
    );
    private static final Set<String> VALUE_OPTIONS = Set.of(
        "--mode",
        "--export",
        "--provider",
        "--model",
        "--api-key",
        "--system-prompt",
        "--append-system-prompt",
        "--thinking",
        "--session",
        "--session-dir",
        "--models",
        "--tools",
        "-e", "--extension",
        "--skill",
        "--prompt-template",
        "--theme"
    );
    private static final String HELP_TEXT = """
        pi-java - AI coding assistant

        Usage:
          pi [options] [@files...] [messages...]

        Options:
          --provider <name>              Provider name
          --model <id>                   Model id or pattern
          --api-key <key>                API key override
          --system-prompt <text>         Explicit system prompt
          --append-system-prompt <text>  Extra system prompt text
          --thinking <level>             Thinking level: off|minimal|low|medium|high|xhigh
          --print, -p                    Run once and print final assistant output
          --mode <mode>                  interactive|print|json|rpc
          --continue, -c                 Continue the most recent session
          --resume, -r                   Open the session picker
          --session <path>               Use a specific session file
          --session-dir <dir>            Session directory override
          --no-session                   Disable session persistence
          --models <patterns>            Model cycling filter list
          --tools <tools>                Comma-separated tool allowlist
          --no-tools                     Disable built-in tools
          --extension, -e <path>         Load an extension file
          --no-extensions                Disable extension discovery
          --skill <path>                 Load a skill file or directory
          --no-skills                    Disable skill discovery
          --prompt-template <path>       Load a prompt template file or directory
          --no-prompt-templates          Disable prompt template discovery
          --theme <path>                 Load a theme file or directory
          --no-themes                    Disable theme discovery
          --list-models [search]         List available models
          --export <session.jsonl>       Export a session to HTML
          --offline                      Disable startup network operations
          --verbose                      Force verbose startup
          --help, -h                     Show this help
          --version, -v                  Show version
        """;

    public PiCliArgs parse(String... arguments) {
        Objects.requireNonNull(arguments, "arguments");
        return parse(List.of(arguments));
    }

    public PiCliArgs parse(List<String> arguments) {
        Objects.requireNonNull(arguments, "arguments");
        var command = new CommandSpec();
        var commandLine = new CommandLine(command);
        commandLine.setExpandAtFiles(false);
        commandLine.setUnmatchedArgumentsAllowed(true);
        commandLine.parseArgs(arguments.toArray(String[]::new));

        var mode = resolveMode(command, commandLine);
        var tokenization = tokenize(arguments);
        var fileArgs = new ArrayList<Path>();
        var messages = new ArrayList<String>();
        for (var input : tokenization.inputs()) {
            if (input.startsWith("@") && input.length() > 1) {
                fileArgs.add(Path.of(input.substring(1)));
            } else {
                messages.add(input);
            }
        }

        var listModelsRequested = command.listModels != null;
        var listModelsQuery = listModelsRequested && !LIST_MODELS_PRESENT.equals(command.listModels)
            ? command.listModels
            : null;
        var exportRequested = command.exportPath != null;

        return new PiCliArgs(
            mode,
            command.helpRequested,
            command.versionRequested,
            command.continueSession,
            command.resumeRequested,
            command.noSession,
            command.noTools,
            command.noExtensions,
            command.noSkills,
            command.noPromptTemplates,
            command.noThemes,
            listModelsRequested,
            exportRequested,
            command.offline,
            command.verbose,
            listModelsQuery,
            command.exportPath,
            command.provider,
            command.model,
            command.apiKey,
            command.systemPrompt,
            command.appendSystemPrompt,
            command.thinking,
            command.sessionPath,
            command.sessionDirectory,
            command.modelPatterns,
            command.tools,
            command.extensions,
            command.skills,
            command.promptTemplates,
            command.themes,
            fileArgs,
            messages,
            tokenization.unmatchedArguments()
        );
    }

    public String helpText() {
        return HELP_TEXT;
    }

    private static PiCliMode resolveMode(CommandSpec command, CommandLine commandLine) {
        if (command.print && command.mode != null && command.mode != PiCliMode.PRINT) {
            throw new ParameterException(commandLine, "`--print` only supports `print` mode.");
        }
        if (command.print) {
            return PiCliMode.PRINT;
        }
        return command.mode == null ? PiCliMode.INTERACTIVE : command.mode;
    }

    private static Tokenization tokenize(List<String> arguments) {
        var inputs = new ArrayList<String>();
        var unmatched = new ArrayList<String>();
        for (int index = 0; index < arguments.size(); index++) {
            var argument = arguments.get(index);
            if (FLAG_OPTIONS.contains(argument)) {
                continue;
            }
            if (VALUE_OPTIONS.contains(argument)) {
                index = consumeOptionValue(arguments, index);
                continue;
            }
            if ("--list-models".equals(argument)) {
                index = consumeOptionalValue(arguments, index);
                continue;
            }
            if (argument.startsWith("-")) {
                unmatched.add(argument);
                continue;
            }
            inputs.add(argument);
        }
        return new Tokenization(inputs, unmatched);
    }

    private static int consumeOptionValue(List<String> arguments, int index) {
        if (index + 1 < arguments.size()) {
            return index + 1;
        }
        return index;
    }

    private static int consumeOptionalValue(List<String> arguments, int index) {
        if (index + 1 < arguments.size()) {
            var next = arguments.get(index + 1);
            if (!next.startsWith("-")) {
                return index + 1;
            }
        }
        return index;
    }

    static final class CommandSpec {
        @Option(names = {"-h", "--help"}, usageHelp = true)
        boolean helpRequested;

        @Option(names = {"-v", "-V", "--version"}, versionHelp = true)
        boolean versionRequested;

        @Option(names = {"-p", "--print"})
        boolean print;

        @Option(names = "--mode", converter = PiCliModeConverter.class)
        PiCliMode mode;

        @Option(names = {"-c", "--continue"})
        boolean continueSession;

        @Option(names = {"-r", "--resume"})
        boolean resumeRequested;

        @Option(names = "--provider")
        String provider;

        @Option(names = "--model")
        String model;

        @Option(names = "--api-key")
        String apiKey;

        @Option(names = "--system-prompt")
        String systemPrompt;

        @Option(names = "--append-system-prompt")
        String appendSystemPrompt;

        @Option(names = "--thinking", converter = PiCliThinkingLevelConverter.class)
        PiCliThinkingLevel thinking;

        @Option(names = "--no-session")
        boolean noSession;

        @Option(names = "--session")
        Path sessionPath;

        @Option(names = "--session-dir")
        Path sessionDirectory;

        @Option(names = "--models", split = ",")
        List<String> modelPatterns = new ArrayList<>();

        @Option(names = "--no-tools")
        boolean noTools;

        @Option(names = "--tools", split = ",")
        List<String> tools = new ArrayList<>();

        @Option(names = {"-e", "--extension"})
        List<Path> extensions = new ArrayList<>();

        @Option(names = "--no-extensions")
        boolean noExtensions;

        @Option(names = "--skill")
        List<Path> skills = new ArrayList<>();

        @Option(names = "--no-skills")
        boolean noSkills;

        @Option(names = "--prompt-template")
        List<Path> promptTemplates = new ArrayList<>();

        @Option(names = "--no-prompt-templates")
        boolean noPromptTemplates;

        @Option(names = "--theme")
        List<Path> themes = new ArrayList<>();

        @Option(names = "--no-themes")
        boolean noThemes;

        @Option(names = "--list-models", arity = "0..1", fallbackValue = LIST_MODELS_PRESENT)
        String listModels;

        @Option(names = "--export")
        Path exportPath;

        @Option(names = "--offline")
        boolean offline;

        @Option(names = "--verbose")
        boolean verbose;

        @Parameters(arity = "0..*")
        List<String> inputs = new ArrayList<>();

        @Unmatched
        List<String> unmatchedArguments = new ArrayList<>();
    }

    record Tokenization(List<String> inputs, List<String> unmatchedArguments) {}

    static final class PiCliModeConverter implements ITypeConverter<PiCliMode> {
        @Override
        public PiCliMode convert(String value) {
            return PiCliMode.fromValue(value);
        }
    }

    static final class PiCliThinkingLevelConverter implements ITypeConverter<PiCliThinkingLevel> {
        @Override
        public PiCliThinkingLevel convert(String value) {
            return PiCliThinkingLevel.fromValue(value);
        }
    }
}
