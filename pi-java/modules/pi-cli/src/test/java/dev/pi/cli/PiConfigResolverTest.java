package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.session.PackageSource;
import dev.pi.session.PackageSourceManager;
import dev.pi.session.SettingsManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiConfigResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void resolveIncludesConventionResourcesAndAppliedFilters() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var packageRoot = tempDir.resolve("resource-pack");
        Files.createDirectories(cwd);
        Files.createDirectories(packageRoot.resolve("themes"));
        Files.createDirectories(packageRoot.resolve("skills").resolve("demo"));
        Files.writeString(packageRoot.resolve("themes").resolve("dark.json"), "{}");
        Files.writeString(packageRoot.resolve("themes").resolve("light.json"), "{}");
        Files.writeString(packageRoot.resolve("skills").resolve("demo").resolve("SKILL.md"), "# demo");

        var settingsManager = SettingsManager.create(cwd);
        settingsManager.setProjectPackages(List.of(new PackageSource(
            packageRoot.toString(),
            List.of(),
            List.of(),
            List.of(),
            List.of("-themes/light.json")
        )));

        var resolver = new PiConfigResolver(settingsManager, new PackageSourceManager(cwd, settingsManager));
        var groups = resolver.resolve();

        assertThat(groups).hasSize(1);
        var themes = findSection(groups, PiConfigResolver.ResourceType.THEMES);
        var themeStates = themes.stream().collect(Collectors.toMap(PiConfigResolver.ResourceItem::displayName, PiConfigResolver.ResourceItem::enabled));
        assertThat(themeStates).containsEntry("themes/dark.json", true).containsEntry("themes/light.json", false);

        var skills = findSection(groups, PiConfigResolver.ResourceType.SKILLS);
        assertThat(skills).singleElement().extracting(PiConfigResolver.ResourceItem::displayName).isEqualTo("skills/demo");
    }

    @Test
    void resolveUsesManifestPatternsWhenManifestOnlyContainsGlobs() throws Exception {
        var cwd = tempDir.resolve("workspace");
        var packageRoot = tempDir.resolve("prompt-pack");
        Files.createDirectories(cwd);
        Files.createDirectories(packageRoot.resolve("prompts"));
        Files.writeString(packageRoot.resolve("prompts").resolve("system.md"), "# system");
        Files.writeString(packageRoot.resolve("prompts").resolve("ignore.md"), "# ignore");
        Files.writeString(packageRoot.resolve("package.json"), """
            {
              "name": "prompt-pack",
              "pi": {
                "prompts": ["prompts/*.md", "!prompts/ignore.md"]
              }
            }
            """);

        var settingsManager = SettingsManager.create(cwd);
        settingsManager.setProjectPackages(List.of(new PackageSource(packageRoot.toString())));

        var resolver = new PiConfigResolver(settingsManager, new PackageSourceManager(cwd, settingsManager));
        var prompts = findSection(resolver.resolve(), PiConfigResolver.ResourceType.PROMPTS);
        var promptStates = prompts.stream().collect(Collectors.toMap(PiConfigResolver.ResourceItem::displayName, PiConfigResolver.ResourceItem::enabled));

        assertThat(promptStates).containsEntry("prompts/system.md", true).containsEntry("prompts/ignore.md", false);
    }

    private static List<PiConfigResolver.ResourceItem> findSection(
        List<PiConfigResolver.ResourceGroup> groups,
        PiConfigResolver.ResourceType type
    ) {
        for (var group : groups) {
            for (var section : group.sections()) {
                if (section.type() == type) {
                    return section.items();
                }
            }
        }
        throw new AssertionError("Missing section: " + type);
    }
}
