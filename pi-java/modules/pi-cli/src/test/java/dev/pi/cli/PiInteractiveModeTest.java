package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.agent.runtime.AgentEvent;
import dev.pi.agent.runtime.AgentMessage;
import dev.pi.agent.runtime.AgentMessages;
import dev.pi.agent.runtime.AgentState;
import dev.pi.agent.runtime.AgentTool;
import dev.pi.ai.model.ImageContent;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.ThinkingLevel;
import dev.pi.ai.model.ThinkingContent;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.Subscription;
import dev.pi.session.SessionEntry;
import dev.pi.session.SessionManager;
import dev.pi.session.SessionTreeNode;
import dev.pi.tui.InputHandler;
import dev.pi.tui.Terminal;
import dev.pi.tui.VirtualTerminal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiInteractiveModeTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void rendersHeaderAndSubmitsPromptThroughTui() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(60, 12);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("pi-java interactive")
            .contains("model: openai/test-model")
            .contains("Ready")
            .contains(">");

        terminal.sendInput("Hello");
        terminal.sendInput("\r");

        assertThat(session.prompts).containsExactly("Hello");
        assertThat(String.join("\n", terminal.getViewport()))
            .contains("You: Hello")
            .contains("Assistant: Ack: Hello");

        mode.stop();
    }

    @Test
    void hidesHeaderWhenQuietStartupIsEnabled() {
        var session = new FakeSession().withQuietStartup(true);
        var terminal = new VirtualTerminal(60, 12);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();

        assertThat(String.join("\n", terminal.getViewport()))
            .doesNotContain("pi-java interactive")
            .doesNotContain("model: openai/test-model")
            .contains("Ready")
            .contains(">");

        mode.stop();
    }

    @Test
    void rendersPromptWithConfiguredEditorPadding() {
        var session = new FakeSession().withEditorPaddingX(2);
        var terminal = new RecordingTerminal(60, 12);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();

        waitFor(() -> terminal.output().contains(">   "));
        assertThat(terminal.output()).contains(">   ");

        mode.stop();
    }

    @Test
    void updatesAnsiPaletteWhenThemeChanges() {
        var session = new FakeSession().withContextWindow(16);
        var terminal = new RecordingTerminal(80, 14);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        waitFor(() -> terminal.output().contains("\u001b[90m0.0%/16 (auto)\u001b[0m"));

        session.updateSetting("theme", "light");

        waitFor(() -> terminal.output().contains("\u001b[2;30m0.0%/16 (auto)\u001b[0m"));
        assertThat(terminal.output()).contains("\u001b[2;30m0.0%/16 (auto)\u001b[0m");

        mode.stop();
    }

    @Test
    void rendersHeaderKeyHintsWhenRowsAllow() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(
                PiAppAction.INTERRUPT, java.util.List.of("alt+x", "ctrl+c"),
                PiAppAction.CLEAR, java.util.List.of("alt+c"),
                PiAppAction.EXIT, java.util.List.of("alt+q"),
                PiAppAction.SELECT_MODEL, java.util.List.of("alt+l", "ctrl+l"),
                PiAppAction.EXPAND_TOOLS, java.util.List.of("alt+h"),
                PiAppAction.TOGGLE_THINKING, java.util.List.of("alt+t"),
                PiAppAction.PASTE_IMAGE, java.util.List.of("alt+z")
            )));

            mode.start();

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("alt+x/ctrl+c interrupt")
            .contains("alt+c clear")
            .contains("alt+q empty exit")
            .contains("alt+l/ctrl+l model")
            .contains("alt+h tools")
            .contains("alt+t thinking")
            .contains("alt+z paste image");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void stylesHeaderKeyHintsWithSharedKeyFormatter() {
        var session = new FakeSession();
        var terminal = new RecordingTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(
                PiAppAction.INTERRUPT, java.util.List.of("alt+x", "ctrl+c"),
                PiAppAction.CLEAR, java.util.List.of("alt+c"),
                PiAppAction.EXIT, java.util.List.of("alt+q"),
                PiAppAction.SELECT_MODEL, java.util.List.of("alt+l", "ctrl+l"),
                PiAppAction.EXPAND_TOOLS, java.util.List.of("alt+h"),
                PiAppAction.TOGGLE_THINKING, java.util.List.of("alt+t"),
                PiAppAction.PASTE_IMAGE, java.util.List.of("alt+z")
            )));

            mode.start();
            waitFor(() -> terminal.output().contains("paste image"));

        assertThat(terminal.output())
            .contains("\u001b[2;37malt+x/ctrl+c\u001b[0m\u001b[90m interrupt\u001b[0m")
            .contains("\u001b[90m \u2022 \u001b[0m")
            .contains("\u001b[2;37malt+l/ctrl+l\u001b[0m\u001b[90m model\u001b[0m")
            .contains("\u001b[2;37malt+z\u001b[0m\u001b[90m paste image\u001b[0m");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void handlesCopySlashCommand() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(60, 12);
        var copied = new StringBuilder();
        var mode = new PiInteractiveMode(session, terminal, new PiCopyCommand(session, copied::append));

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");
        terminal.sendInput("/copy");
        terminal.sendInput("\r");

        assertThat(session.prompts).containsExactly("Hello");
        assertThat(copied.toString()).isEqualTo("Ack: Hello");
        assertThat(String.join("\n", terminal.getViewport())).contains("Copied last agent message to clipboard");

        mode.stop();
    }

    @Test
    void showsPlainCopyEmptyStateWithoutErrorPrefix() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 12);
        var copied = new StringBuilder();
        var mode = new PiInteractiveMode(session, terminal, new PiCopyCommand(session, copied::append));

        mode.start();
        terminal.sendInput("/copy");
        terminal.sendInput("\r");

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("No agent messages to copy yet.")
            .doesNotContain("Error: No agent messages to copy yet.");
        assertThat(copied).hasToString("");

        mode.stop();
    }

    @Test
    void rendersFooterUsageAndModelInfo() {
        var session = new FakeSession().withContextWindow(16);
        var terminal = new VirtualTerminal(80, 14);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("↑1")
            .contains("↓1")
            .contains("$0.000")
            .contains("12.5%/16 (auto)")
            .contains("test-model");

        mode.stop();
    }

    @Test
    void rendersIdleContextUsageWhenNoAssistantUsageExistsYet() {
        var session = new FakeSession().withContextWindow(16);
        var terminal = new VirtualTerminal(80, 14);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("0.0%/16 (auto)")
            .contains("test-model");

        mode.stop();
    }

    @Test
    void rendersFooterCwdAndSessionNameWhenRowsAllow() {
        var session = new FakeSession().withSessionName("Scratch");
        var terminal = new VirtualTerminal(100, 15);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("/workspace • Scratch")
            .contains("0.0%/128k (auto)");

        mode.stop();
    }

    @Test
    void preservesSessionNameWhenFooterMetaLineNeedsTruncation() {
        var session = new FakeSession()
            .withCwd("/workspace/projects/java/cli/pi-mono/very-long-directory-name")
            .withSessionName("Scratchpad");
        var terminal = new VirtualTerminal(32, 15);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();

        var viewport = String.join("\n", terminal.getViewport());
        assertThat(viewport)
            .contains("...")
            .contains("Scratchpad")
            .doesNotContain("/workspace/projects/java/cli/pi-mono/very-long-directory-name");

        mode.stop();
    }

    @Test
    void rendersGitBranchInFooterMetaLine() throws Exception {
        var project = tempDir.resolve("project");
        var gitDir = project.resolve(".git");
        java.nio.file.Files.createDirectories(gitDir);
        java.nio.file.Files.writeString(
            gitDir.resolve("HEAD"),
            "ref: refs/heads/feature/footer\n",
            java.nio.charset.StandardCharsets.UTF_8
        );

        var session = new FakeSession()
            .withCwd(project.toString())
            .withSessionName("Scratch");
        var terminal = new VirtualTerminal(120, 15);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("(feature/footer)")
            .contains("Scratch");

        mode.stop();
    }

    @Test
    void refreshesFooterWhenGitBranchChanges() throws Exception {
        var project = tempDir.resolve("project");
        var gitDir = project.resolve(".git");
        java.nio.file.Files.createDirectories(gitDir);
        var headPath = gitDir.resolve("HEAD");
        java.nio.file.Files.writeString(
            headPath,
            "ref: refs/heads/feature/one\n",
            java.nio.charset.StandardCharsets.UTF_8
        );

        var session = new FakeSession().withCwd(project.toString());
        var terminal = new VirtualTerminal(120, 15);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        assertThat(String.join("\n", terminal.getViewport())).contains("(feature/one)");

        java.nio.file.Files.writeString(
            headPath,
            "ref: refs/heads/feature/two\n",
            java.nio.charset.StandardCharsets.UTF_8
        );

        waitFor(() -> String.join("\n", terminal.getViewport()).contains("(feature/two)"));

        mode.stop();
    }

    @Test
    void stylesFooterStatsAndModelSummaryWithAnsiHierarchy() {
        var session = new FakeSession().withContextWindow(16);
        var terminal = new RecordingTerminal(80, 14);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");

        waitFor(() -> terminal.output().contains("test-model"));

        assertThat(terminal.output())
            .contains("\u001b[90m↑1 ↓1 $0.000\u001b[0m")
            .contains("\u001b[90m12.5%/16 (auto)\u001b[0m")
            .contains("\u001b[1mtest-model\u001b[0m");

        mode.stop();
    }

    @Test
    void highlightsFooterContextUsageWhenNearWindowLimit() {
        var session = new FakeSession().withContextWindow(2);
        var terminal = new RecordingTerminal(100, 14);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");

        waitFor(() -> terminal.output().contains("100.0%/2 (auto)"));

        assertThat(terminal.output()).contains("\u001b[31m100.0%/2 (auto)\u001b[0m");

        mode.stop();
    }

    @Test
    void keepsExactSeventyPercentContextUsageMuted() {
        var session = new FakeSession()
            .withContextWindow(10)
            .withLatestAssistantUsage(3, 4, 7);
        var terminal = new RecordingTerminal(100, 14);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        waitFor(() -> terminal.output().contains("70.0%/10 (auto)"));

        assertThat(terminal.output())
            .contains("\u001b[90m70.0%/10 (auto)\u001b[0m")
            .doesNotContain("\u001b[33m70.0%/10 (auto)\u001b[0m")
            .doesNotContain("\u001b[31m70.0%/10 (auto)\u001b[0m");

        mode.stop();
    }

    @Test
    void keepsExactNinetyPercentContextUsageInWarningState() {
        var session = new FakeSession()
            .withContextWindow(10)
            .withLatestAssistantUsage(4, 5, 9);
        var terminal = new RecordingTerminal(100, 14);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        waitFor(() -> terminal.output().contains("90.0%/10 (auto)"));

        assertThat(terminal.output())
            .contains("\u001b[33m90.0%/10 (auto)\u001b[0m")
            .doesNotContain("\u001b[31m90.0%/10 (auto)\u001b[0m");

        mode.stop();
    }

    @Test
    void preservesModelSummaryInNarrowFooter() {
        var session = new FakeSession().withContextWindow(16);
        var terminal = new VirtualTerminal(24, 14);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("↑1")
            .contains("test-...");

        mode.stop();
    }

    @Test
    void omitsAutoSuffixWhenAutoCompactionIsDisabled() {
        var session = new FakeSession().withContextWindow(16).withAutoCompactionEnabled(false);
        var terminal = new VirtualTerminal(80, 14);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("12.5%/16")
            .doesNotContain("(auto)");

        mode.stop();
    }

    @Test
    void showsUnknownContextUsageAfterCompactionUntilNextAssistantResponse() {
        var session = new FakeSession().withContextWindow(16);
        var terminal = new VirtualTerminal(100, 40);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");
        terminal.sendInput("Second");
        terminal.sendInput("\r");
        terminal.sendInput("/compact");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("?/16 (auto)")));

        var viewport = terminal.getViewport();
        var footerTail = String.join("\n", viewport.subList(Math.max(0, viewport.size() - 8), viewport.size()));
        assertThat(footerTail)
            .contains("?/16 (auto)")
            .doesNotContain("12.5%/16 (auto)");

        mode.stop();
    }

    @Test
    void showsStartupCompactionStatusWhenSessionAlreadyContainsCompaction() {
        var session = new FakeSession()
            .withMessageHistory("Hello")
            .withMessageHistory("Second");
        session.compact(null);
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();

        assertThat(String.join("\n", terminal.getViewport())).contains("Session compacted 1 time");

        mode.stop();
    }

    @Test
    void rendersReasoningThinkingLevelInFooter() {
        var session = new FakeSession().withReasoningModel("reasoning-model", ThinkingLevel.HIGH);
        var terminal = new RecordingTerminal(100, 14);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        waitFor(() -> terminal.output().contains("reasoning-model"));

        assertThat(terminal.output())
            .contains("\u001b[1mreasoning-model\u001b[0m")
            .contains("\u001b[90m • high\u001b[0m");

        mode.stop();
    }

    @Test
    void rendersProviderPrefixInFooterWhenWidthAllows() {
        var session = new FakeSession().withAvailableProviderCount(2);
        var terminal = new RecordingTerminal(100, 14);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        waitFor(() -> terminal.output().contains("test-model"));

        assertThat(terminal.output()).contains("\u001b[90m(openai) \u001b[0m\u001b[1mtest-model\u001b[0m");

        mode.stop();
    }

    @Test
    void omitsProviderPrefixWhenOnlyOneProviderIsAvailable() {
        var session = new FakeSession().withAvailableProviderCount(1);
        var terminal = new RecordingTerminal(100, 14);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        waitFor(() -> terminal.output().contains("test-model"));

        assertThat(terminal.output())
            .doesNotContain("\u001b[90m(openai) \u001b[0m")
            .contains("\u001b[1mtest-model\u001b[0m");

        mode.stop();
    }

    @Test
    void handlesTreeSlashCommand() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");
        terminal.sendInput("/tree");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Navigate session tree")));

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Navigate session tree")
            .contains("user: Hello")
            .contains("assistant: Ack: Hello");

        terminal.sendInput("\u001b[A");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Navigated to selected point")));
        terminal.sendInput("!");
        terminal.sendInput("\r");

        assertThat(session.prompts).containsExactly("Hello", "!Hello");
        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Navigated to selected point")
            .contains("Assistant: Ack: !Hello")
            .doesNotContain("You: Hello\n\nAssistant: Ack: Hello");

        mode.stop();
    }

    @Test
    void showsTreeEmptyStateWhenNoEntriesExist() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("/tree");
        terminal.sendInput("\r");

        assertThat(String.join("\n", terminal.getViewport())).contains("No entries in session");

        mode.stop();
    }

    @Test
    void selectingCurrentTreeEntryShowsAlreadyAtThisPoint() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");
        terminal.sendInput("/tree");
        terminal.sendInput("\r");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Already at this point")));

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Already at this point")
            .doesNotContain("Navigated to selected point");
        assertThat(session.prompts).containsExactly("Hello");

        mode.stop();
    }

    @Test
    void usesAppKeybindingForTreeOverlay() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.TREE, java.util.List.of("alt+t"))));

            mode.start();
            terminal.sendInput("Hello");
            terminal.sendInput("\r");
            terminal.sendInput("\u001bt");

            waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Navigate session tree")));
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void handlesForkSlashCommand() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");
        var originalSessionId = session.sessionId();
        terminal.sendInput("/fork");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Fork from previous message")));

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Fork from previous message")
            .contains("Hello");

        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Branched to new session")));
        terminal.sendInput("!");
        terminal.sendInput("\r");

        assertThat(session.prompts).containsExactly("Hello", "!Hello");
        assertThat(session.sessionId()).isNotEqualTo(originalSessionId);
        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Branched to new session")
            .contains("Assistant: Ack: !Hello")
            .doesNotContain("You: Hello\n\nAssistant: Ack: Hello");

        mode.stop();
    }

    @Test
    void usesAppKeybindingForForkOverlay() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.FORK, java.util.List.of("alt+f"))));

            mode.start();
            terminal.sendInput("Hello");
            terminal.sendInput("\r");
            terminal.sendInput("\u001bf");

            waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Fork from previous message")));
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void handlesCompactSlashCommand() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(100, 20);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Hello");
        terminal.sendInput("\r");
        terminal.sendInput("Second");
        terminal.sendInput("\r");
        terminal.sendInput("/compact Focus on latest work");
        terminal.sendInput("\r");

        waitFor(() -> {
            if (session.state().messages().isEmpty()) {
                return false;
            }
            var firstMessage = session.state().messages().getFirst();
            if (!(firstMessage instanceof AgentMessage.UserMessage compactedSummary)) {
                return false;
            }
            if (compactedSummary.content().isEmpty() || !(compactedSummary.content().getFirst() instanceof TextContent textContent)) {
                return false;
            }
            return textContent.text().contains("Focus on latest work");
        });

        assertThat(session.prompts).containsExactly("Hello", "Second");
        var compactedSummary = (AgentMessage.UserMessage) session.state().messages().getFirst();
        assertThat(((TextContent) compactedSummary.content().getFirst()).text())
            .contains("Focus on latest work")
            .contains("[User]: Hello")
            .contains("[Assistant]: Ack: Hello");
        assertThat(String.join("\n", terminal.getViewport()))
            .doesNotContain("Compacted context");

        mode.stop();
    }

    @Test
    void showsNothingToCompactWhenSessionHasNoMessages() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(100, 20);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("/compact");
        terminal.sendInput("\r");

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Nothing to compact (no messages yet)")
            .doesNotContain("Compacted context");
        assertThat(session.state().messages()).isEmpty();

        mode.stop();
    }

    @Test
    void handlesReloadSlashCommand() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("/reload");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Reloaded extensions, skills, prompts, themes")));

        assertThat(session.reloadCount).isEqualTo(1);
        assertThat(session.state().systemPrompt()).isEqualTo("Reloaded system prompt");
        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Reloaded extensions, skills, prompts, themes");

        mode.stop();
    }

    @Test
    void handlesReloadSlashCommandWarnings() {
        var session = new FakeSession();
        session.reloadWarnings = List.of("reload-plugin.jar: broken extension");
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("/reload");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Reloaded extensions, skills, prompts, themes")));

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Reloaded extensions, skills, prompts, themes")
            .contains("[Reload warnings]")
            .contains("extension: reload-plugin.jar: broken extension");

        mode.stop();
    }

    @Test
    void showsReloadStreamingWarningWithoutCallingReload() {
        var session = new FakeSession().withStreaming(true);
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("/reload");
        terminal.sendInput("\r");

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Wait for the current response to finish before reloading.")
            .doesNotContain("Error: Wait for the current response to finish before reloading");
        assertThat(session.reloadCount).isZero();

        mode.stop();
    }

    @Test
    void hidesThinkingBlocksWhenSettingIsEnabled() {
        var session = new FakeSession()
            .withAssistantThinkingMessage("Reason through options", "Final answer")
            .withHideThinkingBlock(true);
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();

        assertThat(String.join("\n", terminal.getViewport()))
            .contains("Assistant: Final answer")
            .doesNotContain("Thinking: Reason through options");

        mode.stop();
    }

    @Test
    void ctrlDOnEmptyInputStopsMode() throws Exception {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var stopped = new CompletableFuture<Void>();
        mode.setOnStop(() -> stopped.complete(null));

        mode.start();
        terminal.sendInput("\u0004");

        stopped.get(2, TimeUnit.SECONDS);
    }

    @Test
    void usesAppKeybindingForInterrupt() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.INTERRUPT, java.util.List.of("alt+x"))));

            mode.start();
            terminal.sendInput("\u001b");
            assertThat(session.abortCount).isZero();

            terminal.sendInput("\u001bx");
            assertThat(session.abortCount).isEqualTo(1);
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void usesAppKeybindingForClear() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.CLEAR, java.util.List.of("alt+c"))));

            mode.start();
            terminal.sendInput("draft");
            terminal.sendInput("\u001bc");
            terminal.sendInput("next");
            terminal.sendInput("\r");

            assertThat(session.prompts).containsExactly("next");
            assertThat(String.join("\n", terminal.getViewport())).contains("Assistant: Ack: next");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void clearTwiceRequestsExit() throws Exception {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var stopped = new CompletableFuture<Void>();
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.CLEAR, java.util.List.of("alt+c"))));
            mode.setOnStop(() -> stopped.complete(null));

            mode.start();
            terminal.sendInput("draft");
            terminal.sendInput("\u001bc");
            terminal.sendInput("\u001bc");

            stopped.get(2, TimeUnit.SECONDS);
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void usesAppKeybindingForExitWhenInputIsEmpty() throws Exception {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var stopped = new CompletableFuture<Void>();
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.EXIT, java.util.List.of("alt+q"))));
            mode.setOnStop(() -> stopped.complete(null));

            mode.start();
            terminal.sendInput("\u001bq");

            stopped.get(2, TimeUnit.SECONDS);
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void usesAppKeybindingForSuspend() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var suspended = new java.util.concurrent.atomic.AtomicBoolean();
        var mode = new PiInteractiveMode(
            session,
            terminal,
            new PiCopyCommand(session, text -> {
            }),
            PiClipboardImage.system(),
            () -> suspended.set(true)
        );
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.SUSPEND, java.util.List.of("alt+s"))));

            mode.start();
            terminal.sendInput("\u001bs");

            waitFor(suspended::get);
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void showsUnsupportedSuspendMessage() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(
            session,
            terminal,
            new PiCopyCommand(session, text -> {
            }),
            PiClipboardImage.system(),
            () -> {
                throw new UnsupportedOperationException("Suspend is not supported on Windows");
            }
        );
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.SUSPEND, java.util.List.of("alt+s"))));

            mode.start();
            terminal.sendInput("\u001bs");

            waitFor(() -> String.join("\n", terminal.getViewport()).contains("Suspend is not supported on Windows"));
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void usesAppKeybindingForExternalEditor() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(
            session,
            terminal,
            new PiCopyCommand(session, text -> {
            }),
            PiClipboardImage.system(),
            currentText -> currentText + "\nnext line",
            () -> {
            }
        );
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.EXTERNAL_EDITOR, java.util.List.of("alt+g"))));

            mode.start();
            terminal.sendInput("draft");
            terminal.sendInput("\u001bg");

            assertThat(String.join("\n", terminal.getViewport()))
                .contains("> draft next line")
                .doesNotContain("Loaded text from external editor");

            terminal.sendInput("\r");

            assertThat(session.prompts).containsExactly("draft next line");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void showsExternalEditorConfigurationError() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(
            session,
            terminal,
            new PiCopyCommand(session, text -> {
            }),
            PiClipboardImage.system(),
            currentText -> {
                throw new UnsupportedOperationException("No editor configured. Set $VISUAL or $EDITOR environment variable.");
            },
            () -> {
            }
        );
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.EXTERNAL_EDITOR, java.util.List.of("alt+g"))));

            mode.start();
            terminal.sendInput("\u001bg");

            waitFor(() -> String.join("\n", terminal.getViewport()).contains("No editor configured. Set $VISUAL or $EDITOR environment variable."));
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void usesAppKeybindingForExpandTools() {
        var details = JsonNodeFactory.instance.objectNode()
            .put("path", "README.md")
            .put("truncated", false);
        var session = new FakeSession().withToolResultMessage("read", "Read summary", details);
        var terminal = new VirtualTerminal(100, 20);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.EXPAND_TOOLS, java.util.List.of("alt+h"))));

            mode.start();
            assertThat(String.join("\n", terminal.getViewport()))
                .contains("Tool read: Read summary")
                .doesNotContain("Details:");

            terminal.sendInput("\u001bh");

            waitFor(() -> String.join("\n", terminal.getViewport()).contains("Details:"));
            assertThat(String.join("\n", terminal.getViewport()))
                .contains("Tool details: expanded")
                .contains("\"path\" : \"README.md\"");

            terminal.sendInput("\u001bh");

            waitFor(() -> String.join("\n", terminal.getViewport()).contains("Tool details: collapsed"));
            assertThat(String.join("\n", terminal.getViewport())).doesNotContain("Details:");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void usesAppKeybindingForPasteImage() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(
            session,
            terminal,
            new PiCopyCommand(session, text -> {
            }),
            () -> new ImageContent("ZGF0YQ==", "image/png")
        );
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.PASTE_IMAGE, java.util.List.of("alt+z"))));

            mode.start();
            terminal.sendInput("\u001bz");

            waitFor(() -> String.join("\n", terminal.getViewport()).contains("Attached image from clipboard"));
            assertThat(String.join("\n", terminal.getViewport())).contains("Attached images: 1");
            terminal.sendInput("\r");

            waitFor(() -> !session.promptMessages.isEmpty());
            assertThat(session.promptMessages.getFirst().content())
                .singleElement()
                .isInstanceOf(ImageContent.class);
            assertThat(String.join("\n", terminal.getViewport()))
                .contains("You: [image image/png]")
                .contains("Assistant: Ack: [image image/png]")
                .doesNotContain("Attached images: 1");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void rejectsPastedImagesWhileStreaming() {
        var session = new FakeSession().withStreaming(true);
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(
            session,
            terminal,
            new PiCopyCommand(session, text -> {
            }),
            () -> new ImageContent("ZGF0YQ==", "image/png")
        );
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.PASTE_IMAGE, java.util.List.of("alt+z"))));

            mode.start();
            terminal.sendInput("\u001bz");

            waitFor(() -> String.join("\n", terminal.getViewport()).contains("Attached image from clipboard"));
            terminal.sendInput("\r");

            assertThat(session.promptMessages).isEmpty();
            assertThat(session.steeringMessages).isEmpty();
            assertThat(String.join("\n", terminal.getViewport()))
                .contains("Error: Image attachments are not supported while streaming")
                .contains("Attached images: 1");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void ignoresMissingClipboardImageWithoutChangingStatus() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(
            session,
            terminal,
            new PiCopyCommand(session, text -> {
            }),
            () -> null
        );
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.PASTE_IMAGE, java.util.List.of("alt+z"))));

            mode.start();
            var before = String.join("\n", terminal.getViewport());

            terminal.sendInput("\u001bz");

            assertThat(String.join("\n", terminal.getViewport()))
                .isEqualTo(before)
                .doesNotContain("No image in clipboard")
                .doesNotContain("Attached image from clipboard")
                .doesNotContain("Attached images: 1");
            assertThat(session.promptMessages).isEmpty();
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void ignoresClipboardReadErrorsWithoutChangingStatus() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(
            session,
            terminal,
            new PiCopyCommand(session, text -> {
            }),
            () -> {
                throw new IllegalStateException("clipboard unavailable");
            }
        );
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.PASTE_IMAGE, java.util.List.of("alt+z"))));

            mode.start();
            var before = String.join("\n", terminal.getViewport());

            terminal.sendInput("\u001bz");

            assertThat(String.join("\n", terminal.getViewport()))
                .isEqualTo(before)
                .doesNotContain("clipboard unavailable")
                .doesNotContain("Attached image from clipboard")
                .doesNotContain("Attached images: 1");
            assertThat(session.promptMessages).isEmpty();
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void usesAppKeybindingForResume() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(80, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(PiAppAction.RESUME, java.util.List.of("alt+u"))));

            mode.start();
            terminal.sendInput("\u001bu");

            waitFor(() -> session.resumeCount == 1);
            assertThat(String.join("\n", terminal.getViewport())).contains("Resumed session");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void usesAppKeybindingForCycleModelForward() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(
                PiAppAction.CYCLE_MODEL_FORWARD,
                java.util.List.of("ctrl+p")
            )));

            mode.start();
            terminal.sendInput("\u0010");

            waitFor(() -> "next-model".equals(session.lastModelIdChange));
            assertThat(String.join("\n", terminal.getViewport()))
                .contains("Switched to Next Model")
                .contains("model: openai/next-model");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void usesAppKeybindingForCycleModelBackward() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(
                PiAppAction.CYCLE_MODEL_BACKWARD,
                java.util.List.of("shift+ctrl+p")
            )));

            mode.start();
            terminal.sendInput("\u001b[112;6u");

            waitFor(() -> "previous-model".equals(session.lastModelIdChange));
            assertThat(String.join("\n", terminal.getViewport()))
                .contains("Switched to Previous Model")
                .contains("model: openai/previous-model");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void usesAppKeybindingForCycleThinkingLevel() {
        var session = new FakeSession().withReasoningModel("reasoning-model", null);
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(
                PiAppAction.CYCLE_THINKING_LEVEL,
                java.util.List.of("shift+tab")
            )));

            mode.start();
            terminal.sendInput("\u001b[Z");

            waitFor(() -> "minimal".equals(session.lastThinkingLevelChange));
            assertThat(String.join("\n", terminal.getViewport())).contains("Thinking level: minimal");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void showsUnsupportedThinkingStatusForNonReasoningModels() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(
                PiAppAction.CYCLE_THINKING_LEVEL,
                java.util.List.of("shift+tab")
            )));

            mode.start();
            terminal.sendInput("\u001b[Z");

            waitFor(() -> String.join("\n", terminal.getViewport()).contains("Current model does not support thinking"));
            assertThat(String.join("\n", terminal.getViewport()))
                .contains("Current model does not support thinking")
                .doesNotContain("Error: Thinking level is not available for current model");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void cycleModelShowsScopedAvailabilityMessage() {
        var session = new FakeSession()
            .withSelectableModels(List.of(new PiInteractiveSession.SelectableModel(0, "openai", "gpt-5", "GPT-5", "minimal", true, true, 400_000)))
            .withScopedSelectableModels(List.of(new PiInteractiveSession.SelectableModel(0, "openai", "gpt-5", "GPT-5", "minimal", true, true, 400_000)))
            .withSingleModelOnly(true);
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(
                PiAppAction.CYCLE_MODEL_FORWARD,
                java.util.List.of("ctrl+p")
            )));

            mode.start();
            terminal.sendInput("\u0010");

            assertThat(String.join("\n", terminal.getViewport())).contains("Only one model in scope");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void usesAppKeybindingForToggleThinking() {
        var session = new FakeSession().withAssistantThinkingMessage("Reason through options", "Final answer");
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(
                PiAppAction.TOGGLE_THINKING,
                java.util.List.of("ctrl+t")
            )));

            mode.start();
            terminal.sendInput("\u0014");

            waitFor(() -> String.join("\n", terminal.getViewport()).contains("Thinking blocks: hidden"));
            assertThat(String.join("\n", terminal.getViewport()))
                .contains("Assistant: Final answer")
                .contains("Thinking blocks: hidden")
                .doesNotContain("Thinking: Reason through options");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void usesAppKeybindingForNewSession() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(
                PiAppAction.NEW_SESSION,
                java.util.List.of("alt+m")
            )));

            mode.start();
            terminal.sendInput("Hello");
            terminal.sendInput("\r");
            var originalSessionId = session.sessionId();

            terminal.sendInput("\u001bm");

            waitFor(() -> !originalSessionId.equals(session.sessionId()));
            assertThat(String.join("\n", terminal.getViewport()))
                .contains("New session started")
                .contains("session: " + session.sessionId())
                .doesNotContain("You: Hello");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void usesAppKeybindingForFollowUpWhileStreaming() {
        var session = new FakeSession().withStreaming(true);
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(
                PiAppAction.FOLLOW_UP, java.util.List.of("alt+enter"),
                PiAppAction.DEQUEUE, java.util.List.of("alt+up", "ctrl+y")
            )));

            mode.start();
            terminal.sendInput("Queued");
            terminal.sendInput("\u001b\r");

            waitFor(() -> session.followUps.contains("Queued"));
            var viewport = String.join("\n", terminal.getViewport());
            assertThat(viewport).doesNotContain("Queued follow-up");
            assertThat(viewport).contains("Follow-up: Queued");
            assertThat(viewport).contains("alt+up/ctrl+y to edit all queued messages");
            assertThat(session.prompts).doesNotContain("Queued");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void stylesQueuedMessageHintWithSharedKeyHintFormatter() {
        var session = new FakeSession().withStreaming(true);
        var terminal = new RecordingTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(
                PiAppAction.FOLLOW_UP, java.util.List.of("alt+enter"),
                PiAppAction.DEQUEUE, java.util.List.of("alt+up", "ctrl+y")
            )));

            mode.start();
            terminal.sendInput("Queued");
            terminal.sendInput("\u001b\r");

            waitFor(() -> terminal.output().contains("to edit all queued messages"));
            assertThat(terminal.output())
                .contains("\u001b[90m↳ \u001b[0m")
                .contains("\u001b[2;37malt+up/ctrl+y\u001b[0m")
                .contains("\u001b[90m to edit all queued messages\u001b[0m");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void stylesQueuedMessageLinesAsMuted() {
        var session = new FakeSession().withStreaming(true);
        var terminal = new RecordingTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Queued");
        terminal.sendInput("\r");

        waitFor(() -> terminal.output().contains("Steering: Queued"));
        assertThat(terminal.output()).contains("\u001b[90mSteering: Queued\u001b[0m");

        mode.stop();
    }

    @Test
    void truncatesQueuedMessageLinesInsteadOfWrappingThem() {
        var session = new FakeSession().withStreaming(true);
        var terminal = new VirtualTerminal(26, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Queued message that should not wrap");
        terminal.sendInput("\r");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> stripAnsi(line).contains("Steering:")));

        var viewport = terminal.getViewport().stream().map(PiInteractiveModeTest::stripAnsi).toList();
        assertThat(viewport).anyMatch(line -> line.contains("Steering: Queued mess..."));
        assertThat(viewport).noneMatch(line -> line.contains("age that should not wrap"));

        mode.stop();
    }

    @Test
    void submitQueuesSteeringWhileStreaming() {
        var session = new FakeSession().withStreaming(true);
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("Queued");
        terminal.sendInput("\r");

        waitFor(() -> session.steeringMessages.contains("Queued"));
        var viewport = String.join("\n", terminal.getViewport());
        assertThat(viewport).doesNotContain("Queued steering message");
        assertThat(viewport).contains("Steering: Queued");
        assertThat(viewport).contains("alt+up to edit all queued messages");
        assertThat(session.prompts).doesNotContain("Queued");

        mode.stop();
    }

    @Test
    void followUpActsLikeSubmitWhenIdle() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(
                PiAppAction.FOLLOW_UP,
                java.util.List.of("alt+enter")
            )));

            mode.start();
            terminal.sendInput("Immediate");
            terminal.sendInput("\u001b\r");

            waitFor(() -> session.prompts.contains("Immediate"));
            assertThat(session.followUps).isEmpty();
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void usesAppKeybindingForDequeue() {
        var session = new FakeSession().withDequeuedMessages("Queued one\n\nQueued two", 2);
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(
                PiAppAction.DEQUEUE,
                java.util.List.of("alt+up")
            )));

            mode.start();
            terminal.sendInput("draft");
            terminal.sendInput("\u001bp");

            var viewport = String.join("\n", terminal.getViewport());
            assertThat(viewport).contains("Restored 2 queued messages to editor");
            assertThat(viewport).contains("Queued one");
            assertThat(viewport).contains("Queued two");
            assertThat(viewport).contains("draft");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void dequeueShowsEmptyStatusWhenNothingQueued() {
        var session = new FakeSession();
        var terminal = new VirtualTerminal(100, 16);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(
                PiAppAction.DEQUEUE,
                java.util.List.of("alt+up")
            )));

            mode.start();
            terminal.sendInput("\u001bp");

            assertThat(String.join("\n", terminal.getViewport())).contains("No queued messages to restore");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void usesAppKeybindingForSelectModel() {
        var session = new FakeSession().withSelectableModels(List.of(
            new PiInteractiveSession.SelectableModel(0, "openai", "gpt-5", "minimal", true),
            new PiInteractiveSession.SelectableModel(1, "anthropic", "claude-3-7-sonnet", "high", false)
        ));
        var terminal = new VirtualTerminal(100, 18);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(
                PiAppAction.SELECT_MODEL,
                java.util.List.of("ctrl+l")
            )));

            mode.start();
            terminal.sendInput("\u000c");
            assertThat(String.join("\n", terminal.getViewport()))
                .doesNotContain("Select model")
                .contains("Only showing models with configured API keys")
                .contains("gpt-5");

            terminal.sendInput("\u001b[B");
            terminal.sendInput("\r");

            waitFor(() -> "claude-3-7-sonnet".equals(session.lastModelIdChange));
            assertThat(String.join("\n", terminal.getViewport())).contains("Model: claude-3-7-sonnet");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void showsStatusWhenNoModelsAreAvailableForSelector() {
        var session = new FakeSession().withSelectableModels(List.of());
        var terminal = new VirtualTerminal(100, 18);
        var mode = new PiInteractiveMode(session, terminal);
        var previousApp = PiAppKeybindings.global();
        try {
            PiAppKeybindings.setGlobal(new PiAppKeybindings(java.util.Map.of(
                PiAppAction.SELECT_MODEL,
                java.util.List.of("ctrl+l")
            )));

            mode.start();
            terminal.sendInput("\u000c");

            assertThat(String.join("\n", terminal.getViewport()))
                .contains("No models available")
                .doesNotContain("Select model");
        } finally {
            PiAppKeybindings.setGlobal(previousApp);
            mode.stop();
        }
    }

    @Test
    void doubleEscapeOpensTreeWhenConfigured() {
        var session = new FakeSession().withMessageHistory("Hello").withDoubleEscapeAction("tree");
        var terminal = new VirtualTerminal(100, 18);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("\u001b");
        terminal.sendInput("\u001b");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Navigate session tree")));
        mode.stop();
    }

    @Test
    void doubleEscapeOpensForkWhenConfigured() {
        var session = new FakeSession().withMessageHistory("Hello").withDoubleEscapeAction("fork");
        var terminal = new VirtualTerminal(100, 18);
        var mode = new PiInteractiveMode(session, terminal);

        mode.start();
        terminal.sendInput("\u001b");
        terminal.sendInput("\u001b");

        waitFor(() -> terminal.getViewport().stream().anyMatch(line -> line.contains("Fork from previous message")));
        mode.stop();
    }

    private static final class FakeSession implements PiInteractiveSession {
        private final List<String> prompts = new ArrayList<>();
        private final List<AgentMessage.UserMessage> promptMessages = new ArrayList<>();
        private final CopyOnWriteArrayList<Consumer<AgentState>> stateListeners = new CopyOnWriteArrayList<>();
        private final SessionManager sessionManager = SessionManager.inMemory("/workspace");
        private int reloadCount;
        private int abortCount;
        private int resumeCount;
        private boolean autoCompactionEnabled = true;
        private boolean hideThinkingBlock;
        private boolean quietStartup;
        private String doubleEscapeAction = "tree";
        private String theme = "dark";
        private int editorPaddingX;
        private int availableProviderCount = 1;
        private String cwd = "/workspace";
        private String lastThinkingLevelChange;
        private String lastModelIdChange;
        private String lastModelProviderChange;
        private boolean singleModelOnly;
        private boolean streaming;
        private List<String> reloadWarnings = List.of();
        private final List<String> steeringMessages = new ArrayList<>();
        private final List<String> followUps = new ArrayList<>();
        private final List<String> queuedSteeringMessages = new ArrayList<>();
        private final List<String> queuedFollowUps = new ArrayList<>();
        private List<SelectableModel> selectableModels = List.of();
        private List<SelectableModel> scopedSelectableModels = List.of();
        private DequeueResult dequeueResult = new DequeueResult("", 0);
        private AgentState state = new AgentState(
            "",
            new Model(
                "test-model",
                "Test Model",
                "openai-responses",
                "openai",
                "https://example.com",
                false,
                List.of("text"),
                new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
                128_000,
                8_192,
                null,
                null
            ),
            null,
            List.<AgentTool<?>>of(),
            List.of(),
            false,
            null,
            Set.of(),
            null
        );

        @Override
        public String sessionId() {
            return sessionManager.sessionId();
        }

        @Override
        public AgentState state() {
            return state;
        }

        @Override
        public Subscription subscribe(Consumer<AgentEvent> listener) {
            return () -> {
            };
        }

        @Override
        public Subscription subscribeState(Consumer<AgentState> listener) {
            listener.accept(state);
            stateListeners.add(listener);
            return () -> stateListeners.remove(listener);
        }

        @Override
        public CompletionStage<Void> prompt(String text) {
            return prompt(new AgentMessage.UserMessage(List.of(new TextContent(text, null)), 1L));
        }

        @Override
        public CompletionStage<Void> prompt(AgentMessage.UserMessage message) {
            promptMessages.add(message);
            var rendered = PiMessageRenderer.renderUserContent(message.content());
            prompts.add(rendered);
            try {
                sessionManager.appendMessage(AgentMessages.toLlmMessage(message));
                sessionManager.appendMessage(new Message.AssistantMessage(
                    List.of(new TextContent("Ack: " + rendered, null)),
                    state.model().api(),
                    state.model().provider(),
                    state.model().id(),
                    new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                    dev.pi.ai.model.StopReason.STOP,
                    null,
                    2L
                ));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            syncState();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> steer(String text) {
            steeringMessages.add(text);
            queuedSteeringMessages.add(text);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> resume() {
            resumeCount += 1;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> waitForIdle() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void abort() {
            abortCount += 1;
        }

        @Override
        public boolean autoCompactionEnabled() {
            return autoCompactionEnabled;
        }

        @Override
        public ContextUsage contextUsage() {
            var contextWindow = state.model().contextWindow();
            if (contextWindow <= 0) {
                return null;
            }

            var branchEntries = sessionManager.branch();
            var latestCompactionIndex = latestCompactionIndex(branchEntries);
            if (latestCompactionIndex >= 0 && !hasPostCompactionUsage(branchEntries, latestCompactionIndex)) {
                return new ContextUsage(null, contextWindow, null);
            }

            var latestUsage = latestAssistantUsage();
            if (latestUsage == null || latestUsage.totalTokens() <= 0) {
                return new ContextUsage(0, contextWindow, 0.0);
            }
            return new ContextUsage(
                latestUsage.totalTokens(),
                contextWindow,
                latestUsage.totalTokens() * 100.0 / contextWindow
            );
        }

        @Override
        public SettingsSelection settingsSelection() {
            return new SettingsSelection(
                autoCompactionEnabled,
                "one-at-a-time",
                "one-at-a-time",
                "auto",
                hideThinkingBlock,
                quietStartup,
                doubleEscapeAction,
                theme,
                List.of("dark", "light"),
                editorPaddingX,
                state.model().reasoning(),
                state.thinkingLevel() == null ? "off" : state.thinkingLevel().value(),
                state.model().reasoning() ? List.of("off", "minimal", "low", "medium", "high", "xhigh") : List.of()
            );
        }

        @Override
        public void updateSetting(String settingId, String value) {
            if ("hide-thinking".equals(settingId)) {
                hideThinkingBlock = "true".equals(value);
                emitState();
                return;
            }
            if ("double-escape-action".equals(settingId)) {
                doubleEscapeAction = value;
                emitState();
                return;
            }
            if ("editor-padding".equals(settingId)) {
                editorPaddingX = Integer.parseInt(value);
                emitState();
                return;
            }
            if ("theme".equals(settingId)) {
                theme = value;
                emitState();
                return;
            }
            throw new UnsupportedOperationException("Unsupported setting: " + settingId);
        }

        @Override
        public int availableProviderCount() {
            return availableProviderCount;
        }

        @Override
        public String cwd() {
            return cwd;
        }

        @Override
        public String sessionName() {
            for (var index = sessionManager.entries().size() - 1; index >= 0; index--) {
                var entry = sessionManager.entries().get(index);
                if (entry instanceof SessionEntry.SessionInfoEntry sessionInfoEntry
                    && sessionInfoEntry.name() != null
                    && !sessionInfoEntry.name().isBlank()) {
                    return sessionInfoEntry.name().trim();
                }
            }
            return null;
        }

        @Override
        public ModelCycleResult cycleModelForward() {
            if (singleModelOnly) {
                return null;
            }
            var nextModel = new Model(
                "next-model",
                "Next Model",
                state.model().api(),
                state.model().provider(),
                "https://example.com",
                true,
                List.of("text"),
                new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
                128_000,
                8_192,
                null,
                null
            );
            state = state.withModel(nextModel);
            lastModelIdChange = nextModel.id();
            emitState();
            return new ModelCycleResult(nextModel.provider(), nextModel.id(), nextModel.name(), "off", false);
        }

        @Override
        public ModelCycleResult cycleModelBackward() {
            if (singleModelOnly) {
                return null;
            }
            var previousModel = new Model(
                "previous-model",
                "Previous Model",
                state.model().api(),
                state.model().provider(),
                "https://example.com",
                true,
                List.of("text"),
                new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
                128_000,
                8_192,
                null,
                null
            );
            state = state.withModel(previousModel);
            lastModelIdChange = previousModel.id();
            emitState();
            return new ModelCycleResult(previousModel.provider(), previousModel.id(), previousModel.name(), "off", false);
        }

        @Override
        public String cycleThinkingLevel() {
            if (!state.model().reasoning()) {
                throw new UnsupportedOperationException("Thinking level is not available for current model");
            }
            var next = switch (state.thinkingLevel()) {
                case null -> ThinkingLevel.MINIMAL;
                case MINIMAL -> ThinkingLevel.LOW;
                case LOW -> ThinkingLevel.MEDIUM;
                case MEDIUM -> ThinkingLevel.HIGH;
                case HIGH, XHIGH -> null;
            };
            state = state.withThinkingLevel(next);
            lastThinkingLevelChange = next == null ? "off" : next.value();
            emitState();
            return lastThinkingLevelChange;
        }

        @Override
        public List<SelectableModel> selectableModels() {
            return selectableModels;
        }

        @Override
        public ModelSelection modelSelection() {
            return new ModelSelection(selectableModels, scopedSelectableModels);
        }

        @Override
        public ModelCycleResult selectModel(int index) {
            if (index < 0 || index >= selectableModels.size()) {
                throw new IllegalArgumentException("Unknown model selection: " + index);
            }
            var selected = selectableModels.get(index);
            var nextModel = new Model(
                selected.modelId(),
                selected.modelId(),
                state.model().api(),
                selected.provider(),
                "https://example.com",
                !"off".equals(selected.thinkingLevel()),
                state.model().input(),
                state.model().cost(),
                state.model().contextWindow(),
                state.model().maxTokens(),
                null,
                null
            );
            var nextThinkingLevel = "off".equals(selected.thinkingLevel())
                ? null
                : ("current".equals(selected.thinkingLevel()) ? state.thinkingLevel() : ThinkingLevel.fromValue(selected.thinkingLevel()));
            state = state.withModel(nextModel).withThinkingLevel(nextThinkingLevel);
            lastModelProviderChange = selected.provider();
            lastModelIdChange = selected.modelId();
            emitState();
            return new ModelCycleResult(selected.provider(), selected.modelId(), selected.modelName(), selected.thinkingLevel(), true);
        }

        @Override
        public String newSession() {
            try {
                sessionManager.createBranchedSession(null);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            syncState();
            return sessionManager.sessionId();
        }

        @Override
        public CompletionStage<Void> followUp(String text) {
            followUps.add(text);
            queuedFollowUps.add(text);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public List<String> queuedSteeringMessages() {
            return List.copyOf(queuedSteeringMessages);
        }

        @Override
        public List<String> queuedFollowUps() {
            return List.copyOf(queuedFollowUps);
        }

        @Override
        public DequeueResult dequeue() {
            var current = dequeueResult;
            queuedSteeringMessages.clear();
            queuedFollowUps.clear();
            dequeueResult = new DequeueResult("", 0);
            return current;
        }

        @Override
        public String leafId() {
            return sessionManager.leafId();
        }

        @Override
        public List<SessionTreeNode> tree() {
            return sessionManager.tree();
        }

        @Override
        public TreeNavigationResult navigateTree(String targetId) {
            var entry = sessionManager.entry(targetId);
            if (entry == null) {
                throw new IllegalArgumentException("Unknown session entry: " + targetId);
            }
            var editorText = switch (entry) {
                case SessionEntry.MessageEntry messageEntry when "user".equals(messageEntry.message().path("role").asText()) -> {
                    sessionManager.navigate(messageEntry.parentId());
                    yield extractUserText(messageEntry);
                }
                default -> {
                    sessionManager.navigate(targetId);
                    yield null;
                }
            };
            syncState();
            return new TreeNavigationResult(sessionManager.leafId(), editorText);
        }

        @Override
        public List<ForkMessage> forkMessages() {
            var messages = new ArrayList<ForkMessage>();
            for (var entry : sessionManager.entries()) {
                if (!(entry instanceof SessionEntry.MessageEntry messageEntry)) {
                    continue;
                }
                if (!"user".equals(messageEntry.message().path("role").asText())) {
                    continue;
                }
                var text = extractUserText(messageEntry);
                if (!text.isBlank()) {
                    messages.add(new ForkMessage(entry.id(), text));
                }
            }
            return List.copyOf(messages);
        }

        @Override
        public ForkResult fork(String entryId) {
            var entry = sessionManager.entry(entryId);
            if (!(entry instanceof SessionEntry.MessageEntry messageEntry) || !"user".equals(messageEntry.message().path("role").asText())) {
                throw new IllegalArgumentException("Invalid entry ID for forking");
            }
            try {
                sessionManager.createBranchedSession(messageEntry.parentId());
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            syncState();
            return new ForkResult(extractUserText(messageEntry), sessionManager.sessionId());
        }

        @Override
        public CompactionResult compact(String customInstructions) {
            try {
                var result = PiCompactor.compact(sessionManager, customInstructions);
                syncState();
                return result;
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        @Override
        public ReloadResult reload() {
            reloadCount++;
            state = state.withSystemPrompt("Reloaded system prompt");
            emitState();
            return new ReloadResult(List.of(), List.of(), reloadWarnings);
        }

        private void emitState() {
            for (var listener : stateListeners) {
                listener.accept(state);
            }
        }

        private void syncState() {
            var messages = sessionManager.buildSessionContext().messages().stream()
                .map(AgentMessages::fromLlmMessage)
                .toList();
            state = state.withMessages(messages);
            emitState();
        }

        private FakeSession withReasoningModel(String modelId, ThinkingLevel thinkingLevel) {
            state = state.withModel(new Model(
                modelId,
                modelId,
                "openai-responses",
                "openai",
                "https://example.com",
                true,
                List.of("text"),
                new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
                128_000,
                8_192,
                null,
                null
            )).withThinkingLevel(thinkingLevel);
            emitState();
            return this;
        }

        private FakeSession withContextWindow(int contextWindow) {
            state = state.withModel(new Model(
                state.model().id(),
                state.model().name(),
                state.model().api(),
                state.model().provider(),
                state.model().baseUrl(),
                state.model().reasoning(),
                state.model().input(),
                state.model().cost(),
                contextWindow,
                state.model().maxTokens(),
                state.model().headers(),
                state.model().compat()
            ));
            emitState();
            return this;
        }

        private FakeSession withAutoCompactionEnabled(boolean autoCompactionEnabled) {
            this.autoCompactionEnabled = autoCompactionEnabled;
            emitState();
            return this;
        }

        private FakeSession withHideThinkingBlock(boolean hideThinkingBlock) {
            this.hideThinkingBlock = hideThinkingBlock;
            emitState();
            return this;
        }

        private FakeSession withQuietStartup(boolean quietStartup) {
            this.quietStartup = quietStartup;
            emitState();
            return this;
        }

        private FakeSession withDoubleEscapeAction(String doubleEscapeAction) {
            this.doubleEscapeAction = doubleEscapeAction;
            emitState();
            return this;
        }

        private FakeSession withEditorPaddingX(int editorPaddingX) {
            this.editorPaddingX = editorPaddingX;
            emitState();
            return this;
        }

        private FakeSession withTheme(String theme) {
            this.theme = theme;
            emitState();
            return this;
        }

        private FakeSession withMessageHistory(String text) {
            try {
                sessionManager.appendMessage(new dev.pi.ai.model.Message.UserMessage(List.of(new TextContent(text, null)), 1L));
                sessionManager.appendMessage(new dev.pi.ai.model.Message.AssistantMessage(
                    List.of(new TextContent("Ack: " + text, null)),
                    state.model().api(),
                    state.model().provider(),
                    state.model().id(),
                    new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                    dev.pi.ai.model.StopReason.STOP,
                    null,
                    2L
                ));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            syncState();
            return this;
        }

        private FakeSession withLatestAssistantUsage(int inputTokens, int outputTokens, int totalTokens) {
            try {
                sessionManager.appendMessage(new Message.AssistantMessage(
                    List.of(new TextContent("Usage marker", null)),
                    state.model().api(),
                    state.model().provider(),
                    state.model().id(),
                    new Usage(
                        inputTokens,
                        outputTokens,
                        0,
                        0,
                        totalTokens,
                        new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)
                    ),
                    dev.pi.ai.model.StopReason.STOP,
                    null,
                    System.currentTimeMillis()
                ));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            syncState();
            return this;
        }

        private FakeSession withAvailableProviderCount(int availableProviderCount) {
            this.availableProviderCount = availableProviderCount;
            emitState();
            return this;
        }

        private FakeSession withCwd(String cwd) {
            this.cwd = cwd;
            emitState();
            return this;
        }

        private FakeSession withSessionName(String sessionName) {
            try {
                sessionManager.appendSessionInfo(sessionName);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            emitState();
            return this;
        }

        private FakeSession withStreaming(boolean streaming) {
            this.streaming = streaming;
            state = state.finishStreaming(state.messages());
            if (streaming) {
                state = state.startStreaming(new AgentMessage.AssistantMessage(
                    List.of(new TextContent("Streaming", null)),
                    state.model().api(),
                    state.model().provider(),
                    state.model().id(),
                    new Usage(0, 0, 0, 0, 0, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                    dev.pi.ai.model.StopReason.STOP,
                    null,
                    1L
                ));
            }
            emitState();
            return this;
        }

        private FakeSession withAssistantThinkingMessage(String thinking, String text) {
            try {
                sessionManager.appendMessage(new Message.AssistantMessage(
                    List.of(new ThinkingContent(thinking, null, false), new TextContent(text, null)),
                    state.model().api(),
                    state.model().provider(),
                    state.model().id(),
                    new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                    dev.pi.ai.model.StopReason.STOP,
                    null,
                    2L
                ));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            syncState();
            return this;
        }

        private FakeSession withToolResultMessage(String toolName, String text, com.fasterxml.jackson.databind.JsonNode details) {
            try {
                sessionManager.appendMessage(new Message.ToolResultMessage(
                    "tool-1",
                    toolName,
                    List.of(new TextContent(text, null)),
                    details,
                    false,
                    2L
                ));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            syncState();
            return this;
        }

        private FakeSession withDequeuedMessages(String editorText, int restoredCount) {
            this.dequeueResult = new DequeueResult(editorText, restoredCount);
            return this;
        }

        private FakeSession withSelectableModels(List<SelectableModel> selectableModels) {
            this.selectableModels = List.copyOf(selectableModels);
            return this;
        }

        private FakeSession withScopedSelectableModels(List<SelectableModel> scopedSelectableModels) {
            this.scopedSelectableModels = List.copyOf(scopedSelectableModels);
            return this;
        }

        private FakeSession withSingleModelOnly(boolean singleModelOnly) {
            this.singleModelOnly = singleModelOnly;
            return this;
        }

        private Usage latestAssistantUsage() {
            for (var index = state.messages().size() - 1; index >= 0; index--) {
                var message = state.messages().get(index);
                if (message instanceof AgentMessage.AssistantMessage assistantMessage
                    && assistantMessage.stopReason() != dev.pi.ai.model.StopReason.ERROR
                    && assistantMessage.stopReason() != dev.pi.ai.model.StopReason.ABORTED) {
                    return assistantMessage.usage();
                }
            }
            return null;
        }

        private static int latestCompactionIndex(List<SessionEntry> branchEntries) {
            for (var index = branchEntries.size() - 1; index >= 0; index--) {
                if (branchEntries.get(index) instanceof SessionEntry.CompactionEntry) {
                    return index;
                }
            }
            return -1;
        }

        private static boolean hasPostCompactionUsage(List<SessionEntry> branchEntries, int compactionIndex) {
            for (var index = branchEntries.size() - 1; index > compactionIndex; index--) {
                var entry = branchEntries.get(index);
                if (!(entry instanceof SessionEntry.MessageEntry messageEntry)) {
                    continue;
                }
                if (!"assistant".equals(messageEntry.message().path("role").asText())) {
                    continue;
                }

                var stopReason = messageEntry.message().path("stopReason").asText("stop");
                if ("error".equals(stopReason) || "aborted".equals(stopReason)) {
                    break;
                }
                return messageEntry.message().path("usage").path("totalTokens").asInt(0) > 0;
            }
            return false;
        }

        private static String extractUserText(SessionEntry.MessageEntry entry) {
            var parts = new ArrayList<String>();
            for (var item : entry.message().path("content")) {
                if ("text".equals(item.path("type").asText())) {
                    var text = item.path("text").asText("");
                    if (!text.isBlank()) {
                        parts.add(text);
                    }
                }
            }
            return String.join("\n", parts);
        }
    }

    private static final class RecordingTerminal implements Terminal {
        private final int columns;
        private final int rows;
        private final List<String> writes = new CopyOnWriteArrayList<>();
        private volatile InputHandler inputHandler;

        private RecordingTerminal(int columns, int rows) {
            this.columns = columns;
            this.rows = rows;
        }

        @Override
        public void start(InputHandler onInput, Runnable onResize) {
            inputHandler = onInput;
        }

        @Override
        public void stop() {
            inputHandler = null;
        }

        @Override
        public void write(String data) {
            writes.add(data);
        }

        @Override
        public int columns() {
            return columns;
        }

        @Override
        public int rows() {
            return rows;
        }

        private void sendInput(String data) {
            if (inputHandler != null) {
                inputHandler.onInput(data);
            }
        }

        private String output() {
            return String.join("", writes);
        }
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) {
        var deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
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

    private static String stripAnsi(String text) {
        return text.replaceAll("\\u001B\\[[;\\d]*m", "");
    }
}
