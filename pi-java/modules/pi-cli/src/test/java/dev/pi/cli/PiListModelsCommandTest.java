package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.ai.model.Model;
import dev.pi.ai.model.Usage;
import dev.pi.ai.registry.ModelRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class PiListModelsCommandTest {
    @Test
    void rendersSortedModelTable() {
        var output = new StringBuilder();
        var command = new PiListModelsCommand(registry(
            model("claude-3-7-sonnet", "anthropic", true, List.of("text")),
            model("gpt-4o-mini", "openai", false, List.of("text", "image"))
        ), output);

        command.run(null);

        assertThat(normalize(output.toString())).containsExactly(
            "provider   model              context  max-out  thinking  images",
            "anthropic  claude-3-7-sonnet  128K     8.2K     yes       no",
            "openai     gpt-4o-mini        128K     8.2K     no        yes"
        );
    }

    @Test
    void filtersModelsWithFuzzyQuery() {
        var output = new StringBuilder();
        var command = new PiListModelsCommand(registry(
            model("claude-3-7-sonnet", "anthropic", true, List.of("text")),
            model("gpt-4o-mini", "openai", false, List.of("text"))
        ), output);

        command.run("c37s");

        assertThat(output.toString()).contains("claude-3-7-sonnet");
        assertThat(output.toString()).doesNotContain("gpt-4o-mini");
    }

    @Test
    void reportsNoModelsAndNoMatches() {
        var noModelsOutput = new StringBuilder();
        new PiListModelsCommand(new ModelRegistry(), noModelsOutput).run(null);

        var noMatchOutput = new StringBuilder();
        new PiListModelsCommand(registry(model("gpt-4o-mini", "openai", false, List.of("text"))), noMatchOutput).run("sonnet");

        assertThat(noModelsOutput.toString()).isEqualTo("No models available.%n".formatted());
        assertThat(noMatchOutput.toString()).isEqualTo("No models matching \"sonnet\"%n".formatted());
    }

    private static ModelRegistry registry(Model... models) {
        var registry = new ModelRegistry();
        registry.registerAll(List.of(models));
        return registry;
    }

    private static List<String> normalize(String output) {
        return output.lines()
            .map(String::stripTrailing)
            .filter(line -> !line.isEmpty())
            .toList();
    }

    private static Model model(String id, String provider, boolean reasoning, List<String> input) {
        return new Model(
            id,
            id,
            provider + "-api",
            provider,
            "https://example.com",
            reasoning,
            input,
            new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
            128_000,
            8_192,
            null,
            null
        );
    }
}
