package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.agent.runtime.AgentLoopConfig;
import dev.pi.ai.model.AssistantMessageEvent;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.ThinkingLevel;
import dev.pi.ai.model.Usage;
import dev.pi.ai.stream.AssistantMessageEventStream;
import dev.pi.session.InstructionResources;
import dev.pi.session.SessionManager;
import dev.pi.session.SettingsManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class PiAgentSessionScopedModelsTest {
    @Test
    void updateScopedModelsAppliesSessionOnlyFilter() {
        var openai = testReasoningModel("openai", "gpt-5");
        var anthropic = testReasoningModel("anthropic", "claude-3-7-sonnet");
        var google = testModel("google", "gemini-2.5-pro", false);
        var session = PiAgentSession.builder(
            openai,
            SessionManager.inMemory("/workspace"),
            SettingsManager.inMemory(),
            InstructionResources.empty()
        )
            .thinkingLevel(ThinkingLevel.HIGH)
            .cycleModels(List.of(
                new PiAgentSession.CycleModel(openai, ThinkingLevel.HIGH),
                new PiAgentSession.CycleModel(anthropic, ThinkingLevel.HIGH),
                new PiAgentSession.CycleModel(google, null)
            ), false)
            .modelSelectorModels(List.of(openai, anthropic, google))
            .streamFunction(fakeAssistant("ready"))
            .build();

        session.updateScopedModels(List.of("anthropic/claude-3-7-sonnet", "google/gemini-2.5-pro"));

        var selection = session.scopedModelsSelection();
        assertThat(selection.hasFilter()).isTrue();
        assertThat(selection.enabledModelIds())
            .containsExactly("anthropic/claude-3-7-sonnet", "google/gemini-2.5-pro");
        assertThat(session.modelSelection().scopedModels())
            .extracting(model -> model.provider() + "/" + model.modelId())
            .containsExactly("anthropic/claude-3-7-sonnet", "google/gemini-2.5-pro");
        assertThat(session.modelSelection().scopedModels().getFirst().thinkingLevel()).isEqualTo("high");
    }

    @Test
    void saveScopedModelsPersistsAndClearingFilterRemovesSavedScope() {
        var openai = testReasoningModel("openai", "gpt-5");
        var anthropic = testReasoningModel("anthropic", "claude-3-7-sonnet");
        var settingsManager = SettingsManager.inMemory();
        var session = PiAgentSession.builder(
            openai,
            SessionManager.inMemory("/workspace"),
            settingsManager,
            InstructionResources.empty()
        )
            .cycleModels(List.of(
                new PiAgentSession.CycleModel(openai, null),
                new PiAgentSession.CycleModel(anthropic, null)
            ), false)
            .modelSelectorModels(List.of(openai, anthropic))
            .streamFunction(fakeAssistant("ready"))
            .build();

        session.saveScopedModels(List.of("anthropic/claude-3-7-sonnet"));

        assertThat(settingsManager.getEnabledModels()).containsExactly("anthropic/claude-3-7-sonnet");
        assertThat(session.scopedModelsSelection().enabledModelIds()).containsExactly("anthropic/claude-3-7-sonnet");

        session.saveScopedModels(List.of());

        assertThat(settingsManager.getEnabledModels()).isEmpty();
        assertThat(session.scopedModelsSelection().hasFilter()).isFalse();
        assertThat(session.scopedModelsSelection().enabledModelIds()).isEmpty();
    }

    private static Model testReasoningModel(String provider, String modelId) {
        return testModel(provider, modelId, true);
    }

    private static Model testModel(String provider, String modelId, boolean reasoning) {
        return new Model(
            modelId,
            modelId,
            provider + "-api",
            provider,
            "https://example.com",
            reasoning,
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
}
