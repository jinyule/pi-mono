package dev.pi.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PiAgentPathsTest {
    @Test
    void defaultsToHomePiAgentDirectory() {
        var homeDir = Path.of("C:/Users/tester");
        var resolved = PiAgentPaths.agentDir(Map.of(), homeDir);

        assertThat(resolved).isEqualTo(homeDir.resolve(".pi").resolve("agent").toAbsolutePath().normalize());
    }

    @Test
    void expandsTildeFromConfiguredAgentDirectory() {
        var homeDir = Path.of("C:/Users/tester");
        var resolved = PiAgentPaths.agentDir(Map.of(PiAgentPaths.ENV_AGENT_DIR, "~/custom-agent"), homeDir);

        assertThat(resolved).isEqualTo(homeDir.resolve("custom-agent").toAbsolutePath().normalize());
    }

    @Test
    void resolvesRelativeConfiguredAgentDirectoryAgainstCurrentWorkingDirectory() {
        var expected = Path.of(".config/pi-agent").toAbsolutePath().normalize();
        var resolved = PiAgentPaths.agentDir(Map.of(PiAgentPaths.ENV_AGENT_DIR, ".config/pi-agent"), Path.of("/home/tester"));

        assertThat(resolved).isEqualTo(expected);
    }
}
