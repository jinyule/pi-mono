package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.ai.model.Model;
import dev.pi.ai.model.Usage;
import dev.pi.ai.registry.ModelRegistry;
import dev.pi.session.Settings;
import dev.pi.session.SettingsManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class PiCliModuleScopedModelsTest {
    @Test
    void resolveCycleModelsUsesSavedScopeEvenWithoutCliPatterns() {
        var registry = new ModelRegistry();
        registry.registerAll(List.of(
            model("gpt-5", "openai"),
            model("claude-3-7-sonnet", "anthropic"),
            model("gemini-2.5-pro", "google")
        ));
        var args = new PiCliParser().parse();
        var scopedCycleModels = PiCliModule.resolveScopedCycleModels(List.of(
            "anthropic/claude-3-7-sonnet",
            "google/gemini-2.5-pro"
        ), registry);

        var resolved = PiCliModule.resolveCycleModels(args, registry, scopedCycleModels);

        assertThat(resolved)
            .extracting(model -> model.model().provider() + "/" + model.model().id())
            .containsExactly("anthropic/claude-3-7-sonnet", "google/gemini-2.5-pro");
    }

    @Test
    void resolveModelPrefersSavedDefaultWhenItIsInsideSavedScope() {
        var registry = new ModelRegistry();
        registry.registerAll(List.of(
            model("gpt-5", "openai"),
            model("claude-3-7-sonnet", "anthropic")
        ));
        var settingsManager = SettingsManager.inMemory(
            Settings.empty()
                .withStringList("enabledModels", List.of("openai/gpt-5", "anthropic/claude-3-7-sonnet"))
                .withMutations(root -> {
                    root.put("defaultProvider", "anthropic");
                    root.put("defaultModel", "claude-3-7-sonnet");
                }),
            Settings.empty()
        );
        var scopedCycleModels = PiCliModule.resolveScopedCycleModels(settingsManager.getEnabledModels(), registry);

        var resolved = PiCliModule.resolveModel(new PiCliParser().parse(), registry, scopedCycleModels, settingsManager);

        assertThat(resolved.provider()).isEqualTo("anthropic");
        assertThat(resolved.id()).isEqualTo("claude-3-7-sonnet");
    }

    @Test
    void resolveModelFallsBackToFirstScopedModelWhenSavedDefaultIsOutsideScope() {
        var registry = new ModelRegistry();
        registry.registerAll(List.of(
            model("gpt-5", "openai"),
            model("claude-3-7-sonnet", "anthropic")
        ));
        var settingsManager = SettingsManager.inMemory(
            Settings.empty()
                .withStringList("enabledModels", List.of("anthropic/claude-3-7-sonnet"))
                .withMutations(root -> {
                    root.put("defaultProvider", "openai");
                    root.put("defaultModel", "gpt-5");
                }),
            Settings.empty()
        );
        var scopedCycleModels = PiCliModule.resolveScopedCycleModels(settingsManager.getEnabledModels(), registry);

        var resolved = PiCliModule.resolveModel(new PiCliParser().parse(), registry, scopedCycleModels, settingsManager);

        assertThat(resolved.provider()).isEqualTo("anthropic");
        assertThat(resolved.id()).isEqualTo("claude-3-7-sonnet");
    }

    private static Model model(String modelId, String provider) {
        return new Model(
            modelId,
            modelId,
            provider + "-api",
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
}
