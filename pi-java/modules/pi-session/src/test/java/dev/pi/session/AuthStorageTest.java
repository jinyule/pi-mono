package dev.pi.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsApiKeys() throws Exception {
        var authPath = tempDir.resolve("auth.json");
        var storage = AuthStorage.create(authPath);

        storage.setApiKey("anthropic", "sk-ant-test");

        assertThat(storage.getApiKey("anthropic")).isEqualTo("sk-ant-test");
        assertThat(Files.readString(authPath, StandardCharsets.UTF_8))
            .contains("\"anthropic\"")
            .contains("\"type\" : \"api_key\"")
            .contains("\"key\" : \"sk-ant-test\"");
    }

    @Test
    void reloadsOauthCredentialsFromExistingFile() throws Exception {
        var authPath = tempDir.resolve("auth.json");
        Files.writeString(
            authPath,
            """
            {
              "anthropic": {
                "type": "oauth",
                "access": "oauth-access-token",
                "refresh": "refresh-token",
                "expires": 9999999999999
              }
            }
            """,
            StandardCharsets.UTF_8
        );

        var storage = AuthStorage.create(authPath);

        assertThat(storage.getApiKey("anthropic")).isEqualTo("oauth-access-token");
        assertThat(storage.providers()).containsExactly("anthropic");
    }

    @Test
    void removesSavedCredentials() throws Exception {
        var authPath = tempDir.resolve("auth.json");
        var storage = AuthStorage.create(authPath);
        storage.setApiKey("openai", "sk-openai-test");

        storage.remove("openai");

        assertThat(storage.get("openai")).isNull();
        assertThat(storage.providers()).isEmpty();
        assertThat(Files.readString(authPath, StandardCharsets.UTF_8)).doesNotContain("openai");
    }
}
