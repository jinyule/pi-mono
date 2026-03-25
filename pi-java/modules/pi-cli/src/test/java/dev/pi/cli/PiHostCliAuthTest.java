package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PiHostCliAuthTest {
    @Test
    void importsGithubTokenFromGhAuthToken() {
        var observedCommand = new AtomicReference<java.util.List<String>>();
        var auth = new PiHostCliAuth(command -> {
            observedCommand.set(command);
            return new PiHostCliAuth.CommandResult(0, "ghp-test-token\n", "");
        });

        var imported = auth.importFor("github");

        assertThat(imported).isNotNull();
        assertThat(imported.secret()).isEqualTo("ghp-test-token");
        assertThat(imported.sourceDisplay()).isEqualTo("gh auth token");
        assertThat(observedCommand.get()).containsExactly("gh", "auth", "token");
    }

    @Test
    void importsGitlabTokenFromGlabAuthToken() {
        var observedCommand = new AtomicReference<java.util.List<String>>();
        var auth = new PiHostCliAuth(command -> {
            observedCommand.set(command);
            return new PiHostCliAuth.CommandResult(0, "glpat-test-token\n", "");
        });

        var imported = auth.importFor("gitlab");

        assertThat(imported).isNotNull();
        assertThat(imported.secret()).isEqualTo("glpat-test-token");
        assertThat(imported.sourceDisplay()).isEqualTo("glab auth token");
        assertThat(observedCommand.get()).containsExactly("glab", "auth", "token");
    }

    @Test
    void returnsNullForUnsupportedProvider() {
        var auth = new PiHostCliAuth(command -> new PiHostCliAuth.CommandResult(0, "unused\n", ""));

        assertThat(auth.importFor("anthropic")).isNull();
    }
}
