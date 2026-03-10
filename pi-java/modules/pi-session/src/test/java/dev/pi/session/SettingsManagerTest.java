package dev.pi.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsManagerTest {
    @Test
    void deepMergesGlobalProjectAndRuntimeOverrides() {
        var global = Settings.empty().withMutations(root -> {
            root.put("theme", "dark");
            root.with("compaction").put("enabled", true).put("reserveTokens", 4096);
            root.withArray("packages").add("global");
            root.with("images").put("autoResize", true);
        });
        var project = Settings.empty().withMutations(root -> {
            root.with("compaction").put("keepRecentTokens", 1200);
            root.withArray("packages").add("project");
            root.with("images").put("blockImages", true);
        });

        var manager = SettingsManager.inMemory(global, project);
        manager.applyOverrides(Settings.empty().withMutations(root -> {
            root.put("theme", "light");
            root.with("compaction").put("enabled", false);
        }));

        var effective = manager.effective();
        assertThat(effective.getString("/theme")).isEqualTo("light");
        assertThat(effective.getBoolean("/compaction/enabled", true)).isFalse();
        assertThat(effective.getInt("/compaction/reserveTokens", 0)).isEqualTo(4096);
        assertThat(effective.getInt("/compaction/keepRecentTokens", 0)).isEqualTo(1200);
        assertThat(effective.getBoolean("/images/autoResize", false)).isTrue();
        assertThat(effective.getBoolean("/images/blockImages", false)).isTrue();
        assertThat(effective.getStringList("/packages")).containsExactly("project");
    }

    @Test
    void migratesLegacySettingsWhenLoadedAndPersistsModernShape(@TempDir Path tempDir) throws IOException {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd.resolve(".pi"));
        Files.createDirectories(agentDir);

        var globalSettingsPath = agentDir.resolve("settings.json");
        Files.writeString(
            globalSettingsPath,
            """
            {
              "queueMode": "all",
              "websockets": true,
              "skills": {
                "enableSkillCommands": false,
                "customDirectories": ["C:/skills"]
              }
            }
            """,
            StandardCharsets.UTF_8
        );

        var manager = SettingsManager.create(cwd, agentDir);
        manager.updateGlobal(settings -> settings.withMutations(root -> root.put("theme", "dark")));

        assertThat(manager.effective().getString("/steeringMode")).isEqualTo("all");
        assertThat(manager.effective().getString("/transport")).isEqualTo("websocket");
        assertThat(manager.effective().getBoolean("/enableSkillCommands", true)).isFalse();
        assertThat(manager.effective().getStringList("/skills")).containsExactly("C:/skills");

        var persisted = Files.readString(globalSettingsPath, StandardCharsets.UTF_8);
        assertThat(persisted).doesNotContain("\"queueMode\"");
        assertThat(persisted).doesNotContain("\"websockets\"");
        assertThat(persisted).contains("\"steeringMode\" : \"all\"");
        assertThat(persisted).contains("\"transport\" : \"websocket\"");
        assertThat(persisted).contains("\"theme\" : \"dark\"");
    }

    @Test
    void updateGlobalMergesAgainstLatestOnDiskState(@TempDir Path tempDir) throws IOException {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd.resolve(".pi"));
        Files.createDirectories(agentDir);

        var globalSettingsPath = agentDir.resolve("settings.json");
        Files.writeString(
            globalSettingsPath,
            """
            {
              "compaction": {
                "reserveTokens": 4096
              }
            }
            """,
            StandardCharsets.UTF_8
        );

        var manager = SettingsManager.create(cwd, agentDir);
        Files.writeString(
            globalSettingsPath,
            """
            {
              "compaction": {
                "reserveTokens": 8192
              },
              "images": {
                "autoResize": false
              },
              "theme": "dark"
            }
            """,
            StandardCharsets.UTF_8
        );

        manager.updateGlobal(settings -> settings.withMutations(root -> root.with("compaction").put("enabled", false)));

        var reloaded = SettingsManager.create(cwd, agentDir).effective();
        assertThat(reloaded.getInt("/compaction/reserveTokens", 0)).isEqualTo(8192);
        assertThat(reloaded.getBoolean("/compaction/enabled", true)).isFalse();
        assertThat(reloaded.getBoolean("/images/autoResize", true)).isFalse();
        assertThat(reloaded.getString("/theme")).isEqualTo("dark");
    }

    @Test
    void updateProjectWritesProjectScopedSettingsAndOverridesGlobal(@TempDir Path tempDir) throws IOException {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd.resolve(".pi"));
        Files.createDirectories(agentDir);

        Files.writeString(
            agentDir.resolve("settings.json"),
            """
            {
              "theme": "dark",
              "compaction": {
                "enabled": true,
                "reserveTokens": 4096
              }
            }
            """,
            StandardCharsets.UTF_8
        );

        var manager = SettingsManager.create(cwd, agentDir);
        manager.updateProject(settings -> settings.withMutations(root -> {
            root.put("theme", "light");
            root.with("compaction").put("keepRecentTokens", 1500);
        }));

        assertThat(manager.effective().getString("/theme")).isEqualTo("light");
        assertThat(manager.effective().getBoolean("/compaction/enabled", false)).isTrue();
        assertThat(manager.effective().getInt("/compaction/reserveTokens", 0)).isEqualTo(4096);
        assertThat(manager.effective().getInt("/compaction/keepRecentTokens", 0)).isEqualTo(1500);

        var projectFile = Files.readString(cwd.resolve(".pi").resolve("settings.json"), StandardCharsets.UTF_8);
        assertThat(projectFile).contains("\"theme\" : \"light\"");
        assertThat(projectFile).doesNotContain("\"reserveTokens\"");
    }

    @Test
    void serializesConcurrentScopedUpdatesWithFileLock(@TempDir Path tempDir) throws Exception {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd.resolve(".pi"));
        Files.createDirectories(agentDir);

        var firstManager = SettingsManager.create(cwd, agentDir);
        var secondManager = SettingsManager.create(cwd, agentDir);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> firstManager.updateGlobal(settings -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(150);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return settings.withMutations(root -> root.put("theme", "dark"));
            }));
            var second = executor.submit(() -> secondManager.updateGlobal(settings ->
                settings.withMutations(root -> root.put("transport", "websocket"))
            ));

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        var effective = SettingsManager.create(cwd, agentDir).effective();
        assertThat(effective.getString("/theme")).isEqualTo("dark");
        assertThat(effective.getString("/transport")).isEqualTo("websocket");
    }

    @Test
    void reloadKeepsLastGoodSnapshotAndRecordsParseErrors(@TempDir Path tempDir) throws IOException {
        var cwd = tempDir.resolve("workspace");
        var agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd.resolve(".pi"));
        Files.createDirectories(agentDir);

        var globalSettingsPath = agentDir.resolve("settings.json");
        Files.writeString(globalSettingsPath, "{\"theme\":\"dark\"}", StandardCharsets.UTF_8);

        var manager = SettingsManager.create(cwd, agentDir);
        Files.writeString(globalSettingsPath, "{invalid", StandardCharsets.UTF_8);

        manager.reload();

        assertThat(manager.global().getString("/theme")).isEqualTo("dark");
        assertThat(manager.drainErrors()).singleElement().satisfies(error -> {
            assertThat(error.scope()).isEqualTo(SettingsManager.SettingsScope.GLOBAL);
            assertThat(error.error()).hasMessageContaining("Failed to parse settings JSON");
        });
        assertThat(manager.drainErrors()).isEmpty();
    }
}
