package dev.pi.ai.auth;

import java.util.Optional;

@FunctionalInterface
public interface CredentialSource {
    Optional<Credential> resolve(String provider);
}
