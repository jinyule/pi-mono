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
import dev.pi.ai.model.ThinkingLevel;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.AssistantMessageEventStream;
import dev.pi.session.InstructionResourceLoader;
import dev.pi.session.InstructionResources;
import dev.pi.session.Settings;
import dev.pi.session.SessionManager;
import dev.pi.session.SettingsManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void buildsQueueModesAndThinkingLevelFromSettings() {
        var globalSettings = Settings.empty().withMutations(root -> {
            root.put("steeringMode", "all");
            root.put("followUpMode", "all");
            root.put("defaultThinkingLevel", "high");
        });
        var session = PiAgentSession.builder(
            testReasoningModel("anthropic", "claude-3-7-sonnet"),
            SessionManager.inMemory("/workspace"),
            SettingsManager.inMemory(globalSettings, Settings.empty()),
            new InstructionResources(List.of(), "", List.of())
        )
            .streamFunction(fakeAssistant("Ack"))
            .build();

        assertThat(session.agent().steeringMode()).isEqualTo(dev.pi.agent.runtime.Agent.QueueMode.ALL);
        assertThat(session.agent().followUpMode()).isEqualTo(dev.pi.agent.runtime.Agent.QueueMode.ALL);
        assertThat(session.state().thinkingLevel()).isEqualTo(ThinkingLevel.HIGH);
        assertThat(session.settingsSelection().steeringMode()).isEqualTo("all");
        assertThat(session.settingsSelection().followUpMode()).isEqualTo("all");
        assertThat(session.settingsSelection().thinkingLevel()).isEqualTo("high");
    }

    @Test
    void updateSettingPersistsQueueModesAndThinkingLevel() {
        var sessionManager = SessionManager.inMemory("/workspace");
        var settingsManager = SettingsManager.inMemory();
        var session = PiAgentSession.builder(
            testReasoningModel("anthropic", "claude-3-7-sonnet"),
            sessionManager,
            settingsManager,
            new InstructionResources(List.of(), "", List.of())
        )
            .streamFunction(fakeAssistant("Ack"))
            .build();

        session.updateSetting("autocompact", "false");
        session.updateSetting("steering-mode", "all");
        session.updateSetting("follow-up-mode", "all");
        session.updateSetting("thinking", "high");

        assertThat(settingsManager.effective().getBoolean("/compaction/enabled", true)).isFalse();
        assertThat(settingsManager.effective().getString("/steeringMode")).isEqualTo("all");
        assertThat(settingsManager.effective().getString("/followUpMode")).isEqualTo("all");
        assertThat(settingsManager.effective().getString("/defaultThinkingLevel")).isEqualTo("high");
        assertThat(session.agent().steeringMode()).isEqualTo(dev.pi.agent.runtime.Agent.QueueMode.ALL);
        assertThat(session.agent().followUpMode()).isEqualTo(dev.pi.agent.runtime.Agent.QueueMode.ALL);
        assertThat(session.state().thinkingLevel()).isEqualTo(ThinkingLevel.HIGH);
        assertThat(sessionManager.entries().getLast()).isInstanceOf(dev.pi.session.SessionEntry.ThinkingLevelChangeEntry.class);
        assertThat(((dev.pi.session.SessionEntry.ThinkingLevelChangeEntry) sessionManager.entries().getLast()).thinkingLevel())
            .isEqualTo("high");
    }

    @Test
    void cyclesForwardThroughScopedModelsAndPersistsModelChange() {
        var sessionManager = SessionManager.inMemory("/workspace");
        var nextModel = new Model(
            "reasoning-model",
            "Reasoning Model",
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
        );

        var session = PiAgentSession.builder(
            testModel(),
            sessionManager,
            SettingsManager.inMemory(),
            new InstructionResources(List.of(), "", List.of())
        )
            .streamFunction(fakeAssistant("Ack"))
            .cycleModels(List.of(
                new PiAgentSession.CycleModel(testModel(), null),
                new PiAgentSession.CycleModel(nextModel, ThinkingLevel.HIGH)
            ), true)
            .build();

        var result = session.cycleModelForward();

        assertThat(result).isNotNull();
        assertThat(result.modelId()).isEqualTo("reasoning-model");
        assertThat(result.thinkingLevel()).isEqualTo("high");
        assertThat(result.scoped()).isTrue();
        assertThat(session.state().model().id()).isEqualTo("reasoning-model");
        assertThat(session.state().thinkingLevel()).isEqualTo(ThinkingLevel.HIGH);

        var entries = sessionManager.entries();
        assertThat(entries.get(entries.size() - 2)).isInstanceOf(dev.pi.session.SessionEntry.ModelChangeEntry.class);
        assertThat(entries.get(entries.size() - 1)).isInstanceOf(dev.pi.session.SessionEntry.ThinkingLevelChangeEntry.class);
        assertThat(((dev.pi.session.SessionEntry.ModelChangeEntry) entries.get(entries.size() - 2)).modelId()).isEqualTo("reasoning-model");
        assertThat(((dev.pi.session.SessionEntry.ThinkingLevelChangeEntry) entries.get(entries.size() - 1)).thinkingLevel())
            .isEqualTo("high");
    }

    @Test
    void cyclesBackwardThroughScopedModelsAndPersistsModelChange() {
        var sessionManager = SessionManager.inMemory("/workspace");
        var previousModel = new Model(
            "reasoning-model",
            "Reasoning Model",
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
        );

        var session = PiAgentSession.builder(
            testModel(),
            sessionManager,
            SettingsManager.inMemory(),
            new InstructionResources(List.of(), "", List.of())
        )
            .streamFunction(fakeAssistant("Ack"))
            .cycleModels(List.of(
                new PiAgentSession.CycleModel(testModel(), null),
                new PiAgentSession.CycleModel(previousModel, ThinkingLevel.HIGH)
            ), true)
            .build();

        var result = session.cycleModelBackward();

        assertThat(result).isNotNull();
        assertThat(result.modelId()).isEqualTo("reasoning-model");
        assertThat(result.thinkingLevel()).isEqualTo("high");
        assertThat(result.scoped()).isTrue();
        assertThat(session.state().model().id()).isEqualTo("reasoning-model");
        assertThat(session.state().thinkingLevel()).isEqualTo(ThinkingLevel.HIGH);

        var entries = sessionManager.entries();
        assertThat(entries.get(entries.size() - 2)).isInstanceOf(dev.pi.session.SessionEntry.ModelChangeEntry.class);
        assertThat(entries.get(entries.size() - 1)).isInstanceOf(dev.pi.session.SessionEntry.ThinkingLevelChangeEntry.class);
        assertThat(((dev.pi.session.SessionEntry.ModelChangeEntry) entries.get(entries.size() - 2)).modelId()).isEqualTo("reasoning-model");
        assertThat(((dev.pi.session.SessionEntry.ThinkingLevelChangeEntry) entries.get(entries.size() - 1)).thinkingLevel())
            .isEqualTo("high");
    }

    @Test
    void startsNewSessionAndUsesNewSessionIdForLaterPrompts() {
        var sessionManager = SessionManager.inMemory("/workspace");
        var observedSessionIds = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var session = PiAgentSession.builder(
            testModel(),
            sessionManager,
            SettingsManager.inMemory(),
            new InstructionResources(List.of(), "", List.of())
        )
            .streamFunction(capturingAssistant("Ack after reset", observedSessionIds))
            .build();

        session.prompt("before").toCompletableFuture().join();
        session.waitForIdle().toCompletableFuture().join();
        var originalSessionId = session.sessionId();

        var newSessionId = session.newSession();

        assertThat(newSessionId).isNotEqualTo(originalSessionId);
        assertThat(session.sessionId()).isEqualTo(newSessionId);
        assertThat(session.state().messages()).isEmpty();

        session.prompt("after").toCompletableFuture().join();
        session.waitForIdle().toCompletableFuture().join();

        assertThat(observedSessionIds).containsExactly(originalSessionId, newSessionId);
        assertThat(session.state().messages())
            .extracting(message -> message.role())
            .containsExactly("user", "assistant");
    }

    @Test
    void queuesFollowUpWhileStreamingAndRunsItAfterCurrentTurn() throws Exception {
        var sessionManager = SessionManager.inMemory("/workspace");
        var firstTurnRelease = new CountDownLatch(1);
        var invocationCount = new AtomicInteger();
        var session = PiAgentSession.builder(
            testModel(),
            sessionManager,
            SettingsManager.inMemory(),
            new InstructionResources(List.of(), "", List.of())
        )
            .streamFunction((model, context, options) -> {
                var call = invocationCount.getAndIncrement();
                var stream = new AssistantMessageEventStream();
                Thread.ofVirtual().start(() -> {
                    if (call == 0) {
                        try {
                            firstTurnRelease.await(2, TimeUnit.SECONDS);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(exception);
                        }
                    }
                    stream.push(new AssistantMessageEvent.Done(
                        StopReason.STOP,
                        new Message.AssistantMessage(
                            List.of(new TextContent(call == 0 ? "Ack:first" : "Ack:follow-up", null)),
                            model.api(),
                            model.provider(),
                            model.id(),
                            new Usage(1, 1, 0, 0, 2, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
                            StopReason.STOP,
                            null,
                            200L + call
                        )
                    ));
                });
                return stream;
            })
            .build();

        session.prompt("first").toCompletableFuture();
        waitFor(() -> session.state().isStreaming());

        session.followUp("second").toCompletableFuture().join();
        assertThat(session.agent().hasQueuedMessages()).isTrue();

        firstTurnRelease.countDown();
        session.waitForIdle().toCompletableFuture().join();

        assertThat(session.state().messages())
            .extracting(message -> message.role())
            .containsExactly("user", "assistant", "user", "assistant");
        assertThat(PiMessageRenderer.renderMessage(session.state().messages().get(2))).contains("second");
        assertThat(session.agent().hasQueuedMessages()).isFalse();
    }

    @Test
    void dequeueRestoresQueuedFollowUpsToEditorText() {
        var session = PiAgentSession.builder(
            testModel(),
            SessionManager.inMemory("/workspace"),
            SettingsManager.inMemory(),
            new InstructionResources(List.of(), "", List.of())
        )
            .streamFunction(fakeAssistant("ready"))
            .build();

        session.followUp("first queued").toCompletableFuture().join();
        session.followUp("second queued").toCompletableFuture().join();

        var result = session.dequeue();

        assertThat(result.restoredCount()).isEqualTo(2);
        assertThat(result.editorText()).isEqualTo("first queued\n\nsecond queued");
        assertThat(session.agent().hasQueuedMessages()).isFalse();
    }

    @Test
    void selectModelAppliesExactCycleTarget() throws Exception {
        var initialModel = testReasoningModel("openai", "gpt-5");
        var nextModel = testReasoningModel("anthropic", "claude-3-7-sonnet");
        var session = PiAgentSession.builder(
            initialModel,
            SessionManager.inMemory("/workspace"),
            SettingsManager.inMemory(),
            new InstructionResources(List.of(), "", List.of())
        )
            .thinkingLevel(ThinkingLevel.MINIMAL)
            .cycleModels(List.of(
                new PiAgentSession.CycleModel(initialModel, ThinkingLevel.MINIMAL),
                new PiAgentSession.CycleModel(nextModel, ThinkingLevel.HIGH)
            ), true)
            .streamFunction(fakeAssistant("ready"))
            .build();

        var selectableModels = session.selectableModels();
        assertThat(selectableModels).hasSize(2);
        assertThat(selectableModels.get(0).current()).isTrue();

        var result = session.selectModel(1);

        assertThat(result.provider()).isEqualTo("anthropic");
        assertThat(result.modelId()).isEqualTo("claude-3-7-sonnet");
        assertThat(result.thinkingLevel()).isEqualTo("high");
        assertThat(session.state().model().provider()).isEqualTo("anthropic");
        assertThat(session.state().model().id()).isEqualTo("claude-3-7-sonnet");
        assertThat(session.state().thinkingLevel()).isEqualTo(ThinkingLevel.HIGH);
    }

    @Test
    void queuedFollowUpsExposeQueuedMessages() {
        var session = PiAgentSession.builder(
            testModel(),
            SessionManager.inMemory("/workspace"),
            SettingsManager.inMemory(),
            new InstructionResources(List.of(), "", List.of())
        )
            .streamFunction(fakeAssistant("ready"))
            .build();

        session.followUp("first queued").toCompletableFuture().join();
        session.followUp("second queued").toCompletableFuture().join();

        assertThat(session.queuedFollowUps()).containsExactly("first queued", "second queued");
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

    private static Model testReasoningModel(String provider, String modelId) {
        return new Model(
            modelId,
            modelId,
            "anthropic-messages",
            provider,
            "https://example.com",
            true,
            List.of("text"),
            new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
            200_000,
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
}
