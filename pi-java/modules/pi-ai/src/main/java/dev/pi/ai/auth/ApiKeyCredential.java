package dev.pi.ai.auth;

import java.util.Objects;

public record ApiKeyCredential(String secret) implements Credential {
    public ApiKeyCredential {
        Objects.requireNonNull(secret, "secret");
        if (secret.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
    }
}
