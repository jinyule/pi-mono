package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.ParameterException;

class PiCliParserTest {
    private final PiCliParser parser = new PiCliParser();

    @Test
    void defaultsToInteractiveAndSeparatesFileArgs() {
        var args = parser.parse("@prompt.md", "Review", "the repo");

        assertThat(args.mode()).isEqualTo(PiCliMode.INTERACTIVE);
        assertThat(args.fileArgs()).containsExactly(Path.of("prompt.md"));
        assertThat(args.messages()).containsExactly("Review", "the repo");
        assertThat(args.listModelsRequested()).isFalse();
    }

    @Test
    void parsesRunOptionsForPrintMode() {
        var args = parser.parse(
            "--print",
            "--provider", "openai",
            "--model", "gpt-4o-mini",
            "--api-key", "test-key",
            "--system-prompt", "system",
            "--append-system-prompt", "append",
            "--thinking", "high",
            "--continue",
            "--resume",
            "--session", "session.jsonl",
            "--session-dir", "sessions",
            "--models", "anthropic/*,openai/*",
            "--tools", "read,grep",
            "--extension", "ext.jar",
            "--skill", "skills",
            "--prompt-template", "prompts",
            "--theme", "theme.json",
            "--offline",
            "--verbose",
            "Summarize status"
        );

        assertThat(args.mode()).isEqualTo(PiCliMode.PRINT);
        assertThat(args.provider()).isEqualTo("openai");
        assertThat(args.model()).isEqualTo("gpt-4o-mini");
        assertThat(args.apiKey()).isEqualTo("test-key");
        assertThat(args.systemPrompt()).isEqualTo("system");
        assertThat(args.appendSystemPrompt()).isEqualTo("append");
        assertThat(args.thinking()).isEqualTo(PiCliThinkingLevel.HIGH);
        assertThat(args.continueSession()).isTrue();
        assertThat(args.resumeRequested()).isTrue();
        assertThat(args.sessionPath()).isEqualTo(Path.of("session.jsonl"));
        assertThat(args.sessionDirectory()).isEqualTo(Path.of("sessions"));
        assertThat(args.modelPatterns()).containsExactly("anthropic/*", "openai/*");
        assertThat(args.tools()).containsExactly("read", "grep");
        assertThat(args.extensions()).containsExactly(Path.of("ext.jar"));
        assertThat(args.skills()).containsExactly(Path.of("skills"));
        assertThat(args.promptTemplates()).containsExactly(Path.of("prompts"));
        assertThat(args.themes()).containsExactly(Path.of("theme.json"));
        assertThat(args.offline()).isTrue();
        assertThat(args.verbose()).isTrue();
        assertThat(args.messages()).containsExactly("Summarize status");
    }

    @Test
    void parsesListModelsWithOptionalQuery() {
        var args = parser.parse("--list-models", "sonnet");

        assertThat(args.mode()).isEqualTo(PiCliMode.INTERACTIVE);
        assertThat(args.listModelsRequested()).isTrue();
        assertThat(args.listModelsQuery()).isEqualTo("sonnet");
    }

    @Test
    void parsesExportInputPath() {
        var args = parser.parse("--export", "session.jsonl", "output.html");

        assertThat(args.exportRequested()).isTrue();
        assertThat(args.exportInputPath()).isEqualTo(Path.of("session.jsonl"));
        assertThat(args.messages()).containsExactly("output.html");
    }

    @Test
    void acceptsLegacyTextAliasForPrintMode() {
        var args = parser.parse("--mode", "text", "Explain this");

        assertThat(args.mode()).isEqualTo(PiCliMode.PRINT);
        assertThat(args.messages()).containsExactly("Explain this");
    }

    @Test
    void rejectsConflictingPrintSelectors() {
        assertThatThrownBy(() -> parser.parse("--print", "--mode", "json"))
            .isInstanceOf(ParameterException.class)
            .hasMessageContaining("--print");
    }

    @Test
    void preservesUnmatchedArgumentsForFutureExtensionFlags() {
        var args = parser.parse("--custom-flag", "value", "--another-flag", "Hello");

        assertThat(args.unmatchedArguments()).containsExactly("--custom-flag", "--another-flag");
        assertThat(args.messages()).containsExactly("value", "Hello");
    }

    @Test
    void helpTextMentionsConfigCommand() {
        assertThat(parser.helpText())
            .contains("pi login [provider] [token]")
            .contains("import from gh/glab")
            .contains("pi logout [provider]")
            .contains("pi auth list")
            .contains("config                         Configure package-provided resources");
    }
}
