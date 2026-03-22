package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.agent.runtime.AgentLoopConfig;
import dev.pi.ai.model.AssistantMessageEvent;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.AssistantMessageEventStream;
import dev.pi.session.InstructionFile;
import dev.pi.session.InstructionResources;
import dev.pi.session.SessionManager;
import dev.pi.session.SettingsManager;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PiAgentSessionStartupResourcesTest {
    @Test
    void exposesStartupResourcesAndRefreshesThemesOnReload() {
        var agentsPath = Path.of("/workspace/project/AGENTS.md").toString();
        var session = PiAgentSession.builder(
            testModel(),
            SessionManager.inMemory("/workspace/project"),
            SettingsManager.inMemory(),
            new InstructionResources(
                List.of(new InstructionFile(Path.of("/workspace/project/AGENTS.md"), "agent rules")),
                null,
                List.of()
            )
        )
            .streamFunction(fakeAssistant("ready"))
            .skillPaths(List.of("/workspace/project/skills/demo"))
            .promptPaths(List.of("/workspace/project/prompts/main.md"))
            .customThemes(List.of("midnight"))
            .extensionPaths(List.of("/workspace/project/ext/demo.jar"))
            .startupResourcePathsReloadAction(() -> new PiAgentSession.StartupResourcePaths(
                List.of("/workspace/project/skills/reloaded"),
                List.of("/workspace/project/prompts/reloaded.md")
            ))
            .themeReloadAction(() -> new PiAgentSession.ThemeReloadResult(
                List.of("sunrise"),
                List.of("theme warning")
            ))
            .build();

        assertThat(session.startupResources().contextFiles()).containsExactly(agentsPath);
        assertThat(session.startupResources().extensionPaths()).containsExactly("/workspace/project/ext/demo.jar");
        assertThat(session.startupResources().skillPaths()).containsExactly("/workspace/project/skills/demo");
        assertThat(session.startupResources().promptPaths()).containsExactly("/workspace/project/prompts/main.md");
        assertThat(session.startupResources().customThemes()).containsExactly("midnight");

        var reloadResult = session.reload();

        assertThat(reloadResult.themeWarnings()).containsExactly("theme warning");
        assertThat(session.startupResources().skillPaths()).containsExactly("/workspace/project/skills/reloaded");
        assertThat(session.startupResources().promptPaths()).containsExactly("/workspace/project/prompts/reloaded.md");
        assertThat(session.startupResources().customThemes()).containsExactly("sunrise");
    }

    private static Model testModel() {
        return new Model(
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
        );
    }

    private static AgentLoopConfig.AssistantStreamFunction fakeAssistant(String text) {
        return (model, context, options) -> {
            var stream = new AssistantMessageEventStream();
            Thread.ofVirtual().start(() -> stream.push(new AssistantMessageEvent.Done(
                StopReason.STOP,
                new Message.AssistantMessage(
                    List.of(new TextContent(text, null)),
                    model.api(),
                    model.provider(),
                    model.id(),
                    new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                    StopReason.STOP,
                    null,
                    200L
                )
            )));
            return stream;
        };
    }
}
