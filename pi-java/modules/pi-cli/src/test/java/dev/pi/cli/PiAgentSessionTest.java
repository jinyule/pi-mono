package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.agent.runtime.AgentMessage;
import dev.pi.agent.runtime.AgentLoopConfig;
import dev.pi.ai.model.AssistantMessageEvent;
import dev.pi.ai.model.Context;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.AssistantMessageEventStream;
import dev.pi.session.InstructionResourceLoader;
import dev.pi.session.InstructionResources;
import dev.pi.session.SessionManager;
import dev.pi.session.SettingsManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiAgentSessionTest {
    @Test
    void rehydratesSessionContextAndPersistsPromptedMessages() {
        var sessionManager = SessionManager.inMemory("/workspace");
        var restored = new Message.UserMessage(List.of(new TextContent("existing", null)), 100L);
        try {
            sessionManager.appendMessage(restored);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }

        var session = PiAgentSession.builder(
            testModel(),
            sessionManager,
            SettingsManager.inMemory(),
            new InstructionResources(List.of(), "Base system prompt", List.of("Append prompt"))
        )
            .streamFunction(fakeAssistant("Ack: hello"))
            .build();

        assertThat(session.state().messages())
            .extracting(message -> message.role())
            .containsExactly("user");
        assertThat(session.state().systemPrompt()).contains("Base system prompt").contains("Append prompt");

        session.prompt("hello").toCompletableFuture().join();
        session.waitForIdle().toCompletableFuture().join();

        assertThat(session.state().messages())
            .extracting(message -> message.role())
            .containsExactly("user", "user", "assistant");
        assertThat(sessionManager.buildSessionContext().messages())
            .extracting(Message::role)
            .containsExactly("user", "user", "assistant");
        assertThat(session.drainPersistenceErrors()).isEmpty();
    }

    @Test
    void navigatesToAssistantEntryInPlace() {
        var model = testModel();
        var sessionManager = SessionManager.inMemory("/workspace");
        String firstAssistantId;
        try {
            sessionManager.appendMessage(new Message.UserMessage(List.of(new TextContent("first", null)), 100L));
            firstAssistantId = sessionManager.appendMessage(assistantMessage(model, "ack:first", 200L));
            sessionManager.appendMessage(new Message.UserMessage(List.of(new TextContent("second", null)), 300L));
            sessionManager.appendMessage(assistantMessage(model, "ack:second", 400L));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }

        var session = PiAgentSession.builder(
            model,
            sessionManager,
            SettingsManager.inMemory(),
            new InstructionResources(List.of(), "", List.of())
        )
            .streamFunction(fakeAssistant("Ack"))
            .build();

        var result = session.navigateTree(firstAssistantId);

        assertThat(result.editorText()).isNull();
        assertThat(session.leafId()).isEqualTo(firstAssistantId);
        assertThat(session.state().messages())
            .extracting(message -> message.role())
            .containsExactly("user", "assistant");
    }

    @Test
    void selectingRootUserLoadsEditorTextAndMovesLeafToParent() {
        var model = testModel();
        var sessionManager = SessionManager.inMemory("/workspace");
        String firstUserId;
        try {
            firstUserId = sessionManager.appendMessage(new Message.UserMessage(List.of(new TextContent("first", null)), 100L));
            sessionManager.appendMessage(assistantMessage(model, "ack:first", 200L));
            sessionManager.appendMessage(new Message.UserMessage(List.of(new TextContent("second", null)), 300L));
            sessionManager.appendMessage(assistantMessage(model, "ack:second", 400L));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }

        var session = PiAgentSession.builder(
            model,
            sessionManager,
            SettingsManager.inMemory(),
            new InstructionResources(List.of(), "", List.of())
        )
            .streamFunction(fakeAssistant("Ack"))
            .build();

        var result = session.navigateTree(firstUserId);

        assertThat(result.editorText()).isEqualTo("first");
        assertThat(session.leafId()).isNull();
        assertThat(session.state().messages()).isEmpty();
    }

    @Test
    void forksToNewSessionAndUsesNewSessionIdForLaterPrompts() {
        var model = testModel();
        var sessionManager = SessionManager.inMemory("/workspace");
        String secondUserId;
        try {
            sessionManager.appendMessage(new Message.UserMessage(List.of(new TextContent("first", null)), 100L));
            sessionManager.appendMessage(assistantMessage(model, "ack:first", 200L));
            secondUserId = sessionManager.appendMessage(new Message.UserMessage(List.of(new TextContent("second", null)), 300L));
            sessionManager.appendMessage(assistantMessage(model, "ack:second", 400L));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }

        var observedSessionIds = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var session = PiAgentSession.builder(
            model,
            sessionManager,
            SettingsManager.inMemory(),
            new InstructionResources(List.of(), "", List.of())
        )
            .streamFunction(capturingAssistant("Ack after fork", observedSessionIds))
            .build();

        var originalSessionId = session.sessionId();
        var forkResult = session.fork(secondUserId);
        session.prompt(forkResult.selectedText()).toCompletableFuture().join();
        session.waitForIdle().toCompletableFuture().join();

        assertThat(forkResult.selectedText()).isEqualTo("second");
        assertThat(forkResult.sessionId()).isNotEqualTo(originalSessionId);
        assertThat(session.sessionId()).isEqualTo(forkResult.sessionId());
        assertThat(observedSessionIds).containsExactly(forkResult.sessionId());
        assertThat(session.state().messages())
            .extracting(message -> message.role())
            .containsExactly("user", "assistant", "user", "assistant");
    }

    @Test
    void compactsOlderTurnsAndReplaysSummaryMessage() {
        var model = testModel();
        var sessionManager = SessionManager.inMemory("/workspace");
        String secondUserId;
        try {
            sessionManager.appendMessage(new Message.UserMessage(List.of(new TextContent("first", null)), 100L));
            sessionManager.appendMessage(assistantMessage(model, "ack:first", 200L));
            secondUserId = sessionManager.appendMessage(new Message.UserMessage(List.of(new TextContent("second", null)), 300L));
            sessionManager.appendMessage(assistantMessage(model, "ack:second", 400L));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }

        var session = PiAgentSession.builder(
            model,
            sessionManager,
            SettingsManager.inMemory(),
            new InstructionResources(List.of(), "", List.of())
        )
            .streamFunction(fakeAssistant("Ack"))
            .build();

        var result = session.compact("Focus on latest implementation details");

        assertThat(result.firstKeptEntryId()).isEqualTo(secondUserId);
        assertThat(result.tokensBefore()).isGreaterThan(0);
        assertThat(result.summary()).contains("Focus on latest implementation details").contains("first");
        assertThat(session.state().messages())
            .extracting(message -> message.role())
            .containsExactly("user", "user", "assistant");
        var summaryMessage = (AgentMessage.UserMessage) session.state().messages().getFirst();
        assertThat(((TextContent) summaryMessage.content().getFirst()).text())
            .contains("compacted into the following summary")
            .contains("first");
    }

    @Test
    void reloadRefreshesSettingsAndInstructionResources(@TempDir Path tempDir) throws Exception {
        var projectDir = tempDir.resolve("project");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(projectDir.resolve(".pi"));
        Files.createDirectories(agentDir);
        Files.writeString(projectDir.resolve(".pi").resolve("SYSTEM.md"), "Project system v1", StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve("AGENTS.md"), "Project agents v1", StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve(".pi").resolve("settings.json"), "{\"theme\":\"dark\"}", StandardCharsets.UTF_8);

        var loader = new InstructionResourceLoader(projectDir, agentDir);
        loader.reload();
        var settingsManager = SettingsManager.create(projectDir, agentDir);
        var session = PiAgentSession.builder(
            testModel(),
            SessionManager.inMemory(projectDir.toString()),
            settingsManager,
            loader.resources()
        )
            .instructionResourceLoader(loader)
            .streamFunction(fakeAssistant("Ack"))
            .build();

        assertThat(session.state().systemPrompt())
            .contains("Project system v1")
            .contains("Project agents v1");
        assertThat(settingsManager.effective().getString("/theme")).isEqualTo("dark");

        Files.writeString(projectDir.resolve(".pi").resolve("SYSTEM.md"), "Project system v2", StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve("AGENTS.md"), "Project agents v2", StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve(".pi").resolve("settings.json"), "{\"theme\":\"light\"}", StandardCharsets.UTF_8);

        var result = session.reload();

        assertThat(result.settingsErrors()).isEmpty();
        assertThat(result.resourceErrors()).isEmpty();
        assertThat(result.extensionWarnings()).isEmpty();
        assertThat(session.state().systemPrompt())
            .contains("Project system v2")
            .contains("Project agents v2")
            .doesNotContain("Project system v1")
            .doesNotContain("Project agents v1");
        assertThat(settingsManager.effective().getString("/theme")).isEqualTo("light");
    }

    @Test
    void reloadReturnsExtensionWarningsFromReloadAction() {
        var session = PiAgentSession.builder(
            testModel(),
            SessionManager.inMemory("/workspace"),
            SettingsManager.inMemory(),
            new InstructionResources(List.of(), "System prompt", List.of())
        )
            .streamFunction(fakeAssistant("Ack"))
            .reloadAction(() -> List.of("reload-plugin.jar: broken extension"))
            .build();

        var result = session.reload();

        assertThat(result.settingsErrors()).isEmpty();
        assertThat(result.resourceErrors()).isEmpty();
        assertThat(result.extensionWarnings()).containsExactly("reload-plugin.jar: broken extension");
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

    private static AgentLoopConfig.AssistantStreamFunction capturingAssistant(
        String text,
        java.util.List<String> observedSessionIds
    ) {
        return (model, context, options) -> {
            observedSessionIds.add(options.sessionId());
            return fakeAssistant(text).stream(model, context, options);
        };
    }

    private static Message.AssistantMessage assistantMessage(Model model, String text, long timestamp) {
        return new Message.AssistantMessage(
            List.of(new TextContent(text, null)),
            model.api(),
            model.provider(),
            model.id(),
            new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
            StopReason.STOP,
            null,
            timestamp
        );
    }
}
