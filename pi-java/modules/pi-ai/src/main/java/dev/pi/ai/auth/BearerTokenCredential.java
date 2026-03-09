package dev.pi.ai.auth;

import java.util.Objects;

public record BearerTokenCredential(String secret) implements Credential {
    public BearerTokenCredential {
        Objects.requireNonNull(secret, "secret");
        if (secret.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
    }
}
